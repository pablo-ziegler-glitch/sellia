package com.example.selliaapp.ui.screens.providers


import android.icu.text.SimpleDateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.local.entity.ProviderEntity
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.repository.ProviderRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderInvoicesScreen(
    providerRepo: ProviderRepository,
    invoiceRepo: ProviderInvoiceRepository,
    onOpenDetail: (invoiceId: Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val providers by providerRepo.observeAll().collectAsState(initial = emptyList())
    var selected: ProviderEntity? by remember { mutableStateOf(null) }

    // Lista reactiva de facturas del proveedor elegido
    val invoices by remember(selected) {
        derivedStateOf { selected?.id }
    }.let { providerIdState ->
        if (providerIdState.value == null) {
            mutableStateOf(emptyList())
        } else {
            val id = providerIdState.value!!
            invoiceRepo.observeByProvider(id).collectAsState(initial = emptyList())
        }
    }

    Scaffold(topBar = { BackTopAppBar(title = "Facturas por Proveedor", onBack = onBack) }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Selector de proveedor
            Text("Proveedor")
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Seleccionar proveedor") },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    providers.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = {
                            selected = p; expanded = false
                        })
                    }
                }
            }

            val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(invoices) { row ->
                    val inv = row.invoice
                    // ✅ Opción universal: Card + Modifier.clickable
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(inv.id) } // Navega al detalle
                    ) {
                        ListItem(
                            headlineContent = {
                                Text("Factura ${inv.number}  •  ${sdf.format(Date(inv.issueDateMillis))}")
                            },
                            supportingContent = {
                                Text("Total: ${"%.2f".format(inv.total)}  •  Estado: ${inv.status}")
                            }
                        )
                    }
                }
            }
        }
    }
}
