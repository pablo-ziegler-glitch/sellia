package com.example.selliaapp.auth

import com.example.selliaapp.data.AppDatabase
import com.example.selliaapp.data.dao.TenantSkuConfigDao
import com.example.selliaapp.data.local.entity.TenantSkuConfigEntity
import com.example.selliaapp.di.AppModule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Singleton
class AuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val tenantSkuConfigDao: TenantSkuConfigDao,
    private val database: AppDatabase,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : TenantProvider {

    // ─── Límite de sesión ─────────────────────────────────────────────────────
    // Duración máxima de sesión. El admin puede cambiarla via Firestore
    // en platform/config → session.maxSessionHours (1–24h). Default: 8h.
    private var sessionMaxDurationMs: Long = DEFAULT_SESSION_MAX_DURATION_MS

    // Job del watchdog de expiración absoluta de sesión.
    private var sessionExpiryJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    private val _lastSessionRefreshAtMs = MutableStateFlow<Long?>(null)
    val lastSessionRefreshAtMs: StateFlow<Long?> = _lastSessionRefreshAtMs

    private val _loadingUiState = MutableStateFlow(AuthLoadingUiState())
    val loadingUiState: StateFlow<AuthLoadingUiState> = _loadingUiState

    private val refreshSignals = MutableSharedFlow<FirebaseUser?>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var idTokenListener: FirebaseAuth.IdTokenListener? = null

    companion object {
        /** Duración máxima de sesión por defecto: 8 horas. */
        const val DEFAULT_SESSION_MAX_DURATION_MS = 8L * 60L * 60L * 1000L

        /** Intervalo del watchdog de expiración: cada 60 segundos. */
        private const val SESSION_CHECK_INTERVAL_MS = 60_000L
    }

    init {
        observeRefreshSignals()
        registerAuthListeners()
        startSessionExpiryWatchdog()
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = runCatching {
        showLoading(progress = 0.1f, label = "Validando credenciales...")
        _state.value = AuthState.Loading
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw IllegalStateException("No se pudo obtener el usuario")
        showLoading(progress = 0.35f, label = "Verificando tu cuenta...")
        enforceEmailVerification(user)
        showLoading(progress = 0.6f, label = "Sincronizando tu perfil...")
        val session = fetchSession(user)
        syncTenantStoreMetadata(session)
        publishAuthenticatedState(session)
        session
    }.onFailure { error ->
        _state.value = AuthState.Error(AuthErrorMapper.toUserMessage(error, "No se pudo iniciar sesión"))
        resetLoading()
    }

    suspend fun signInWithGoogle(idToken: String, allowOnboardingFallback: Boolean = true): Result<AuthSession> =
        runCatching {
            showLoading(progress = 0.1f, label = "Conectando con Google...")
            _state.value = AuthState.Loading
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("No se pudo obtener el usuario")
            showLoading(progress = 0.55f, label = "Recuperando datos de tu tienda...")
            val session = runCatching { fetchSession(user) }
                .getOrElse { throw IllegalStateException("No encontramos un perfil dueño válido para esta cuenta.") }
            syncTenantStoreMetadata(session)
            publishAuthenticatedState(session)
            session
        }.onFailure { error ->
            _state.value = AuthState.Error(AuthErrorMapper.toUserMessage(error, "No se pudo iniciar sesión con Google"))
            resetLoading()
        }

    suspend fun completePublicCustomerOnboarding(tenantId: String, tenantName: String?): Result<AuthSession> =
        runCatching {
            throw IllegalStateException("El onboarding de cliente final está deshabilitado en esta app.")
        }.onFailure {
            _state.value = AuthState.PartiallyAuthenticated(
                session = pendingSessionFromCurrentUser(),
                requiredAction = RequiredAuthAction.SELECT_TENANT
            )
            resetLoading()
        }

    fun reportAuthError(message: String) {
        _state.value = AuthState.Error(message)
        resetLoading()
    }

    suspend fun refreshSession(): Result<AuthSession> = runCatching {
        showLoading(progress = 0.2f, label = "Actualizando sesión...")
        _state.value = AuthState.Loading
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("Sesión no disponible")
        val session = fetchSession(user)
        syncTenantStoreMetadata(session)
        publishAuthenticatedState(session)
        session
    }.onFailure { error ->
        _state.value = AuthState.Error(AuthErrorMapper.toUserMessage(error, "No se pudo actualizar la sesión"))
        resetLoading()
    }

    fun signOut() {
        cancelSessionExpiryWatchdog()
        firebaseAuth.signOut()
        _state.value = AuthState.Unauthenticated
        _lastSessionRefreshAtMs.value = null
        resetLoading()
        // Limpia TODOS los datos locales de la tienda al cerrar sesión.
        // Esto evita que al iniciar sesión con una cuenta distinta se vean
        // datos (productos, clientes, ventas, etc.) de la tienda anterior.
        scope.launch {
            runCatching { withContext(io) { database.clearAllTables() } }
        }
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("Sesión no disponible")
        user.updatePassword(newPassword).await()
    }

    private fun startSessionExpiryWatchdog() {
        sessionExpiryJob?.cancel()
        sessionExpiryJob = scope.launch {
            var wasAuthenticated = false
            state.collectLatest { authState ->
                when (authState) {
                    is AuthState.Authenticated -> wasAuthenticated = true
                    is AuthState.Unauthenticated -> {
                        if (wasAuthenticated) {
                            withContext(NonCancellable + io) {
                                database.clearAllTables()
                            }
                        }
                        wasAuthenticated = false
                    }
                    else -> Unit
                }
            }
        }
    }

    fun clear() {
        cancelSessionExpiryWatchdog()
        authStateListener?.let(firebaseAuth::removeAuthStateListener)
        authStateListener = null
        idTokenListener?.let(firebaseAuth::removeIdTokenListener)
        idTokenListener = null
        scope.cancel("AuthManager fue liberado")
    }

    override fun currentTenantId(): String? =
        (state.value as? AuthState.Authenticated)?.session?.tenantId

    override suspend fun requireTenantId(): String {
        val resolved = when (val current = state.value) {
            is AuthState.Authenticated -> current.session.tenantId
            is AuthState.Loading -> {
                val next = state.first { it !is AuthState.Loading }
                (next as? AuthState.Authenticated)?.session?.tenantId
            }
            else -> null
        }
        return resolved ?: throw IllegalStateException("Sesión no disponible para obtener tenantId")
    }

    private fun observeRefreshSignals() {
        scope.launch {
            refreshSignals
                .debounce(500)
                .collectLatest { user -> resolveSession(user) }
        }
    }

    private fun registerAuthListeners() {
        authStateListener = FirebaseAuth.AuthStateListener { auth ->
            refreshSignals.tryEmit(auth.currentUser)
        }.also(firebaseAuth::addAuthStateListener)

        idTokenListener = FirebaseAuth.IdTokenListener { auth ->
            refreshSignals.tryEmit(auth.currentUser)
        }.also(firebaseAuth::addIdTokenListener)
    }

    private suspend fun resolveSession(user: FirebaseUser?) {
        if (user == null) {
            cancelSessionExpiryWatchdog()
            _state.value = AuthState.Unauthenticated
            _lastSessionRefreshAtMs.value = null
            resetLoading()
            return
        }

        // ── Verificar límite absoluto de sesión ──────────────────────────────
        // Usamos lastSignInTime del metadata de Firebase: timestamp del último
        // inicio de sesión real (no se actualiza con refreshes de token).
        val lastSignInMs = user.metadata?.lastSignInTimestamp ?: System.currentTimeMillis()
        val sessionAgeMs = System.currentTimeMillis() - lastSignInMs
        if (sessionAgeMs > sessionMaxDurationMs) {
            val hours = sessionMaxDurationMs / (1000 * 60 * 60)
            _state.value = AuthState.Error(
                "Tu sesión expiró (límite de ${hours}h). Por favor, iniciá sesión nuevamente."
            )
            firebaseAuth.signOut()
            scope.launch { runCatching { withContext(io) { database.clearAllTables() } } }
            resetLoading()
            return
        }

        if (shouldShowLoadingFor(user)) {
            showLoading(progress = 0.15f, label = "Validando tu sesión...")
            _state.value = AuthState.Loading
        }
        runCatching { fetchSessionWithRetry(user) }
            .onSuccess { session ->
                syncTenantStoreMetadata(session)
                publishAuthenticatedState(session)
                // Iniciar (o re-armar) el watchdog que fuerza logout al vencer la sesión.
                startSessionExpiryWatchdog(lastSignInMs)
            }
            .onFailure { error ->
                _state.value = AuthState.Error(
                    error.message ?: "No se pudo resolver el tenantId"
                )
                resetLoading()
            }
    }

    private fun shouldShowLoadingFor(user: FirebaseUser): Boolean {
        val currentState = _state.value
        val authenticated = currentState as? AuthState.Authenticated ?: return true
        return authenticated.session.uid != user.uid
    }

    private fun publishAuthenticatedState(session: AuthSession) {
        showLoading(progress = 1f, label = "Listo")
        val refreshedAtMs = System.currentTimeMillis()
        _lastSessionRefreshAtMs.value = refreshedAtMs
        _state.value = AuthState.Authenticated(
            session = session,
            refreshedAtMs = refreshedAtMs
        )
        resetLoading()
    }

    private suspend fun enforceEmailVerification(user: FirebaseUser) {
        user.reload().await()
        if (!user.isEmailVerified) {
            firebaseAuth.signOut()
            throw IllegalStateException(
                "Necesitás verificar tu email antes de ingresar. Revisá tu bandeja y correo no deseado."
            )
        }
    }

    private suspend fun fetchSessionWithRetry(user: FirebaseUser): AuthSession {
        val maxAttempts = 3
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            val progress = 0.35f + ((attempt + 1f) / maxAttempts.toFloat()) * 0.45f
            showLoading(progress = progress.coerceIn(0f, 0.9f), label = "Sincronizando perfil (${attempt + 1}/$maxAttempts)...")
            runCatching { fetchSession(user) }
                .onSuccess { return it }
                .onFailure { lastError = it }
            if (attempt < maxAttempts - 1) {
                delay(300)
            }
        }
        throw lastError ?: MissingTenantContextException()
    }

    private suspend fun fetchSession(user: FirebaseUser): AuthSession {
        showLoading(progress = 0.75f, label = "Leyendo permisos y tienda...")
        val snapshot = firestore.collection("users").document(user.uid).get().await()
        val status = snapshot.getString("status")?.trim()?.lowercase(Locale.ROOT)
        if (!status.isNullOrBlank() && status != "active") {
            throw IllegalStateException("Tu cuenta está pendiente de aprobación o fue deshabilitada.")
        }

        val availableTenants = extractAvailableTenants(snapshot)
        val selectedTenantId = snapshot.getString("tenantId")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { tenantId -> availableTenants.isEmpty() || availableTenants.any { it.id == tenantId } }

        if (availableTenants.size > 1 && selectedTenantId == null) {
            _state.value = AuthState.PartiallyAuthenticated(
                session = PendingAuthSession(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString(),
                    availableTenants = availableTenants
                ),
                requiredAction = RequiredAuthAction.SELECT_TENANT
            )
            throw MissingTenantContextException()
        }

        val resolvedTenantId = selectedTenantId ?: availableTenants.firstOrNull()?.id

        if (resolvedTenantId == null) {
            throw MissingTenantContextException()
        }

        validateTenantIsActiveOrThrow(resolvedTenantId)

        showLoading(progress = 0.92f, label = "Finalizando ingreso...")
        return AuthSession(
            uid = user.uid,
            tenantId = resolvedTenantId,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl?.toString()
        )
    }

    private suspend fun ensurePublicCustomerSession(user: FirebaseUser, allowOnboardingFallback: Boolean): AuthSession {
        firebaseAuth.signOut()
        throw IllegalStateException("El acceso de cliente final fue deshabilitado. Solo se permiten cuentas de dueño.")
    }

    private suspend fun validateTenantIsActiveOrThrow(tenantId: String) {
        val tenantSnapshot = firestore.collection("tenants").document(tenantId).get().await()
        if (!tenantSnapshot.exists()) {
            throw IllegalStateException("La tienda asociada a tu cuenta ya no existe.")
        }
        val tenantStatus = tenantSnapshot.getString("status")?.trim()?.lowercase(Locale.ROOT)
        if (!tenantStatus.isNullOrBlank() && tenantStatus != "active") {
            throw IllegalStateException("La tienda está temporalmente deshabilitada. Podés solicitar reactivación al administrador.")
        }
    }

    private suspend fun extractAvailableTenants(snapshot: com.google.firebase.firestore.DocumentSnapshot): List<PendingTenantOption> {
        @Suppress("UNCHECKED_CAST")
        val tenantIdsFromArray = (snapshot.get("tenantIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val candidateIds = buildList {
            addAll(tenantIdsFromArray)
            snapshot.getString("tenantId")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            snapshot.getString("storeId")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()

        if (candidateIds.isEmpty()) return emptyList()

        val tenantsById = candidateIds.associateWith { tenantId ->
            runCatching {
                firestore.collection("tenants").document(tenantId).get().await()
            }.getOrNull()
        }

        return candidateIds.mapNotNull { tenantId ->
            val doc = tenantsById[tenantId] ?: return@mapNotNull null
            if (!doc.exists()) return@mapNotNull null
            val status = doc.getString("status")?.trim()?.lowercase(Locale.ROOT)
            if (!status.isNullOrBlank() && status != "active") return@mapNotNull null
            val name = doc.getString("name")?.trim().orEmpty().ifBlank { "Tienda" }
            PendingTenantOption(id = tenantId, name = name)
        }
    }

    suspend fun switchTenant(tenantId: String): Result<AuthSession> = runCatching {
        showLoading(progress = 0.3f, label = "Cambiando de tienda...")
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("Sesión no disponible")
        val snapshot = firestore.collection("users").document(user.uid).get().await()
        val availableTenants = extractAvailableTenants(snapshot)
        if (availableTenants.none { it.id == tenantId }) {
            throw IllegalArgumentException("No tenés acceso a la tienda seleccionada")
        }
        validateTenantIsActiveOrThrow(tenantId)
        firestore.collection("users").document(user.uid)
            .set(
                mapOf(
                    "tenantId" to tenantId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        // Refresca claims luego de cambiar tenant seleccionado para evitar ventanas
        // donde el SDK siga consultando con token desactualizado.
        user.getIdToken(true).await()

        // Limpiar datos locales del tenant anterior ANTES de sincronizar el nuevo.
        // Esto garantiza aislamiento total: ningún dato de la tienda anterior es
        // visible mientras se cargan los datos de la tienda recién seleccionada.
        showLoading(progress = 0.5f, label = "Limpiando datos de la tienda anterior...")
        withContext(io) { database.clearAllTables() }

        val session = fetchSession(user)
        syncTenantStoreMetadata(session)
        publishAuthenticatedState(session)
        session
    }.onFailure {
        resetLoading()
    }

    private suspend fun syncTenantStoreMetadata(session: AuthSession) {
        val tenantId = session.tenantId.trim()
        if (tenantId.isBlank()) return

        val now = System.currentTimeMillis()
        runCatching {
            val tenantRef = firestore.collection("tenants").document(tenantId)
            val tenantSnapshot = tenantRef.get().await()
            val tenantName = tenantSnapshot.getString("name").orEmpty().trim()
            val tenantPrefix = tenantSnapshot.getString("skuPrefix").normalizeSkuPrefixOrNull()

            val marketingStoreName = if (tenantName.isBlank()) {
                val marketingSnapshot = tenantRef
                    .collection("config")
                    .document("marketing")
                    .get()
                    .await()
                val marketingData = marketingSnapshot.get("data") as? Map<*, *>
                (marketingData?.get("storeName") as? String).orEmpty().trim()
            } else {
                ""
            }

            val resolvedStoreName = tenantName
                .ifBlank { marketingStoreName }
                .ifBlank { "Tienda" }

            val resolvedPrefix = tenantPrefix ?: deriveSkuPrefixFromStoreName(resolvedStoreName)

            tenantSkuConfigDao.upsert(
                TenantSkuConfigEntity(
                    tenantId = tenantId,
                    storeName = resolvedStoreName,
                    skuPrefix = resolvedPrefix,
                    updatedAtEpochMs = now
                )
            )

            val tenantWrite = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (tenantName.isBlank()) {
                tenantWrite["name"] = resolvedStoreName
            }
            if (tenantPrefix == null) {
                tenantWrite["skuPrefix"] = resolvedPrefix
            }
            if (tenantWrite.size > 1) {
                tenantRef.set(tenantWrite, SetOptions.merge()).await()
            }

            firestore.collection("tenant_directory")
                .document(tenantId)
                .set(
                    mapOf(
                        "tenantId" to tenantId,
                        "storeName" to resolvedStoreName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    private fun String?.normalizeSkuPrefixOrNull(): String? {
        val normalized = this.orEmpty().uppercase().replace("[^A-Z0-9]".toRegex(), "").take(6)
        return normalized.takeIf { it.length >= 3 }
    }

    private fun deriveSkuPrefixFromStoreName(storeName: String): String {
        val normalized = storeName.uppercase().replace("[^A-Z0-9]".toRegex(), "")
        return normalized.take(3).padEnd(3, 'X')
    }

    private fun showLoading(progress: Float, label: String) {
        _loadingUiState.value = AuthLoadingUiState(
            progress = progress.coerceIn(0f, 1f),
            label = label
        )
    }

    private fun resetLoading() {
        _loadingUiState.value = AuthLoadingUiState()
    }

    private fun pendingSessionFromCurrentUser(): PendingAuthSession {
        val user = firebaseAuth.currentUser
        return PendingAuthSession(
            uid = user?.uid.orEmpty(),
            email = user?.email,
            displayName = user?.displayName,
            photoUrl = user?.photoUrl?.toString(),
            availableTenants = emptyList()
        )
    }

    // ─── Watchdog de expiración de sesión ─────────────────────────────────────

    /**
     * Inicia un coroutine que verifica cada minuto si la sesión venció.
     * Si el tiempo transcurrido desde el último sign-in supera [sessionMaxDurationMs],
     * fuerza el logout y limpia la base de datos local.
     *
     * Se re-arma en cada resolución exitosa de sesión, usando siempre el
     * [lastSignInMs] del metadata de Firebase (no se actualiza con token refreshes).
     */
    private fun startSessionExpiryWatchdog(lastSignInMs: Long) {
        cancelSessionExpiryWatchdog()
        sessionExpiryJob = scope.launch {
            while (true) {
                delay(SESSION_CHECK_INTERVAL_MS)
                val elapsed = System.currentTimeMillis() - lastSignInMs
                if (elapsed > sessionMaxDurationMs) {
                    val hours = sessionMaxDurationMs / (1000 * 60 * 60)
                    cancelSessionExpiryWatchdog()
                    firebaseAuth.signOut()
                    runCatching { withContext(io) { database.clearAllTables() } }
                    _lastSessionRefreshAtMs.value = null
                    _state.value = AuthState.Error(
                        "Tu sesión expiró (límite de ${hours}h). Por favor, iniciá sesión nuevamente."
                    )
                    resetLoading()
                    break
                }
            }
        }
    }

    private fun cancelSessionExpiryWatchdog() {
        sessionExpiryJob?.cancel()
        sessionExpiryJob = null
    }

    /**
     * Carga la política de sesión desde Firestore (platform/config, campo session.maxSessionHours).
     * Si no existe o está fuera del rango permitido (1–24h), usa el default de 8h.
     * El admin de plataforma puede modificar este valor.
     */
    suspend fun loadSessionPolicy() {
        runCatching {
            val snap = firestore.collection("platform").document("config").get().await()
            val configuredHours = snap.getDouble("session.maxSessionHours")
                ?: (snap.getLong("session.maxSessionHours")?.toDouble())
            if (configuredHours != null && configuredHours > 0) {
                sessionMaxDurationMs = (configuredHours * 60.0 * 60.0 * 1000.0)
                    .toLong()
                    .coerceIn(
                        1L * 60 * 60 * 1000,   // mínimo 1h
                        24L * 60 * 60 * 1000   // máximo 24h
                    )
            }
        }
        // Si falla (sin permisos, sin conexión), se usa el default configurado en el companion.
    }
}

private class MissingTenantContextException : IllegalStateException(
    "Falta contexto de tenant. Seleccioná una tienda para continuar."
)
