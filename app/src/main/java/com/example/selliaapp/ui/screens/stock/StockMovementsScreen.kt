package com.example.selliaapp.ui.screens.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.StockMovementsUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockMovementsScreen(
    state: StockMovementsUiState,
    onBack: () -> Unit,
    onFilterChange: (String?) -> Unit
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
    }
    Scaffold(topBar = { BackTopAppBar(title = "Historial de stock", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var filterExpanded by remember { mutableStateOf(false) }
            val selectedLabel = state.selectedReason?.let { StockMovementReasons.humanReadable(it) }
            val options = state.allReasons
            ExposedDropdownMenuBox(
                expanded = filterExpanded,
                onExpandedChange = { filterExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedLabel ?: "Todos los motivos",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filtrar por motivo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                    modifier = Modifier.menuAnchor(
                        type = MenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )

                )
                ExposedDropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Todos") },
                        onClick = {
                            onFilterChange(null)
                            filterExpanded = false
                        }
                    )
                    options.forEach { option ->
                        val label = StockMovementReasons.humanReadable(option)
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onFilterChange(option)
                                filterExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.loading) {
                Text("Cargando movimientos…", style = MaterialTheme.typography.bodyMedium)
            } else if (state.movements.isEmpty()) {
                Text(
                    text = "No hay movimientos en el rango consultado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Agrupar por fecha para mostrar cabeceras de día
                val grouped = state.movements.groupBy { movement ->
                    formatter.format(movement.ts).substringBefore(" ")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (dateLabel, movementsForDay) ->
                        item(key = "header_$dateLabel") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }
                        }
                        items(movementsForDay, key = { it.id }) { movement ->
                            val isPositive = movement.delta >= 0
                            val deltaColor = if (isPositive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                            val deltaText = if (isPositive) "+${movement.delta}" else "${movement.delta}"

                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = deltaText,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = deltaColor,
                                        modifier = Modifier.alignByBaseline()
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = movement.productName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append(StockMovementReasons.humanReadable(movement.reason))
                                                movement.note?.takeIf { it.isNotBlank() }
                                                    ?.let { append(" · $it") }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatter.format(movement.ts).substringAfter(" "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
