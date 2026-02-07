package com.example.selliaapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.selliaapp.auth.AuthState
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.ui.screens.auth.LoginScreen
import com.example.selliaapp.ui.screens.auth.RegisterScreen
import com.example.selliaapp.viewmodel.AuthViewModel
import com.example.selliaapp.viewmodel.RegisterViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.platform.LocalContext
import com.example.selliaapp.R

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

    val googleSignInClient = remember(context) {
        val webClientId = context.getString(R.string.default_web_client_id)
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
                    authViewModel.signInWithGoogle(token)
                }
            }
            .onFailure {
                authViewModel.reportAuthError("No se pudo completar el inicio con Google.")
            }
    }

    val onGoogleSignInClick = {
        val webClientId = context.getString(R.string.default_web_client_id)
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
            SelliaApp(
                navController = navController,
                customerRepo = customerRepo
            )
        }
        is AuthState.Error -> {
            val message = (authState as AuthState.Error).message
            if (isRegistering) {
                RegisterScreen(
                    isLoading = registerState.isLoading,
                    errorMessage = registerState.errorMessage,
                    onSubmit = registerViewModel::register,
                    onGoogleSignInClick = onGoogleSignInClick,
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
                    onRegisterClick = {
                        isRegistering = true
                    }
                )
            }
        }
        AuthState.Unauthenticated -> {
            if (isRegistering) {
                RegisterScreen(
                    isLoading = registerState.isLoading,
                    errorMessage = registerState.errorMessage,
                    onSubmit = registerViewModel::register,
                    onGoogleSignInClick = onGoogleSignInClick,
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
