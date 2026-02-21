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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.selliaapp.ui.navigation.Routes
import com.example.selliaapp.repository.StockValuationReport
import com.example.selliaapp.repository.StockValuationScenario
import com.example.selliaapp.viewmodel.ReportsFilter
import com.example.selliaapp.viewmodel.ReportsViewModel
import java.text.NumberFormat
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
) {
    val state by vm.state.collectAsState()
    val localeEsAr = Locale("es", "AR")
    val currency = NumberFormat.getCurrencyInstance(localeEsAr)

    LaunchedEffect(Unit) {
        vm.onFilterChange(ReportsFilter.DAY)
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
        }
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
        ReportsFilter.DAY -> "Ventas de hoy"
        ReportsFilter.WEEK -> "Ventas de la semana"
        ReportsFilter.MONTH -> "Ventas del mes"
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
