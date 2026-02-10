package com.example.selliaapp.ui.screens.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ProductPriceAuditUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProductPriceAuditScreen(
    state: ProductPriceAuditUiState,
    onBack: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

    Scaffold(topBar = { BackTopAppBar(title = "Auditoría de precios", onBack = onBack) }) { padding ->
        if (state.entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("No hay cambios de precios auditados todavía.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.entries) { entry ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(entry.productName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Lista: ${entry.oldListPrice ?: "-"} → ${entry.newListPrice ?: "-"} | " +
                                "Efectivo: ${entry.oldCashPrice ?: "-"} → ${entry.newCashPrice ?: "-"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Transferencia: ${entry.oldTransferPrice ?: "-"} → ${entry.newTransferPrice ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Motivo: ${entry.reason} · Origen: ${entry.source} · Por: ${entry.changedBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatter.format(entry.changedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
