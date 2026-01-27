package com.example.selliaapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.selliaapp.auth.AuthState
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.ui.screens.auth.LoginScreen
import com.example.selliaapp.viewmodel.AuthViewModel

@Composable
fun SelliaRoot(
    navController: NavHostController = rememberNavController(),
    customerRepo: CustomerRepository,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()

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
            LoginScreen(
                isLoading = false,
                errorMessage = message,
                onSubmit = authViewModel::signIn
            )
        }
        AuthState.Unauthenticated -> {
            LoginScreen(
                isLoading = false,
                errorMessage = null,
                onSubmit = authViewModel::signIn
            )
        }
    }
}
