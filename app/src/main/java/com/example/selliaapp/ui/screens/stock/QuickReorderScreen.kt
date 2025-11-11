package com.example.selliaapp.ui.screens.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.QuickReorderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReorderScreen(
    state: QuickReorderState,
    onBack: () -> Unit,
    onProviderSelected: (Int) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitPriceChange: (String) -> Unit,
    onToggleReceive: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    Scaffold(topBar = { BackTopAppBar(title = "Crear orden", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val product = state.product
            Text(
                text = product?.name ?: "Producto no disponible",
                style = MaterialTheme.typography.headlineSmall
            )
            if (product != null) {
                Text(
                    text = "Stock actual: ${product.quantity}  •  Mínimo: ${product.minStock ?: 0}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            var providerExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedProvider = state.providers.firstOrNull { it.id == state.selectedProviderId }
                OutlinedTextField(
                    value = selectedProvider?.name ?: "Seleccionar proveedor",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("Proveedor") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    state.providers.forEach { provider ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                onProviderSelected(provider.id)
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.providers.isEmpty()) {
                Text(
                    text = "No hay proveedores disponibles. Agregá uno desde el módulo de proveedores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.quantityText,
                onValueChange = onQuantityChange,
                label = { Text("Cantidad a ordenar") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.unitPriceText,
                onValueChange = onUnitPriceChange,
                label = { Text("Precio unitario (con IVA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = state.autoReceive,
                    onCheckedChange = onToggleReceive,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Actualizar stock inmediatamente")
            }

            Text(
                text = "Total estimado: ${state.totalText}",
                style = MaterialTheme.typography.titleMedium
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.isSaving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSaving && state.product != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Guardar orden")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}
