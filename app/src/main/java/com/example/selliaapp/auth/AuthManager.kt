package com.example.selliaapp.auth

import com.example.selliaapp.BuildConfig
import com.example.selliaapp.di.AppModule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Singleton
class AuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : TenantProvider {

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

    init {
        observeRefreshSignals()
        registerAuthListeners()
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
                .getOrElse { ensurePublicCustomerSession(user, allowOnboardingFallback) }
            publishAuthenticatedState(session)
            session
        }.onFailure { error ->
            _state.value = AuthState.Error(AuthErrorMapper.toUserMessage(error, "No se pudo iniciar sesión con Google"))
            resetLoading()
        }

    suspend fun completePublicCustomerOnboarding(tenantId: String, tenantName: String?): Result<AuthSession> =
        runCatching {
            val user = firebaseAuth.currentUser ?: throw IllegalStateException("Sesión no disponible")
            val tenantSnapshot = firestore.collection("tenants").document(tenantId).get().await()
            if (!tenantSnapshot.exists()) {
                throw IllegalArgumentException("La tienda seleccionada no existe")
            }
            val createdAt = FieldValue.serverTimestamp()
            val normalizedEmail = (user.email ?: "").trim().lowercase()
            val displayName = (user.displayName ?: "").trim()

            firestore.collection("users").document(user.uid)
                .set(
                    mapOf(
                        "tenantId" to tenantId,
                        "email" to normalizedEmail,
                        "role" to "viewer",
                        "accountType" to "final_customer",
                        "status" to "active",
                        "displayName" to displayName,
                        "selectedCatalogTenantId" to tenantId,
                        "followedTenantIds" to listOf(tenantId),
                        "updatedAt" to createdAt
                    ),
                    SetOptions.merge()
                )
                .await()

            firestore.collection("tenant_users")
                .document("${tenantId}_${normalizedEmail}")
                .set(
                    mapOf(
                        "tenantId" to tenantId,
                        "name" to displayName,
                        "email" to normalizedEmail,
                        "role" to "viewer",
                        "isActive" to true,
                        "updatedAt" to createdAt
                    ),
                    SetOptions.merge()
                )
                .await()

            firestore.collection("account_requests")
                .document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "accountType" to "final_customer",
                        "status" to "active",
                        "tenantId" to tenantId,
                        "tenantName" to tenantName.orEmpty(),
                        "contactName" to displayName,
                        "createdAt" to createdAt,
                        "updatedAt" to createdAt
                    ),
                    SetOptions.merge()
                )
                .await()

            val session = fetchSession(user)
            publishAuthenticatedState(session)
            session
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
        publishAuthenticatedState(session)
        session
    }.onFailure { error ->
        _state.value = AuthState.Error(AuthErrorMapper.toUserMessage(error, "No se pudo actualizar la sesión"))
        resetLoading()
    }

    fun signOut() {
        firebaseAuth.signOut()
        _state.value = AuthState.Unauthenticated
        _lastSessionRefreshAtMs.value = null
        resetLoading()
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("Sesión no disponible")
        user.updatePassword(newPassword).await()
    }

    fun clear() {
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
            _state.value = AuthState.Unauthenticated
            _lastSessionRefreshAtMs.value = null
            resetLoading()
            return
        }

        if (shouldShowLoadingFor(user)) {
            showLoading(progress = 0.15f, label = "Validando tu sesión...")
            _state.value = AuthState.Loading
        }
        runCatching { fetchSessionWithRetry(user) }
            .onSuccess { session -> publishAuthenticatedState(session) }
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
        val status = snapshot.getString("status")?.lowercase()
        if (!status.isNullOrBlank() && status != "active") {
            throw IllegalStateException("Tu cuenta está pendiente de aprobación o fue deshabilitada.")
        }
        val tenantId = snapshot.getString("tenantId")
            ?: snapshot.getString("storeId")
            ?: run {
                val role = snapshot.getString("role")?.lowercase()
                val accountType = snapshot.getString("accountType")?.lowercase()
                if (role == "viewer" || accountType == "final_customer" || accountType == "public_customer") {
                    _state.value = AuthState.PartiallyAuthenticated(
                        session = PendingAuthSession(
                            uid = user.uid,
                            email = user.email,
                            displayName = user.displayName,
                            photoUrl = user.photoUrl?.toString()
                        ),
                        requiredAction = RequiredAuthAction.SELECT_TENANT
                    )
                }
                throw MissingTenantContextException()
            }
        showLoading(progress = 0.92f, label = "Finalizando ingreso...")
        return AuthSession(
            uid = user.uid,
            tenantId = tenantId,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl?.toString()
        )
    }

    private suspend fun ensurePublicCustomerSession(user: FirebaseUser, allowOnboardingFallback: Boolean): AuthSession {
        showLoading(progress = 0.82f, label = "Preparando alta de cliente...")
        val existing = runCatching { fetchSession(user) }.getOrNull()
        if (existing != null) return existing

        if (!allowOnboardingFallback) {
            firebaseAuth.signOut()
            throw IllegalStateException("No encontramos tu perfil. Registrate con Google para crear tu cuenta.")
        }

        val globalPublicTenantId = BuildConfig.GLOBAL_PUBLIC_CUSTOMER_TENANT_ID.trim()
        if (globalPublicTenantId.isNotBlank()) {
            val tenantSnapshot = firestore.collection("tenants").document(globalPublicTenantId).get().await()
            if (!tenantSnapshot.exists()) {
                throw IllegalStateException(
                    "GLOBAL_PUBLIC_CUSTOMER_TENANT_ID apunta a un tenant inexistente. Configuralo correctamente."
                )
            }
        }

        _state.value = AuthState.PartiallyAuthenticated(
            session = PendingAuthSession(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString()
            ),
            requiredAction = RequiredAuthAction.SELECT_TENANT
        )
        throw MissingTenantContextException()
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
            photoUrl = user?.photoUrl?.toString()
        )
    }
}

private class MissingTenantContextException : IllegalStateException(
    "Falta contexto de tenant. Seleccioná una tienda para continuar."
)
