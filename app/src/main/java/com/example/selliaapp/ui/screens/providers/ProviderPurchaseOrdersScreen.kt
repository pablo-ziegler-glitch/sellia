package com.example.selliaapp.ui.screens.providers

import android.icu.text.SimpleDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.repository.ProviderRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import java.util.Date
import java.util.Locale

@Composable
fun ProviderPurchaseOrdersScreen(
    providerRepo: ProviderRepository,
    invoiceRepo: ProviderInvoiceRepository,
    onBack: () -> Unit
) {
    val providers by providerRepo.observeAll().collectAsState(initial = emptyList())
    val pendingInvoices by invoiceRepo.observePending().collectAsState(initial = emptyList())

    val providerNames = remember(providers) {
        providers.associate { it.id to it.name }
    }
    val pendingPurchaseOrders = remember(pendingInvoices) {
        pendingInvoices.filter { row -> row.invoice.number.startsWith("PO-") }
    }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Scaffold(topBar = { BackTopAppBar(title = "Pedidos de compra", onBack = onBack) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pendientes de emitir/recibir",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Lista de reposiciones rápidas con proveedor, producto y cantidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pendingPurchaseOrders.isEmpty()) {
                Text(
                    text = "No hay pedidos de compra pendientes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pendingPurchaseOrders) { row ->
                    val invoice = row.invoice
                    val providerName = providerNames[invoice.providerId] ?: "Proveedor #${invoice.providerId}"
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Pedido ${invoice.number}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "$providerName • ${dateFormatter.format(Date(invoice.issueDateMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Total estimado: ${"%.2f".format(invoice.total)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            row.items.forEach { item ->
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        val codePart = item.code?.takeIf { it.isNotBlank() }?.let { "$it • " } ?: ""
                                        Text("$codePart${item.name} · ${item.quantity} u.")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
