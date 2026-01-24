package com.example.selliaapp.ui.screens.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.viewmodel.cash.CashViewModel
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(
    vm: CashViewModel,
    onOpen: () -> Unit,
    onAudit: () -> Unit,
    onMovements: () -> Unit,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm") }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Caja", style = MaterialTheme.typography.headlineSmall)

        if (!state.hasOpenSession) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Caja cerrada", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Abrí la caja para registrar efectivo y movimientos.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir caja")
                    }
                }
            }
            return@Column
        }

        val summary = state.summary
        val session = summary?.session
        val openedAt = session?.openedAt?.atZone(ZoneId.systemDefault())?.format(dateFormatter) ?: "-"

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Caja abierta", style = MaterialTheme.typography.titleMedium)
                Text("Apertura: $openedAt", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Saldo teórico: ${currency.format(summary?.expectedAmount ?: 0.0)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Ventas en efectivo: ${currency.format(summary?.cashSalesTotal ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onAudit, modifier = Modifier.weight(1f)) {
                Text("Arqueo")
            }
            OutlinedButton(onClick = onMovements, modifier = Modifier.weight(1f)) {
                Text("Movimiento")
            }
            Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Cerrar caja")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Últimos movimientos", style = MaterialTheme.typography.titleMedium)
                if (summary?.movements.isNullOrEmpty()) {
                    Text(
                        "Sin movimientos registrados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    summary?.movements?.take(5)?.forEach { movement ->
                        Text(
                            "• ${movement.type}: ${currency.format(movement.amount)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
