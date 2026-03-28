package com.example.selliaapp.ui.screens.sales

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.ui.components.IllustratedEmptyState
import com.example.selliaapp.viewmodel.sales.InvoiceHistoryViewModel
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHistoryScreen(
    vm: InvoiceHistoryViewModel,
    onOpenDetail: (Long) -> Unit,
    onGoToSell: () -> Unit,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val expandedRows = remember { mutableStateListOf<Long>() }
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de ventas") },
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
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por cliente, producto o fecha") },
                singleLine = true
            )

            if (state.invoices.isEmpty()) {
                IllustratedEmptyState(
                    icon = Icons.Default.History,
                    title = "No hay ventas para mostrar",
                    description = "Registrá una venta desde Vender para ver el historial y compartir comprobantes.",
                    actionLabel = "Ir a Vender",
                    onAction = onGoToSell,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.invoices, key = { it.id }) { invoice ->
                        val detail = state.detailsById[invoice.id]
                        InvoiceHistoryCard(
                            isExpanded = expandedRows.contains(invoice.id),
                            invoiceNumber = invoice.number,
                            customerName = invoice.customerName,
                            invoiceDate = invoice.date.format(dateFmt),
                            total = currency.format(invoice.total),
                            onToggle = {
                                if (expandedRows.contains(invoice.id)) {
                                    expandedRows.remove(invoice.id)
                                } else {
                                    expandedRows.add(invoice.id)
                                    vm.loadDetail(invoice.id)
                                }
                            },
                            detail = detail,
                            onOpenDetail = { onOpenDetail(invoice.id) },
                            onShare = {
                                shareReceipt(
                                    context = context,
                                    title = "Comprobante ${invoice.number}",
                                    message = buildReceiptText(detail, invoice.number, currency)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceHistoryCard(
    isExpanded: Boolean,
    invoiceNumber: String,
    customerName: String,
    invoiceDate: String,
    total: String,
    onToggle: () -> Unit,
    detail: InvoiceDetail?,
    onOpenDetail: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = invoiceNumber,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(total, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text("Cliente: $customerName", style = MaterialTheme.typography.bodyMedium)
            Text("Fecha: $invoiceDate", style = MaterialTheme.typography.bodySmall)

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    if (detail == null) {
                        Text("Cargando detalle...", style = MaterialTheme.typography.bodySmall)
                    } else {
                        detail.items.take(4).forEach { item ->
                            Text(
                                "• ${item.name} x${item.quantity} (${detail.paymentMethod})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onOpenDetail) { Text("Ver detalle") }
                        Row {
                            TextButton(onClick = onShare) { Text("Reenviar") }
                            TextButton(onClick = onShare) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Text("Compartir")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildReceiptText(
    detail: InvoiceDetail?,
    invoiceNumber: String,
    currency: NumberFormat
): String {
    if (detail == null) return "Comprobante $invoiceNumber"
    val lines = buildString {
        appendLine("Comprobante ${detail.number}")
        appendLine("Cliente: ${detail.customerName}")
        appendLine("Fecha: ${detail.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
        appendLine("Total: ${currency.format(detail.total)}")
        appendLine()
        appendLine("Detalle:")
        detail.items.forEach { item ->
            appendLine("- ${item.name} x${item.quantity} = ${currency.format(item.lineTotal)}")
        }
    }
    return lines.trim()
}

private fun shareReceipt(
    context: android.content.Context,
    title: String,
    message: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir comprobante"))
}
