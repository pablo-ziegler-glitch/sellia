package com.example.selliaapp.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.selliaapp.ui.navigation.Routes
import com.example.selliaapp.repository.AdvancedSalesInsights
import com.example.selliaapp.repository.CategoryShareItem
import com.example.selliaapp.repository.PeriodComparison
import com.example.selliaapp.repository.ProductRankingItem
import com.example.selliaapp.repository.StockValuationReport
import com.example.selliaapp.repository.StockValuationScenario
import com.example.selliaapp.repository.TrendDirection
import com.example.selliaapp.viewmodel.ReportsFilter
import com.example.selliaapp.viewmodel.ReportsViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de reportes centrada en las ventas del día.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    vm: ReportsViewModel = hiltViewModel(),
    onBack: () -> Boolean,
    navController: NavController,
    canAccessPriceSummary: Boolean,
    canAccessProfitReport: Boolean = false,
) {
    val state by vm.state.collectAsState()
    val localeEsAr = Locale("es", "AR")
    val currency = NumberFormat.getCurrencyInstance(localeEsAr)
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd/MM/yy") }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        vm.onFilterChange(ReportsFilter.DAY)
    }

    // From date picker
    if (showFromPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.customFrom
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        pendingFrom = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showFromPicker = false
                    showToPicker = true
                }) { Text("Siguiente") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("Fecha desde", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    // To date picker
    if (showToPicker) {
        val from = pendingFrom ?: state.customFrom
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.customTo
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val to = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.loadCustomRange(from, if (to.isBefore(from)) from else to)
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("Fecha hasta", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Reportes") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            // Presets de período
            FilterPresetsRow(
                selected = state.filter,
                onSelect = { filter ->
                    if (filter == ReportsFilter.CUSTOM) {
                        showFromPicker = true
                    } else {
                        vm.onFilterChange(filter)
                    }
                }
            )

            if (state.filter == ReportsFilter.CUSTOM) {
                Text(
                    text = "${state.customFrom.format(dateFmt)} – ${state.customTo.format(dateFmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            ) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                SalesSummarySection(
                                    points = state.points,
                                    total = state.total,
                                    currency = currency,
                                    filter = state.filter
                                )
                            }
                            item {
                                HorizontalDivider()
                            }
                            item {
                                AdvancedInsightsSection(
                                    insights = state.advancedInsights,
                                    currency = currency,
                                    filter = state.filter
                                )
                            }
                            item {
                                HorizontalDivider()
                            }
                            item {
                                StockValuationSection(
                                    report = state.stockValuation,
                                    currency = currency
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(Routes.SalesInvoices.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver facturas de venta")
            }
            if (canAccessPriceSummary) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { navController.navigate(Routes.PriceSummary.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Resumen de precios (solo dueño)")
                }
            }
            if (canAccessProfitReport) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { navController.navigate(Routes.SalesProfitReport.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reporte de ganancias (solo dueño)")
                }
            }
        }
    }
}

@Composable
private fun AdvancedInsightsSection(
    insights: AdvancedSalesInsights?,
    currency: NumberFormat,
    filter: ReportsFilter
) {
    Text(
        text = "Analytics avanzado",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (insights == null) {
        Text(
            text = "Sin datos suficientes para analytics avanzado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    TrendComparisonCard(
        comparison = insights.comparison,
        currency = currency,
        filter = filter
    )
    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(10.dp))
    RankingSection(
        title = "Top productos por unidades",
        rows = insights.topProductsByUnits,
        currency = currency,
        metric = { "${it.units} u." }
    )
    Spacer(modifier = Modifier.height(10.dp))
    RankingSection(
        title = "Top productos por ingreso",
        rows = insights.topProductsByRevenue,
        currency = currency,
        metric = { currency.format(it.revenue) }
    )
    Spacer(modifier = Modifier.height(10.dp))
    CategoryShareSection(
        rows = insights.categoryShares,
        currency = currency
    )
}

@Composable
private fun TrendComparisonCard(
    comparison: PeriodComparison,
    currency: NumberFormat,
    filter: ReportsFilter
) {
    val periodLabel = when (filter) {
        ReportsFilter.DAY -> "día anterior"
        ReportsFilter.WEEK -> "semana anterior"
        ReportsFilter.MONTH -> "mes anterior"
        ReportsFilter.CUSTOM -> "período anterior equivalente"
    }
    val trendLabel = when (comparison.trend) {
        TrendDirection.UP -> "▲ Tendencia alcista"
        TrendDirection.DOWN -> "▼ Tendencia bajista"
        TrendDirection.FLAT -> "▬ Tendencia estable"
    }
    val trendColor = when (comparison.trend) {
        TrendDirection.UP -> Color(0xFF2E7D32)
        TrendDirection.DOWN -> MaterialTheme.colorScheme.error
        TrendDirection.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val deltaText = buildString {
        append(if (comparison.delta >= 0.0) "+" else "")
        append(currency.format(comparison.delta))
        comparison.deltaPercent?.let { percent ->
            append(" (")
            append(if (percent >= 0.0) "+" else "")
            append(String.format(Locale.US, "%.1f", percent))
            append("%)")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Comparación vs $periodLabel",
            style = MaterialTheme.typography.titleSmall
        )
        ResumenCheckoutLikeRow("Total actual", currency.format(comparison.currentTotal))
        ResumenCheckoutLikeRow("Total previo", currency.format(comparison.previousTotal))
        ResumenCheckoutLikeRow(
            "Delta",
            deltaText,
            valueColor = trendColor
        )
        Text(
            text = trendLabel,
            style = MaterialTheme.typography.bodySmall,
            color = trendColor
        )
    }
}

@Composable
private fun RankingSection(
    title: String,
    rows: List<ProductRankingItem>,
    currency: NumberFormat,
    metric: (ProductRankingItem) -> String
) {
    Text(text = title, style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    if (rows.isEmpty()) {
        Text(
            text = "Sin ventas para calcular ranking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${row.productName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${metric(row)} • ${currency.format(row.revenue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryShareSection(
    rows: List<CategoryShareItem>,
    currency: NumberFormat
) {
    Text(text = "Ventas por categoría", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    if (rows.isEmpty()) {
        Text(
            text = "Sin categorías vendidas en el período.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            val percent = String.format(Locale.US, "%.1f", row.sharePercent)
            ResumenCheckoutLikeRow(
                label = row.category,
                value = "${row.units} u. • ${currency.format(row.revenue)} • $percent%"
            )
        }
    }
}

@Composable
private fun ResumenCheckoutLikeRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPresetsRow(
    selected: ReportsFilter,
    onSelect: (ReportsFilter) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == ReportsFilter.DAY,
            onClick = { onSelect(ReportsFilter.DAY) },
            label = { Text("Hoy") }
        )
        FilterChip(
            selected = selected == ReportsFilter.WEEK,
            onClick = { onSelect(ReportsFilter.WEEK) },
            label = { Text("Esta semana") }
        )
        FilterChip(
            selected = selected == ReportsFilter.MONTH,
            onClick = { onSelect(ReportsFilter.MONTH) },
            label = { Text("Este mes") }
        )
        FilterChip(
            selected = selected == ReportsFilter.CUSTOM,
            onClick = { onSelect(ReportsFilter.CUSTOM) },
            label = { Text("Personalizado") }
        )
    }
}

@Composable
private fun SalesSummarySection(
    points: List<Pair<String, Double>>,
    total: Double,
    currency: NumberFormat,
    filter: ReportsFilter
) {
    val title = when (filter) {
        ReportsFilter.DAY    -> "Ventas de hoy"
        ReportsFilter.WEEK   -> "Ventas de la semana"
        ReportsFilter.MONTH  -> "Ventas del mes"
        ReportsFilter.CUSTOM -> "Ventas del período"
    }
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    if (points.isEmpty()) {
        Text(
            text = "Sin ventas registradas en este período.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            points.take(6).forEach { (label, value) ->
                ReportRow(label = label, value = value, currency = currency)
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Total período",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = currency.format(total),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun StockValuationSection(
    report: StockValuationReport?,
    currency: NumberFormat
) {
    Text(
        text = "Stock valorizado y ganancias esperadas",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (report == null || report.totalUnitsWithStock == 0) {
        Text(
            text = "No hay stock cargado para calcular valorización.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Text(
        text = "${report.totalProductsWithStock} productos • ${report.totalUnitsWithStock} unidades",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Costo de adquisición total: ${currency.format(report.totalAcquisitionCost)}",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(10.dp))

    report.scenarios.forEachIndexed { index, scenario ->
        ScenarioCard(scenario = scenario, currency = currency)
        if (index != report.scenarios.lastIndex) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ScenarioCard(scenario: StockValuationScenario, currency: NumberFormat) {
    Text(
        text = scenario.label,
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Venta potencial (si se vende todo): ${currency.format(scenario.potentialRevenue)}",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Ganancia esperada sobre costo conocido: ${currency.format(scenario.expectedProfit)}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (scenario.expectedProfit < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "Cobertura: ${scenario.unitsWithKnownCost}/${scenario.unitsWithPrice} unidades con costo de adquisición",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ReportRow(label: String, value: Double, currency: NumberFormat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = currency.format(value),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
