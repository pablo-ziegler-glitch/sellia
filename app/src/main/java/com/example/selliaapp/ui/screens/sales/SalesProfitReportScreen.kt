package com.example.selliaapp.ui.screens.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.sales.DailyProfitSummary
import com.example.selliaapp.data.model.sales.InvoiceProfitSummary
import com.example.selliaapp.viewmodel.sales.SalesProfitReportViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ReportTab { DIARIO, POR_VENTA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesProfitReportScreen(
    vm: SalesProfitReportViewModel,
    onOpenDetail: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var activeTab by remember { mutableStateOf(ReportTab.DIARIO) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte de Ganancias") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filtros de rango
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Período", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.fromDate == LocalDate.now(),
                            onClick = { vm.selectToday() },
                            label = { Text("Hoy") }
                        )
                        FilterChip(
                            selected = state.fromDate == LocalDate.now().minusDays(29),
                            onClick = { vm.selectLast30Days() },
                            label = { Text("30 días") }
                        )
                        FilterChip(
                            selected = state.fromDate == LocalDate.now().withDayOfMonth(1)
                                && state.toDate == LocalDate.now(),
                            onClick = { vm.selectThisMonth() },
                            label = { Text("Este mes") }
                        )
                    }
                    Text(
                        "${state.fromDate.format(dateFmt)} – ${state.toDate.format(dateFmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Totales del período
            if (!state.isLoading && state.salesList.isNotEmpty()) {
                item {
                    PeriodTotalsCard(
                        items = state.salesList,
                        currency = currency
                    )
                }
            }

            if (state.isLoading) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }

            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
                return@LazyColumn
            }

            if (state.salesList.isEmpty()) {
                item {
                    Text(
                        "No hay ventas en el período seleccionado.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@LazyColumn
            }

            // Tabs de vista
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeTab == ReportTab.DIARIO,
                        onClick = { activeTab = ReportTab.DIARIO },
                        label = { Text("Por día") }
                    )
                    FilterChip(
                        selected = activeTab == ReportTab.POR_VENTA,
                        onClick = { activeTab = ReportTab.POR_VENTA },
                        label = { Text("Por venta") }
                    )
                }
            }

            when (activeTab) {
                ReportTab.DIARIO -> {
                    if (state.dailySummaries.isEmpty()) {
                        item { Text("Sin datos diarios.", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(state.dailySummaries, key = { it.date.toString() }) { day ->
                            DailyProfitCard(day = day, currency = currency, dayFmt = dayFmt)
                        }
                    }
                }
                ReportTab.POR_VENTA -> {
                    items(state.salesList, key = { it.id }) { sale ->
                        SaleProfitCard(
                            sale = sale,
                            currency = currency,
                            dateFmt = dateFmt,
                            onClick = { onOpenDetail(sale.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodTotalsCard(items: List<InvoiceProfitSummary>, currency: NumberFormat) {
    val totalRevenue = items.sumOf { it.total }
    val itemsWithCost = items.filter { it.purchaseCost != null }
    val totalCost = if (itemsWithCost.size == items.size) items.sumOf { it.purchaseCost!! } else null
    val totalNet = if (itemsWithCost.size == items.size) items.sumOf { it.netGain ?: 0.0 } else null
    val netColor = if ((totalNet ?: 0.0) >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Resumen del período", style = MaterialTheme.typography.titleMedium)
            Text("${items.size} ventas", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            ProfitRow("Ingresos totales", currency.format(totalRevenue))
            ProfitRow("Costo de mercadería", if (totalCost != null) "-${currency.format(totalCost)}" else "Sin datos")
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Ganancia neta total",
                    style = MaterialTheme.typography.titleSmall,
                    color = netColor
                )
                Text(
                    if (totalNet != null) currency.format(totalNet) else "Sin datos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = netColor
                )
            }
        }
    }
}

@Composable
private fun DailyProfitCard(
    day: DailyProfitSummary,
    currency: NumberFormat,
    dayFmt: DateTimeFormatter
) {
    val netColor = if ((day.totalNetGain ?: 0.0) >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.date.format(dayFmt), style = MaterialTheme.typography.titleSmall)
                Text("${day.saleCount} ventas", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            ProfitRow("Ingresos", currency.format(day.totalRevenue))
            ProfitRow("Costo mercadería", if (day.totalPurchaseCost != null) "-${currency.format(day.totalPurchaseCost)}" else "Sin datos")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ganancia neta", style = MaterialTheme.typography.bodyMedium, color = netColor)
                Text(if (day.totalNetGain != null) currency.format(day.totalNetGain) else "Sin datos", style = MaterialTheme.typography.bodyMedium, color = netColor)
            }
        }
    }
}

@Composable
private fun SaleProfitCard(
    sale: InvoiceProfitSummary,
    currency: NumberFormat,
    dateFmt: DateTimeFormatter,
    onClick: () -> Unit
) {
    val netColor = if ((sale.netGain ?: 0.0) >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sale.number, style = MaterialTheme.typography.titleSmall)
                Text(sale.date.format(dateFmt), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                sale.customerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sale.paymentMethod,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            ProfitRow("Venta", currency.format(sale.total))
            ProfitRow("Costo", if (sale.purchaseCost != null) "-${currency.format(sale.purchaseCost)}" else "Sin datos")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ganancia neta", style = MaterialTheme.typography.bodyMedium, color = netColor)
                Text(if (sale.netGain != null) currency.format(sale.netGain) else "Sin datos", style = MaterialTheme.typography.bodyMedium, color = netColor)
            }
        }
    }
}

@Composable
private fun ProfitRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
