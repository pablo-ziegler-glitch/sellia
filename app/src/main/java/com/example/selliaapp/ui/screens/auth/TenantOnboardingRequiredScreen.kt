package com.example.selliaapp.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.TenantSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantOnboardingRequiredScreen(
    isLoading: Boolean,
    isLoadingTenants: Boolean,
    errorMessage: String?,
    tenants: List<TenantSummary>,
    selectedTenantId: String?,
    onTenantChange: (String) -> Unit,
    onSubmit: (String?, String?) -> Unit,
    onSignOut: () -> Unit
) {
    var tenantExpanded by remember { mutableStateOf(false) }
    val selectedTenant = tenants.firstOrNull { it.id == selectedTenantId }
    val hasTenants = tenants.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Elegí una tienda para continuar",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tu cuenta quedó autenticada pero falta vincularla a una tienda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (hasTenants) {
            ExposedDropdownMenuBox(
                expanded = tenantExpanded,
                onExpandedChange = { tenantExpanded = !tenantExpanded }
            ) {
                OutlinedTextField(
                    value = selectedTenant?.name.orEmpty(),
                    onValueChange = {},
                    label = { Text("Tienda") },
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
        } else {
            Text(
                text = "Todavía no hay tiendas publicadas para visualizar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (hasTenants) {
            Button(
                onClick = { onSubmit(selectedTenant?.id, selectedTenant?.name) },
                enabled = !isLoading && !selectedTenantId.isNullOrBlank()
            ) {
                Text(if (isLoading) "Guardando..." else "Continuar")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Button(onClick = onSignOut, enabled = !isLoading) {
            Text(if (hasTenants) "Salir" else "Cerrar sesión")
        }
    }
}
