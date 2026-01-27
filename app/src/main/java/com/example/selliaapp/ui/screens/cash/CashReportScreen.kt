package com.example.selliaapp.ui.screens.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.local.entity.CashMovementType
import com.example.selliaapp.viewmodel.cash.CashViewModel
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashReportScreen(
    vm: CashViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val summary = state.summary
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte de caja") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (summary == null) {
                Text(
                    "No hay una caja abierta para reportar.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            val session = summary.session
            val openedAt = session.openedAt.atZone(ZoneId.systemDefault()).format(dateFormatter)
            Text("Apertura: $openedAt", style = MaterialTheme.typography.titleMedium)
            session.openedBy?.takeIf { it.isNotBlank() }?.let { openedBy ->
                Text("Abierta por: $openedBy", style = MaterialTheme.typography.bodySmall)
            }
            session.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text("Nota apertura: $note", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Text("Resumen monetario", style = MaterialTheme.typography.titleSmall)
            Text("Monto inicial: ${currency.format(session.openingAmount)}")
            Text("Ventas en efectivo: ${currency.format(summary.cashSalesTotal)}")

            val groupedMovements = summary.movements
                .groupBy { it.type }
                .mapValues { (_, list) -> list.sumOf { it.amount } }

            if (groupedMovements.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Movimientos", style = MaterialTheme.typography.titleSmall)
                groupedMovements.forEach { (type, total) ->
                    val label = when (type) {
                        CashMovementType.INCOME -> "Ingresos"
                        CashMovementType.EXPENSE -> "Egresos"
                        CashMovementType.ADJUSTMENT -> "Ajustes"
                        CashMovementType.SALE_CASH -> "Ventas efectivo"
                        else -> type
                    }
                    Text("$label: ${currency.format(total)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Saldo teórico actual: ${currency.format(summary.expectedAmount)}",
                style = MaterialTheme.typography.titleMedium
            )

            if (summary.audits.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Arqueos registrados", style = MaterialTheme.typography.titleSmall)
                summary.audits.forEach { audit ->
                    val auditTime = audit.createdAt.atZone(ZoneId.systemDefault()).format(dateFormatter)
                    Text(
                        "• $auditTime: contado ${currency.format(audit.countedAmount)} " +
                            "(${currency.format(audit.difference)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
