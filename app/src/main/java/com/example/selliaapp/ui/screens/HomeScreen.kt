package com.example.selliaapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.selliaapp.BuildConfig
import com.example.selliaapp.ui.components.AccountAvatarMenu
import com.example.selliaapp.ui.components.AccountSummary
import com.example.selliaapp.viewmodel.HomeViewModel
import com.example.selliaapp.viewmodel.hasOpenCashSession
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

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
    onCashHub: () -> Unit,
    accountSummary: AccountSummary,
    storeName: String,
    storeLogoUrl: String
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var showCashRequired by remember { mutableStateOf(false) }

    BoxWithConstraints {
        val isCompactWidth = maxWidth < 600.dp

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

            CashStatusCard(
                hasOpenCashSession = state.hasOpenCashSession,
                openedBy = state.cashSummary?.session?.openedBy,
                openedAtTime = state.cashSummary?.session?.openedAt
                    ?.atZone(ZoneId.systemDefault())
                    ?.format(dateFormatter),
                expectedAmount = state.cashSummary?.expectedAmount ?: 0.0,
                currencyFormatter = currency,
                onCashOpen = onCashOpen,
                onCashHub = onCashHub
            )

            PrimaryActionCard(
                hasOpenCashSession = state.hasOpenCashSession,
                overdueProviderInvoices = state.overdueProviderInvoices,
                onNewSale = onNewSale,
                onRequireCashOpen = { showCashRequired = true }
            )

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

            KpiSection(
                isCompactWidth = isCompactWidth,
                dailySales = currency.format(state.dailySales),
                tickets = state.cashSummary?.movements
                    ?.count { it.type == "SALE_CASH" }
                    ?.toString() ?: "0",
                averageTicket = currency.format(state.averageTicket)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

            ShortcutsSection(
                isCompactWidth = isCompactWidth,
                onNewSale = onNewSale,
                onStock = onStock,
                onClientes = onClientes,
                onReports = onReports
            )
        }
    }
}

@Composable
private fun CashStatusCard(
    hasOpenCashSession: Boolean,
    openedBy: String?,
    openedAtTime: String?,
    expectedAmount: Double,
    currencyFormatter: NumberFormat,
    onCashOpen: () -> Unit,
    onCashHub: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                Text("Estado de caja", style = MaterialTheme.typography.titleMedium)
            }
            if (!hasOpenCashSession) {
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
                Text(
                    "Abierta ${openedAtTime ?: "-"} · ${openedBy ?: "Operador"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Saldo teórico: ${currencyFormatter.format(expectedAmount)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCashHub, modifier = Modifier.weight(1f)) {
                        Text("Arqueo")
                    }
                    FilledTonalButton(onClick = onCashHub, modifier = Modifier.weight(1f)) {
                        Text("Cerrar caja")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionCard(
    hasOpenCashSession: Boolean,
    overdueProviderInvoices: Int,
    onNewSale: () -> Unit,
    onRequireCashOpen: () -> Unit
) {
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
                    if (!hasOpenCashSession && BuildConfig.REQUIRE_CASH_SESSION_FOR_CASH_PAYMENTS) {
                        onRequireCashOpen()
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
        if (overdueProviderInvoices > 0) {
            Text(
                "Facturas vencidas: $overdueProviderInvoices",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun KpiSection(
    isCompactWidth: Boolean,
    dailySales: String,
    tickets: String,
    averageTicket: String
) {
    val kpis = listOf(
        KpiItem(title = "Ventas hoy", value = dailySales),
        KpiItem(title = "Tickets", value = tickets),
        KpiItem(title = "Ticket promedio", value = averageTicket)
    )

    Text("Resumen del día", style = MaterialTheme.typography.titleMedium)
    if (isCompactWidth) {
        val columns = 2
        val rows = ceil(kpis.size / columns.toDouble()).toInt()
        val gridHeight = (rows * 86).dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(kpis) { item ->
                KpiCard(title = item.title, value = item.value, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            kpis.forEach { item ->
                KpiCard(
                    title = item.title,
                    value = item.value,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ShortcutsSection(
    isCompactWidth: Boolean,
    onNewSale: () -> Unit,
    onStock: () -> Unit,
    onClientes: () -> Unit,
    onReports: () -> Unit
) {
    val shortcuts = listOf(
        ShortcutItem("Vender", Icons.Default.PointOfSale, onNewSale),
        ShortcutItem("Stock", Icons.Default.Inventory2, onStock),
        ShortcutItem("Clientes", Icons.Default.People, onClientes),
        ShortcutItem("Reportes", Icons.Default.QueryStats, onReports)
    )

    Text("Atajos", style = MaterialTheme.typography.titleMedium)
    if (isCompactWidth) {
        val columns = 2
        val rows = ceil(shortcuts.size / columns.toDouble()).toInt()
        val gridHeight = (rows * 64).dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(shortcuts) { item ->
                ShortcutButton(
                    label = item.label,
                    icon = item.icon,
                    onClick = item.onClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            shortcuts.take(2).forEach { item ->
                ShortcutButton(item.label, item.icon, item.onClick, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            shortcuts.drop(2).forEach { item ->
                ShortcutButton(item.label, item.icon, item.onClick, Modifier.weight(1f))
            }
        }
    }
}

private data class KpiItem(
    val title: String,
    val value: String
)

private data class ShortcutItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

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
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
