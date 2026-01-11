package com.example.selliaapp.ui.screens.providers

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.stock.ReorderSuggestion
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ProviderSuggestionsViewModel
import kotlin.math.roundToInt

@Composable
fun ProviderSuggestionsScreen(
    vm: ProviderSuggestionsViewModel,
    onCreateOrder: (Int) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    Scaffold(topBar = { BackTopAppBar(title = "Sugerencias de compra", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = vm::refresh) { Text("Actualizar") }
            }

            state.errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (state.loading) {
                Text("Cargando sugerencias...")
            } else if (state.suggestions.isEmpty()) {
                Text("Sin sugerencias: el stock está cubierto según la venta reciente.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.suggestions) { suggestion ->
                        SuggestionCard(suggestion = suggestion, onCreateOrder = onCreateOrder)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ReorderSuggestion,
    onCreateOrder: (Int) -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = suggestion.providerName?.takeIf { it.isNotBlank() }
                            ?: "Sin proveedor asignado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Reponer ${suggestion.suggestedOrderQty}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Stock actual: ${suggestion.currentStock} · Mínimo: ${suggestion.minStock}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val avgDaily = String.format("%.2f", suggestion.avgDailySales)
            val coverage = suggestion.projectedDaysOfStock?.roundToInt()?.let { "$it días" } ?: "N/A"
            Text(
                text = "Ventas ${suggestion.windowDays} días: ${suggestion.soldLastDays} · Rotación: $avgDaily / día · Cobertura: $coverage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))
            Button(onClick = { onCreateOrder(suggestion.productId) }) {
                Text("Crear orden")
            }
        }
    }
}
