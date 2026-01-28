package com.example.selliaapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
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
                    onLoginClick = {
                        registerViewModel.clearError()
                        isRegistering = false
                    }
                )
            } else {
                LoginScreen(
                    isLoading = false,
                    errorMessage = message,
                    onSubmit = authViewModel::signIn,
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
                    onLoginClick = {
                        registerViewModel.clearError()
                        isRegistering = false
                    }
                )
            } else {
                LoginScreen(
                    isLoading = false,
                    errorMessage = null,
                    onSubmit = authViewModel::signIn,
                    onRegisterClick = { isRegistering = true }
                )
            }
        }
    }
}
