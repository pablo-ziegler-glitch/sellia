package com.example.selliaapp.ui.screens.clients


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ClientPurchasesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ClientPurchasesScreen(
    vm: ClientPurchasesViewModel,
    onBack: () -> Unit
) {
    val items by vm.results.collectAsState()

    Scaffold(topBar = { BackTopAppBar(title = "Compras de clientes", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = { vm.setQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por nombre, teléfono, email o apodo") },
                placeholder = { Text("Ej: Juan / 11223344 / juan@... / Juancito") }
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { row -> InvoiceRow(row) }
            }
        }
    }
}

@Composable
private fun InvoiceRow(row: InvoiceWithItems) {
    val fmtDate = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    val dateStr = Instant.ofEpochMilli(row.invoice.dateMillis)
        .atZone(ZoneId.systemDefault()).format(fmtDate)

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Factura #${row.invoice.id} - ${row.invoice.customerName.orEmpty()}")
        Text("Fecha: $dateStr  -  Total: ${"%.2f".format(row.invoice.total)}")
        if (row.items.isNotEmpty()) {
            Text("Items:")
            row.items.forEach {
                Text(" • ${it.productName} x${it.quantity}  = ${"%.2f".format(it.lineTotal)}")
            }
        }
    }
}
