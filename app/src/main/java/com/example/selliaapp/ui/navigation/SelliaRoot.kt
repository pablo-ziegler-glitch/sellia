package com.example.selliaapp.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.selliaapp.R
import com.example.selliaapp.auth.AuthState
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.auth.RequiredAuthAction
import com.example.selliaapp.sync.SyncScheduler
import com.example.selliaapp.ui.screens.auth.LoginScreen
import com.example.selliaapp.ui.screens.auth.RegisterScreen
import com.example.selliaapp.ui.screens.auth.TenantOnboardingRequiredScreen
import com.example.selliaapp.viewmodel.AuthViewModel
import com.example.selliaapp.viewmodel.RegisterViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun SelliaRoot(
    navController: NavHostController = rememberNavController(),
    customerRepo: CustomerRepository,
    authViewModel: AuthViewModel = hiltViewModel(),
    registerViewModel: RegisterViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val registerState by registerViewModel.uiState.collectAsState()

    var isRegistering by rememberSaveable { mutableStateOf(false) }
    var loginEmail by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current

    // ✅ Leemos el web client id en contexto composable (1 sola fuente de verdad)
    val webClientId = stringResource(id = R.string.default_web_client_id)

    // Se crea 1 vez por composición/Context (no recalcular en cada recomposition)
    val googleSignInClient = remember(context, webClientId) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
        )
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            authViewModel.reportAuthError("No se pudo completar el inicio con Google.")
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.getResult(ApiException::class.java) }
            .onSuccess { account ->
                val token = account.idToken
                if (token.isNullOrBlank()) {
                    authViewModel.reportAuthError("No se pudo obtener el token de Google.")
                } else {
                    if (isRegistering && registerState.mode == com.example.selliaapp.viewmodel.RegisterMode.FINAL_CUSTOMER) {
                        val tenantName = registerState.tenants.firstOrNull {
                            it.id == registerState.selectedTenantId
                        }?.name
                        registerViewModel.registerWithGoogle(
                            idToken = token,
                            tenantId = registerState.selectedTenantId,
                            tenantName = tenantName
                        )
                    } else {
                        authViewModel.signInWithGoogle(token)
                    }
                }
            }
            .onFailure {
                authViewModel.reportAuthError("No se pudo completar el inicio con Google.")
            }
    }

    // ✅ Callback NORMAL (no @Composable)
    val onGoogleSignInClick: () -> Unit = {
        if (webClientId.isBlank()) {
            authViewModel.reportAuthError("Falta configurar el web client id de Google.")
        } else {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    when (authState) {
        is AuthState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is AuthState.Authenticated -> {
            val tenantId = (authState as AuthState.Authenticated).session.tenantId
            LaunchedEffect(tenantId) {
                // Fuerza una sincronización inicial al entrar con una sesión en un dispositivo nuevo.
                SyncScheduler.enqueueNow(context, false)
            }
            SelliaApp(
                navController = navController,
                customerRepo = customerRepo
            )
        }

        is AuthState.PartiallyAuthenticated -> {
            val partialState = authState as AuthState.PartiallyAuthenticated
            LaunchedEffect(partialState.requiredAction) {
                if (partialState.requiredAction == RequiredAuthAction.SELECT_TENANT) {
                    registerViewModel.setRequiresTenantSelectionOnboarding(true)
                }
            }
            TenantOnboardingRequiredScreen(
                isLoading = registerState.isLoading,
                isLoadingTenants = registerState.isLoadingTenants,
                errorMessage = registerState.errorMessage,
                tenants = registerState.tenants,
                selectedTenantId = registerState.selectedTenantId,
                onTenantChange = registerViewModel::selectTenant,
                onSubmit = registerViewModel::completeTenantSelectionOnboarding,
                onSignOut = authViewModel::signOut
            )
        }

        is AuthState.Error -> {
            val message = (authState as AuthState.Error).message
            if (isRegistering) {
                RegisterScreen(
                    isLoading = registerState.isLoading,
                    errorMessage = registerState.errorMessage,
                    successMessage = registerState.successMessage,
                    tenants = registerState.tenants,
                    selectedTenantId = registerState.selectedTenantId,
                    mode = registerState.mode,
                    isLoadingTenants = registerState.isLoadingTenants,
                    onModeChange = registerViewModel::updateMode,
                    onTenantChange = registerViewModel::selectTenant,
                    onSubmit = registerViewModel::register,
                    onGoogleSignInClick = { tenantId, _ ->
                        if (tenantId.isNullOrBlank()) {
                            registerViewModel.clearError()
                            authViewModel.reportAuthError("Seleccioná una tienda para continuar.")
                        } else {
                            onGoogleSignInClick()
                        }
                    },
                    onLoginClick = {
                        registerViewModel.clearError()
                        isRegistering = false
                    }
                )
            } else {
                LoginScreen(
                    isLoading = false,
                    errorMessage = message,
                    email = loginEmail,
                    onEmailChange = { loginEmail = it },
                    onSubmit = authViewModel::signIn,
                    onGoogleSignInClick = onGoogleSignInClick,
                    onRegisterClick = { isRegistering = true }
                )
            }
        }

        AuthState.Unauthenticated -> {
            if (isRegistering) {
                RegisterScreen(
                    isLoading = registerState.isLoading,
                    errorMessage = registerState.errorMessage,
                    successMessage = registerState.successMessage,
                    tenants = registerState.tenants,
                    selectedTenantId = registerState.selectedTenantId,
                    mode = registerState.mode,
                    isLoadingTenants = registerState.isLoadingTenants,
                    onModeChange = registerViewModel::updateMode,
                    onTenantChange = registerViewModel::selectTenant,
                    onSubmit = registerViewModel::register,
                    onGoogleSignInClick = { tenantId, _ ->
                        if (tenantId.isNullOrBlank()) {
                            registerViewModel.clearError()
                            authViewModel.reportAuthError("Seleccioná una tienda para continuar.")
                        } else {
                            onGoogleSignInClick()
                        }
                    },
                    onLoginClick = {
                        registerViewModel.clearError()
                        isRegistering = false
                    }
                )
            } else {
                LoginScreen(
                    isLoading = false,
                    errorMessage = null,
                    email = loginEmail,
                    onEmailChange = { loginEmail = it },
                    onSubmit = authViewModel::signIn,
                    onGoogleSignInClick = onGoogleSignInClick,
                    onRegisterClick = { isRegistering = true }
                )
            }
        }
    }
}
