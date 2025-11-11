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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import com.example.selliaapp.viewmodel.ReportsFilter
import com.example.selliaapp.viewmodel.ReportsViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla de reportes centrada en las ventas de los últimos 7 días.
 */
@Composable
fun ReportsScreen(
    vm: ReportsViewModel = hiltViewModel(),
    onBack: () -> Boolean,
    navController: NavController,
) {
    val state by vm.state.collectAsState()
    val localeEsAr = Locale("es", "AR")
    val currency = NumberFormat.getCurrencyInstance(localeEsAr)

    LaunchedEffect(Unit) {
        vm.onFilterChange(ReportsFilter.WEEK)
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
                    state.loading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    state.error != null -> {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    state.points.isEmpty() -> {
                        Text(
                            text = "Sin ventas registradas en los últimos 7 días.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text(
                                text = "Ventas de los últimos 7 días",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val primerosTres = state.points.take(3)
                            val restantes = state.points.drop(3)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                primerosTres.forEach { (label, value) ->
                                    ReportRow(label = label, value = value, currency = currency)
                                }
                            }

                            if (restantes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Más días",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(restantes) { (label, value) ->
                                        ReportRow(label = label, value = value, currency = currency)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f, fill = true))
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total semanal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currency.format(state.total),
                                    style = MaterialTheme.typography.titleMedium
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
        }
    }
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
