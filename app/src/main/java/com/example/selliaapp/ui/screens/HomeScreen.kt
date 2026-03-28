package com.example.selliaapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.selliaapp.BuildConfig
import com.example.selliaapp.ui.components.AccountAvatarMenu
import com.example.selliaapp.ui.components.AccountSummary
import com.example.selliaapp.data.model.dashboard.DailySalesPoint
import com.example.selliaapp.viewmodel.HomeViewModel
import com.example.selliaapp.viewmodel.hasOpenCashSession
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CashOpenColor = Color(0xFF2E7D32)
private val CashClosedColor = Color(0xFFC62828)
private val CashOnColor = Color.White

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onLatestSales: () -> Unit,
    onPricingSimulator: () -> Unit,
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
    storeLogoUrl: String,
    routeCounts: Map<String, Int> = emptyMap()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var showCashRequired by remember { mutableStateOf(false) }

    val cashSession = state.cashSummary?.session
    val openedTime = remember(cashSession?.openedAt, dateFormatter) {
        cashSession?.openedAt
            ?.atZone(ZoneId.systemDefault())
            ?.format(dateFormatter)
            ?: "-"
    }

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
                    Text("Cancelar")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Store header
            item {
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
            }

            // Cambio 1: Cash status banner – green/red
            item {
                CashStatusBanner(
                    isOpen = state.hasOpenCashSession,
                    openedTime = openedTime,
                    openedBy = cashSession?.openedBy,
                    expectedAmount = currency.format(state.cashSummary?.expectedAmount ?: 0.0),
                    onCashOpen = onCashOpen,
                    onCashAudit = onCashAudit,
                    onCashClose = onCashClose,
                    onCashHub = onCashHub
                )
            }

            // Cambio 2: Sticky daily summary card
            stickyHeader {
                DailySummaryCard(
                    ventas = currency.format(state.dailySales),
                    efectivo = currency.format(state.cashSummary?.cashSalesTotal ?: 0.0),
                    ganancia = currency.format(state.dailyMargin)
                )
            }

            // Mini sales chart
            if (state.weekSales.isNotEmpty()) {
                item {
                    WeeklySalesCard(weekSales = state.weekSales)
                }
            }

            // Primary action card
            item {
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
                }
            }

            // Public catalog card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Catálogo público", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Mostrá productos sin pedir autenticación.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onPublicCatalog, modifier = Modifier.weight(1f)) {
                                Text("Ver catálogo")
                            }
                            OutlinedButton(onClick = onPublicProductScan, modifier = Modifier.weight(1f)) {
                                Text("Escanear QR")
                            }
                        }
                    }
                }
            }

            // Operational alerts card
            item {
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
                        val hasAlerts = state.lowStockAlerts.isNotEmpty() ||
                            state.overdueProviderInvoices > 0 ||
                            state.incompleteProviderNames.isNotEmpty()
                        if (!hasAlerts) {
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
                            if (state.incompleteProviderNames.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onProviders),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Proveedores sin datos (${state.incompleteProviderNames.size})",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        state.incompleteProviderNames.take(2).forEach { name ->
                                            Text(
                                                "• $name — falta teléfono, condición o medio de pago",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cambio 3: Adaptive quick actions (top 3 by usage frequency)
            item {
                AdaptiveShortcutsSection(
                    routeCounts = routeCounts,
                    onNewSale = onNewSale,
                    onStock = onStock,
                    onClientes = onClientes,
                    onReports = onReports,
                    onPricingSimulator = onPricingSimulator,
                    onProviders = onProviders,
                    onExpenses = onExpenses,
                    onLatestSales = onLatestSales
                )
            }
        }
    }
}

// Cambio 1: Cash status banner with unambiguous green/red color
@Composable
private fun CashStatusBanner(
    isOpen: Boolean,
    openedTime: String,
    openedBy: String?,
    expectedAmount: String,
    onCashOpen: () -> Unit,
    onCashAudit: () -> Unit,
    onCashClose: () -> Unit,
    onCashHub: () -> Unit
) {
    val backgroundColor = if (isOpen) CashOpenColor else CashClosedColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isOpen) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = CashOnColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (isOpen) "Caja abierta" else "Caja cerrada",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CashOnColor
                )
            }

            if (!isOpen) {
                Text(
                    "Necesaria para vender con efectivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CashOnColor.copy(alpha = 0.85f)
                )
                Button(
                    onClick = onCashOpen,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CashOnColor,
                        contentColor = CashClosedColor
                    )
                ) {
                    Text("Abrir caja", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "Abierta $openedTime · ${openedBy ?: "Operador"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CashOnColor.copy(alpha = 0.85f)
                )
                Text(
                    expectedAmount,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = CashOnColor
                )
                Text(
                    "Saldo teórico en caja",
                    style = MaterialTheme.typography.labelSmall,
                    color = CashOnColor.copy(alpha = 0.75f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCashAudit,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CashOnColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CashOnColor.copy(alpha = 0.7f))
                    ) {
                        Text("Arqueo")
                    }
                    Button(
                        onClick = onCashClose,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CashOnColor,
                            contentColor = CashOpenColor
                        )
                    ) {
                        Text("Cerrar caja", fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(
                    onClick = onCashHub,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(contentColor = CashOnColor.copy(alpha = 0.85f))
                ) {
                    Text("Ver panel de caja")
                }
            }
        }
    }
}

// Cambio 2: Sticky daily summary card (ventas, efectivo en caja, ganancia)
@Composable
private fun DailySummaryCard(
    ventas: String,
    efectivo: String,
    ganancia: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Resumen del día",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DailySummaryItem("Ventas", ventas, Modifier.weight(1f))
                DailySummaryItem("Efectivo en caja", efectivo, Modifier.weight(1f))
                DailySummaryItem("Ganancia", ganancia, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DailySummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Cambio 3: Adaptive shortcuts – top 3 by usage frequency from routeCounts
@Composable
private fun AdaptiveShortcutsSection(
    routeCounts: Map<String, Int>,
    onNewSale: () -> Unit,
    onStock: () -> Unit,
    onClientes: () -> Unit,
    onReports: () -> Unit,
    onPricingSimulator: () -> Unit,
    onProviders: () -> Unit,
    onExpenses: () -> Unit,
    onLatestSales: () -> Unit
) {
    val allShortcuts = listOf(
        ShortcutItem("Vender", Icons.Default.PointOfSale, "sell", onNewSale),
        ShortcutItem("Stock", Icons.Default.Inventory2, "stock", onStock),
        ShortcutItem("Clientes", Icons.Default.People, "clients_hub", onClientes),
        ShortcutItem("Reportes", Icons.Default.QueryStats, "reports", onReports),
        ShortcutItem("Sim. costos", Icons.Default.Calculate, "pricing_simulator", onPricingSimulator),
        ShortcutItem("Proveedores", Icons.AutoMirrored.Filled.ReceiptLong, "providers_hub", onProviders),
        ShortcutItem("Gastos", Icons.Default.Money, "expenses_hub", onExpenses),
        ShortcutItem("Últimas ventas", Icons.Default.CheckCircle, "sales_invoices", onLatestSales)
    )

    val top3 = allShortcuts
        .sortedByDescending { routeCounts[it.route] ?: 0 }
        .take(3)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            top3.forEach { item ->
                ShortcutButton(item.label, item.icon, item.onClick, Modifier.weight(1f))
            }
        }
    }
}

private data class ShortcutItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
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

@Composable
private fun WeeklySalesCard(weekSales: List<DailySalesPoint>) {
    val today = LocalDate.now()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Ventas últimos 7 días",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            MiniSalesBarChart(weekSales = weekSales, today = today)
        }
    }
}

@Composable
private fun MiniSalesBarChart(weekSales: List<DailySalesPoint>, today: LocalDate) {
    val maxTotal = weekSales.maxOf { it.total }.coerceAtLeast(0.01)
    val todayIndex = weekSales.indexOfFirst { it.fecha == today }
    val primaryColor = MaterialTheme.colorScheme.primary

    val dayLabels = weekSales.map { point ->
        if (point.fecha == today) "Hoy" else point.fecha.dayOfMonth.toString()
    }

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val count = weekSales.size
            val spacing = 4.dp.toPx()
            val barWidth = (size.width - spacing * (count - 1)) / count

            weekSales.forEachIndexed { i, point ->
                val barHeight = (point.total / maxTotal * size.height).toFloat()
                    .coerceAtLeast(2.dp.toPx())
                val left = i * (barWidth + spacing)
                val top = size.height - barHeight
                drawRect(
                    color = if (i == todayIndex) primaryColor else primaryColor.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == todayIndex)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
