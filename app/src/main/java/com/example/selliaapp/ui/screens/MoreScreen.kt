package com.example.selliaapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onStock: () -> Unit,
    onStockHistory: () -> Unit,
    onCustomers: () -> Unit,
    onProviders: () -> Unit,
    onExpenses: () -> Unit,
    onReports: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit
) {
    Surface {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Más", style = MaterialTheme.typography.headlineSmall)

            SectionTitle("Operación")
            ListItem(
                headlineContent = { Text("Stock") },
                leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStock),
                supportingContent = { Text("Productos y ajustes") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )
            ListItem(
                headlineContent = { Text("Historial de stock") },
                leadingContent = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStockHistory),
                supportingContent = { Text("Entradas y salidas") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )
            ListItem(
                headlineContent = { Text("Clientes") },
                leadingContent = { Icon(Icons.Default.People, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCustomers),
                supportingContent = { Text("Ventas por cliente") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )
            ListItem(
                headlineContent = { Text("Proveedores") },
                leadingContent = { Icon(Icons.Default.Store, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProviders),
                supportingContent = { Text("Facturas y pagos") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )

            Divider()
            SectionTitle("Finanzas")
            ListItem(
                headlineContent = { Text("Gastos") },
                leadingContent = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpenses),
                supportingContent = { Text("Ingresos y egresos") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )
            ListItem(
                headlineContent = { Text("Reportes") },
                leadingContent = { Icon(Icons.Default.Assessment, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReports),
                supportingContent = { Text("Ventas, márgenes y KPIs") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )

            Divider()
            SectionTitle("Sistema")
            ListItem(
                headlineContent = { Text("Configuración") },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSettings),
                supportingContent = { Text("Usuarios, precios y marketing") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )
            ListItem(
                headlineContent = { Text("Sincronizar") },
                leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSync),
                supportingContent = { Text("Enviar cambios pendientes") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = null,
                tonalElevation = null
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
    )
}
