package com.example.selliaapp.ui.screens.providers


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderInvoiceDetailScreen(
    invoiceId: Int,
    repo: ProviderInvoiceRepository,
    onBack: () -> Unit
) {
    val row by repo.observeDetail(invoiceId).collectAsState(initial = null)
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Scaffold(topBar = { BackTopAppBar(title = "Detalle de Factura", onBack = onBack) }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            row?.let { data ->
                val inv = data.invoice
                Text("Factura: ${inv.number}")
                Text("Fecha: ${sdf.format(Date(inv.issueDateMillis))}")
                Text("Estado: ${inv.status}")
                if (inv.paymentRef != null) Text("Pago ref: ${inv.paymentRef}")
                if (inv.paymentAmount != null) Text("Monto pagado: ${"%.2f".format(inv.paymentAmount)}")

                HorizontalDivider()

                Text("Items")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(data.items) { it ->
                        ListItem(
                            headlineContent = {
                                Text("${it.code ?: "-"}  ${it.name}")
                            },
                            supportingContent = {
                                Text("Cant: ${it.quantity}  •  P.Unit: ${"%.2f".format(it.priceUnit)}  •  %IVA: ${it.vatPercent}  •  IVA: ${"%.2f".format(it.vatAmount)}  •  Total: ${"%.2f".format(it.total)}")
                            }
                        )
                    }
                }
            } ?: Text("Cargando...")
        }
    }
}
