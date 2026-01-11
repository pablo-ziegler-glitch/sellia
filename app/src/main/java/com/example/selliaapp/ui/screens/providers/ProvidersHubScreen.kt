package com.example.selliaapp.ui.screens.providers


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.ui.components.BackTopAppBar

@Composable
fun ProvidersHubScreen(
    onManageProviders: () -> Unit,
    onProviderInvoices: () -> Unit,
    onProviderPayments: () -> Unit,
    onSuggestions: () -> Unit,
    lowStockAlerts: List<LowStockProduct>,
    onAlertCreateOrder: (Int) -> Unit,
    onAlertAdjustStock: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = { BackTopAppBar(title = "Proveedores", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onManageProviders) { Text("Gestionar Proveedores (CRUD)") }
            Button(onClick = onProviderInvoices) { Text("Boletas/Facturas por Proveedor") }
            Button(onClick = onProviderPayments) { Text("Pagos a Proveedores (Pendientes)") }
            Button(onClick = onSuggestions) { Text("Sugerencias de compra") }

            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Stock crítico",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (lowStockAlerts.isEmpty()) {
                        Text(
                            text = "Sin alertas: el stock está dentro de los mínimos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lowStockAlerts.forEachIndexed { index, alert ->
                            if (index > 0) {
                                Divider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alert.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Stock ${alert.quantity} / mínimo ${alert.minStock}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Faltan ${alert.deficit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = { onAlertAdjustStock(alert.id) }) {
                                        Text("Ajustar stock")
                                    }
                                    TextButton(onClick = { onAlertCreateOrder(alert.id) }) {
                                        Text("Crear orden")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
