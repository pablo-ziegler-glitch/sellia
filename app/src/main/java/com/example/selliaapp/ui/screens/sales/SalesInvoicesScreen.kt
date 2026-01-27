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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.sales.InvoiceSummary
import com.example.selliaapp.data.model.sales.SyncStatus
import com.example.selliaapp.viewmodel.sales.SalesInvoicesViewModel
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesInvoicesScreen(
    vm: SalesInvoicesViewModel,
    onOpenDetail: (Long) -> Unit,
    onBack: () -> Unit
) {
    val list by vm.invoices.collectAsState(initial = emptyList())
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Facturas de Venta") },
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
            if (list.isEmpty()) {
                item {
                    Text(
                        "No hay facturas emitidas.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(list, key = { it.id }) { inv ->
                    InvoiceCard(inv, currency, dateFmt, onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    inv: InvoiceSummary,
    currency: NumberFormat,
    dateFmt: DateTimeFormatter,
    onOpenDetail: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(inv.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = inv.number,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                SyncStatusBadge(status = inv.syncStatus)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cliente: ${inv.customerName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Fecha: ${inv.date.format(dateFmt)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(currency.format(inv.total), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(status: SyncStatus) {
    val (label, color) = when (status) {
        SyncStatus.SYNCED -> "Sincronizado" to MaterialTheme.colorScheme.primary
        SyncStatus.PENDING -> "Pendiente" to MaterialTheme.colorScheme.tertiary
        SyncStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
