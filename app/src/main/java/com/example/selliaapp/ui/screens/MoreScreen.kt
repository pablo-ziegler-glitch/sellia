package com.example.selliaapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.ui.components.AccountAvatarMenu
import com.example.selliaapp.ui.components.AccountSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onStockHistory: () -> Unit,
    onProviders: () -> Unit,
    onExpenses: () -> Unit,
    onReports: () -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    accountSummary: AccountSummary,
    role: AppRole
) {
    Surface {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Más",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                AccountAvatarMenu(accountSummary = accountSummary)
            }


            if (role == AppRole.VIEWER) {
                SectionTitle("Cuenta")
                ListItem(
                    headlineContent = { Text("Configuración de perfil") },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSettings),
                    supportingContent = { Text("Datos de cuenta y perfil") },
                    trailingContent = null,
                    overlineContent = null
                )
                ListItem(
                    headlineContent = { Text("Cerrar sesión") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSignOut),
                    supportingContent = { Text("Salir para ingresar con otra cuenta") },
                    trailingContent = null,
                    overlineContent = null
                )
                Spacer(Modifier.height(8.dp))
                return@Surface
            }

            if (role == AppRole.CASHIER) {
                SectionTitle("Operación diaria")
                ListItem(
                    headlineContent = { Text("Historial de stock") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStockHistory),
                    supportingContent = { Text("Entradas y salidas") },
                    trailingContent = null,
                    overlineContent = null
                )

                HorizontalDivider()
                SectionTitle("Sistema")
                ListItem(
                    headlineContent = { Text("Reportes") },
                    leadingContent = { Icon(Icons.Default.Assessment, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onReports),
                    supportingContent = { Text("Ventas, márgenes y KPIs") },
                    trailingContent = null,
                    overlineContent = null
                )
                ListItem(
                    headlineContent = { Text("Configuración") },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSettings),
                    supportingContent = { Text("Perfil y ajustes operativos") },
                    trailingContent = null,
                    overlineContent = null
                )
                ListItem(
                    headlineContent = { Text("Cerrar sesión") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSignOut),
                    supportingContent = { Text("Salir para ingresar con otra cuenta") },
                    trailingContent = null,
                    overlineContent = null
                )
                Spacer(Modifier.height(8.dp))
                return@Surface
            }

            SectionTitle("Operación diaria")
            ListItem(
                headlineContent = { Text("Proveedores") },
                leadingContent = { Icon(Icons.Default.Store, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProviders),
                supportingContent = { Text("Facturas y pagos") },
                trailingContent = null,
                overlineContent = null
            )

            ListItem(
                headlineContent = { Text("Historial de stock") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStockHistory),
                supportingContent = { Text("Entradas y salidas") },
                trailingContent = null,
                overlineContent = null
            )

            HorizontalDivider()
            SectionTitle("Finanzas")
            ListItem(
                headlineContent = { Text("Gastos") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpenses),
                supportingContent = { Text("Ingresos y egresos") },
                trailingContent = null,
                overlineContent = null
            )
            ListItem(
                headlineContent = { Text("Reportes") },
                leadingContent = { Icon(Icons.Default.Assessment, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReports),
                supportingContent = { Text("Ventas, márgenes y KPIs") },
                trailingContent = null,
                overlineContent = null
            )
            ListItem(
                headlineContent = { Text("Alertas de uso") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAlerts),
                supportingContent = { Text("Límites y consumo del plan") },
                trailingContent = null,
                overlineContent = null,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            )

            HorizontalDivider()
            SectionTitle("Sistema")
            ListItem(
                headlineContent = { Text("Configuración") },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSettings),
                supportingContent = { Text("Usuarios, precios y marketing") },
                trailingContent = null,
                overlineContent = null
            )
            ListItem(
                headlineContent = { Text("Cerrar sesión") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSignOut),
                supportingContent = { Text("Salir para ingresar con otra cuenta") },
                trailingContent = null,
                overlineContent = null
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
