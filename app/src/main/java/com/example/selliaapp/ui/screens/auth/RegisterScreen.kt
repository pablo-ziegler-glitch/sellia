package com.example.selliaapp.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.TenantSummary
import com.example.selliaapp.viewmodel.RegisterMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    tenants: List<TenantSummary>,
    selectedTenantId: String?,
    mode: RegisterMode,
    isLoadingTenants: Boolean,
    onModeChange: (RegisterMode) -> Unit,
    onTenantChange: (String) -> Unit,
    onSubmit: (
        String,
        String,
        String,
        String,
        String,
        String,
        String?,
        String?,
        String,
        String?,
        RegisterMode
    ) -> Unit,
    onGoogleSignInClick: (String?, String?) -> Unit,
    onLoginClick: () -> Unit
) {
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var storePhone by remember { mutableStateOf("") }
    var skuPrefix by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var modeExpanded by remember { mutableStateOf(false) }
    var tenantExpanded by remember { mutableStateOf(false) }

    val selectedTenant = tenants.firstOrNull { it.id == selectedTenantId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Crear cuenta",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        ExposedDropdownMenuBox(
            expanded = modeExpanded,
            onExpandedChange = { modeExpanded = !modeExpanded }
        ) {
            OutlinedTextField(
                value = mode.label,
                onValueChange = {},
                label = { Text("Tipo de cuenta") },
                readOnly = true,
                enabled = !isLoading,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false }
            ) {
                RegisterMode.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label) },
                        onClick = {
                            onModeChange(entry)
                            modeExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (mode == RegisterMode.STORE_OWNER) {
            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                label = { Text("Nombre de la tienda") },
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = skuPrefix,
                onValueChange = { raw ->
                    skuPrefix = raw.uppercase().replace("[^A-Z0-9]".toRegex(), "").take(6)
                },
                label = { Text("Prefijo SKU (opcional, fijo)") },
                supportingText = { Text("Si lo dejás vacío, se usarán las primeras 3 letras de la tienda.") },
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = storeAddress,
                onValueChange = { storeAddress = it },
                label = { Text("Dirección comercial") },
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = storePhone,
                onValueChange = { storePhone = it },
                label = { Text("Teléfono comercial") },
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            ExposedDropdownMenuBox(
                expanded = tenantExpanded,
                onExpandedChange = { tenantExpanded = !tenantExpanded }
            ) {
                OutlinedTextField(
                    value = selectedTenant?.name.orEmpty(),
                    onValueChange = {},
                    label = { Text("Tienda a visualizar") },
                    readOnly = true,
                    enabled = !isLoading && !isLoadingTenants,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tenantExpanded) },
                    modifier = Modifier.menuAnchor().width(280.dp)
                )
                ExposedDropdownMenu(
                    expanded = tenantExpanded,
                    onDismissRequest = { tenantExpanded = false }
                ) {
                    tenants.forEach { tenant ->
                        DropdownMenuItem(
                            text = { Text(tenant.name) },
                            onClick = {
                                onTenantChange(tenant.id)
                                tenantExpanded = false
                            }
                        )
                    }
                }
            }
            if (tenants.isEmpty() && !isLoadingTenants) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No hay tiendas cargadas todavía. Podés crear la cuenta igual y adherirte luego desde Inicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Nombre y apellido") },
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("Teléfono celular (opcional)") },
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
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
        if (!successMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = successMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                onSubmit(
                    email.trim(),
                    password,
                    storeName.trim(),
                    storeAddress.trim(),
                    storePhone.trim(),
                    skuPrefix.trim(),
                    selectedTenantId,
                    selectedTenant?.name,
                    customerName.trim(),
                    customerPhone.takeIf { it.isNotBlank() }?.trim(),
                    mode
                )
            },
            enabled = !isLoading &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                (mode == RegisterMode.STORE_OWNER &&
                    storeName.isNotBlank() &&
                    storeAddress.isNotBlank() &&
                    storePhone.isNotBlank() &&
                    (skuPrefix.isBlank() || skuPrefix.length >= 3) ||
                    mode == RegisterMode.FINAL_CUSTOMER &&
                    customerName.isNotBlank())
        ) {
            Text(if (isLoading) "Creando..." else "Crear cuenta")
        }
        if (mode == RegisterMode.FINAL_CUSTOMER) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onGoogleSignInClick(selectedTenantId, selectedTenant?.name) },
                enabled = !isLoading && !selectedTenantId.isNullOrBlank()
            ) {
                Text("Crear con Google")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onLoginClick, enabled = !isLoading) {
            Text("Ya tengo cuenta")
        }
    }
}