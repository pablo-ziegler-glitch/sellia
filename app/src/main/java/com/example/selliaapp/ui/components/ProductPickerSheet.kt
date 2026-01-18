package com.example.selliaapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.local.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerSheet(
    products: List<ProductEntity>,
    onPick: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    // Filtro local (null-safe para campos opcionales)
    val filtered = remember(products, query) {
        if (query.isBlank()) {
            products
        } else {
            val q = query.trim()
            products.filter { p ->
                // name es no nulo (String) en nuestro modelo; barcode/code son String?
                p.name.contains(q, ignoreCase = true) ||
                        (p.barcode?.contains(q, ignoreCase = true) == true) ||
                        (p.code?.contains(q, ignoreCase = true) == true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Elegir producto")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar por nombre o código") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
            )
            if (filtered.isEmpty()) {
                Text("No hay productos disponibles para agregar.")
            } else {
                LazyColumn {
                    items(filtered) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = {
                                Text(
                                    "Lista: ${p.listPrice ?: p.price ?: "-"} · " +
                                        "Efectivo: ${p.cashPrice ?: p.listPrice ?: p.price ?: "-"} · " +
                                        "Transferencia: ${p.transferPrice ?: p.listPrice ?: p.price ?: "-"} · " +
                                        "Stock: ${p.quantity} · ${p.barcode}"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
