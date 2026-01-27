package com.example.selliaapp.auth

import com.example.selliaapp.di.AppModule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : TenantProvider {

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null) {
                _state.value = AuthState.Unauthenticated
            } else {
                loadSession(user)
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = runCatching {
        _state.value = AuthState.Loading
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw IllegalStateException("No se pudo obtener el usuario")
        val session = fetchSession(user)
        _state.value = AuthState.Authenticated(session)
        session
    }.onFailure { error ->
        _state.value = AuthState.Error(error.message ?: "No se pudo iniciar sesión")
    }

    fun signOut() {
        firebaseAuth.signOut()
        _state.value = AuthState.Unauthenticated
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

    private fun loadSession(user: FirebaseUser) {
        _state.value = AuthState.Loading
        scope.launch {
            runCatching { fetchSession(user) }
                .onSuccess { session ->
                    _state.value = AuthState.Authenticated(session)
                }
                .onFailure { error ->
                    _state.value = AuthState.Error(
                        error.message ?: "No se pudo resolver el tenantId"
                    )
                }
        }
    }

    private suspend fun fetchSession(user: FirebaseUser): AuthSession {
        val snapshot = firestore.collection("users").document(user.uid).get().await()
        val tenantId = snapshot.getString("tenantId")
            ?: snapshot.getString("storeId")
            ?: throw IllegalStateException("El usuario no tiene tenantId/storeId asignado")
        return AuthSession(
            uid = user.uid,
            tenantId = tenantId,
            email = user.email
        )
    }
}
