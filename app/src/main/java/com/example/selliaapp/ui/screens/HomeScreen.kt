package com.example.selliaapp.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.selliaapp.R
import com.example.selliaapp.viewmodel.HomeViewModel
import com.example.selliaapp.viewmodel.HomeKpi
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.layout.ContentScale
/**
 * Pantalla principal con búsqueda, accesos rápidos y listado de ventas recientes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onNewSale: () -> Unit,
    onStock: () -> Unit,
    onClientes: () -> Unit,
    onConfig: () -> Unit,
    onReports: () -> Unit,
    onProviders: () -> Unit,          // NUEVO
    onExpenses: () -> Unit,
    onPublicProductScan: () -> Unit,
    onSyncNow: () -> Unit = {},
    onViewStockMovements: () -> Unit = {},
    onAlertAdjustStock: (Int) -> Unit = {},
    onAlertCreatePurchase: (Int) -> Unit = {}
    ) {
    val state by vm.state.collectAsState()
    val localeEsAr = Locale("es", "AR")
    val currency = NumberFormat.getCurrencyInstance(localeEsAr)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.valkirja_log),
                    contentDescription = "Logo Valkirja",
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Fit
                )
                Column {
                    Text(
                        text = "Valkirja",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Indumentaria y accesorios para chicos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.errorMessage?.let { mensaje ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = mensaje,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            KpiModuleContainer(
                isLoading = state.isLoading,
                selectedKpis = state.selectedKpis,
                dailySales = state.dailySales,
                dailyMargin = state.dailyMargin,
                averageTicket = state.averageTicket,
                monthTotal = state.monthTotal,
                currency = currency
            )

            PendingTasksSection(
                restockCount = state.lowStockAlerts.size,
                overdueInvoices = state.overdueProviderInvoices
            )

            // Botonera superior (puedes ajustar layout si querés 3 por fila)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onStock, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Stock")
                    }
                    OutlinedButton(onClick = onClientes, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Business, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Clientes")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onConfig, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Configuración")
                    }
                    OutlinedButton(onClick = onReports, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.InsertChart, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Reportes")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onProviders, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Business, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Proveedores")
                    }
                    OutlinedButton(onClick = onExpenses, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Administración Gastos")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onSyncNow, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Sincronizar Ahora!")
                    }
                    OutlinedButton(onClick = onViewStockMovements, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Historial de stock")
                    }
                }
                OutlinedButton(onClick = onPublicProductScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear QR (cliente)")
                }
            }

            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Alertas de stock",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (state.lowStockAlerts.isEmpty()) {
                        Text(
                            text = "Sin alertas: el stock está dentro de los mínimos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.lowStockAlerts.forEachIndexed { index, alert ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    TextButton(onClick = { onAlertCreatePurchase(alert.id) }) {
                                        Text("Crear orden")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onNewSale,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "VENDER",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

@Composable
private fun KpiModuleContainer(
    isLoading: Boolean,
    selectedKpis: List<HomeKpi>,
    dailySales: Double,
    dailyMargin: Double,
    averageTicket: Double,
    monthTotal: Double,
    currency: NumberFormat
) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "KPIs",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "Total vendido (mes): ${if (isLoading) "Cargando…" else currency.format(monthTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            selectedKpis.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { kpi ->
                        KpiCard(
                            modifier = Modifier.weight(1f),
                            title = kpi.label,
                            value = if (isLoading) {
                                "Cargando…"
                            } else {
                                currency.format(kpiValue(kpi, dailySales, dailyMargin, averageTicket))
                            }
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier,
    title: String,
    value: String
) {
    ElevatedCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun kpiValue(
    kpi: HomeKpi,
    dailySales: Double,
    dailyMargin: Double,
    averageTicket: Double
): Double =
    when (kpi) {
        HomeKpi.DAILY_SALES -> dailySales
        HomeKpi.MARGIN -> dailyMargin
        HomeKpi.AVG_TICKET -> averageTicket
    }

@Composable
private fun PendingTasksSection(
    restockCount: Int,
    overdueInvoices: Int
) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tareas pendientes",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reposición",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = restockCount.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Facturas vencidas",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = overdueInvoices.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
