package com.example.selliaapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.BuildConfig
import com.example.selliaapp.ui.components.AccountAvatarMenu
import com.example.selliaapp.ui.components.AccountSummary
import com.example.selliaapp.viewmodel.HomeViewModel
import com.example.selliaapp.viewmodel.hasOpenCashSession
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onNewSale: () -> Unit,
    onStock: () -> Unit,
    onClientes: () -> Unit,
    onConfig: () -> Unit,
    onReports: () -> Unit,
    onProviders: () -> Unit,
    onExpenses: () -> Unit,
    onPublicCatalog: () -> Unit,
    onPublicProductScan: () -> Unit,
    onSyncNow: () -> Unit = {},
    onViewStockMovements: () -> Unit = {},
    onAlertAdjustStock: (Int) -> Unit = {},
    onAlertCreatePurchase: (Int) -> Unit = {},
    onCashOpen: () -> Unit,
    onCashAudit: () -> Unit,
    onCashClose: () -> Unit,
    onCashHub: () -> Unit,
    accountSummary: AccountSummary,
    storeName: String,
    storeLogoUrl: String
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var showCashRequired by remember { mutableStateOf(false) }

    if (showCashRequired) {
        AlertDialog(
            onDismissRequest = { showCashRequired = false },
            title = { Text("Abrir caja") },
            text = {
                Text(
                    "Para cobrar en efectivo necesitás abrir la caja. " +
                        "Podés continuar con transferencia o tarjeta si querés."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCashRequired = false
                        onCashOpen()
                    }
                ) {
                    Text("Abrir caja")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashRequired = false }) {
                    Text("Seguir vendiendo")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StoreLogo(logoUrl = storeLogoUrl, contentDescription = storeName)
                Text(
                    text = storeName.ifBlank { "Tu tienda" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AccountAvatarMenu(accountSummary = accountSummary)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null)
                    Text("Estado de caja", style = MaterialTheme.typography.titleMedium)
                }
                if (!state.hasOpenCashSession) {
                    Text(
                        "Caja cerrada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Necesaria para vender con efectivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onCashOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir caja")
                    }
                } else {
                    val session = state.cashSummary?.session
                    val openedAt = session?.openedAt?.atZone(ZoneId.systemDefault())
                    val openedTime = openedAt?.format(dateFormatter) ?: "-"
                    Text(
                        "Abierta ${openedTime} · ${session?.openedBy ?: "Operador"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Saldo teórico: ${currency.format(state.cashSummary?.expectedAmount ?: 0.0)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCashAudit, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Arqueo caja")
                        }
                        FilledTonalButton(onClick = onCashClose, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Cerrar ahora")
                        }
                    }
                    TextButton(onClick = onCashHub, modifier = Modifier.align(Alignment.End)) {
                        Text("Ver panel de caja")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Acción principal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Vender",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Abrí un nuevo ticket y cobrá rápido.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Button(
                    onClick = {
                        if (!state.hasOpenCashSession && BuildConfig.REQUIRE_CASH_SESSION_FOR_CASH_PAYMENTS) {
                            showCashRequired = true
                        } else {
                            onNewSale()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vender")
                }
            }
            if (state.overdueProviderInvoices > 0) {
                Text(
                    "Facturas vencidas: ${state.overdueProviderInvoices}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Catálogo público",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Mostrá productos sin pedir autenticación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onPublicCatalog,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ver catálogo")
                    }
                    OutlinedButton(
                        onClick = onPublicProductScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Escanear QR")
                    }
                }
            }
        }

        Text("Resumen del día", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KpiCard(
                title = "Ventas hoy",
                value = currency.format(state.dailySales),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Tickets",
                value = state.cashSummary?.movements
                    ?.count { it.type == "SALE_CASH" }
                    ?.toString() ?: "0",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Ticket promedio",
                value = currency.format(state.averageTicket),
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text("Alertas operativas", style = MaterialTheme.typography.titleMedium)
                }
                if (state.lowStockAlerts.isEmpty() && state.overdueProviderInvoices == 0) {
                    Text(
                        "Sin alertas por ahora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (state.lowStockAlerts.isNotEmpty()) {
                        Text(
                            "Stock bajo (${state.lowStockAlerts.size})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        state.lowStockAlerts.take(3).forEach { alert ->
                            Text(
                                "• ${alert.name} (${alert.quantity} u.)",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (state.overdueProviderInvoices > 0) {
                        Text(
                            "Facturas vencidas: ${state.overdueProviderInvoices}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Text("Atajos", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShortcutButton("Ventas", Icons.Default.QueryStats, onReports, Modifier.weight(1f))
            ShortcutButton("Stock", Icons.Default.Inventory2, onStock, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShortcutButton("Clientes", Icons.Default.People, onClientes, Modifier.weight(1f))
            ShortcutButton("Reportes", Icons.Default.QueryStats, onReports, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StoreLogo(
    logoUrl: String,
    contentDescription: String
) {
    val size = 36.dp
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraLarge)
    ) {
        if (logoUrl.isNotBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Logo de $contentDescription",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = "Logo de $contentDescription",
                modifier = Modifier
                    .size(size)
                    .padding(6.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
