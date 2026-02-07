package com.example.selliaapp.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    email: String,
    onEmailChange: (String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Iniciar sesión",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            enabled = !isLoading,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            enabled = !isLoading,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(email.trim(), password) },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
        ) {
            Text(if (isLoading) "Ingresando..." else "Ingresar")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onGoogleSignInClick,
            enabled = !isLoading
        ) {
            Text("Continuar con Google")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onRegisterClick, enabled = !isLoading) {
            Text("Crear cuenta")
        }
    }
}
