package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.example.selliaapp.data.local.entity.ProductEntity

@Composable
fun ProductEditorDialog(
    initial: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        barcode: String,
        price: Double,
        listPrice: Double?,
        cashPrice: Double?,
        transferPrice: Double?,
        mlPrice: Double?,
        ml3cPrice: Double?,
        ml6cPrice: Double?,
        stock: Int,
        description: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial?.name.orEmpty())) }
    var barcode by remember { mutableStateOf(TextFieldValue(initial?.barcode.orEmpty())) }
    var price by remember { mutableStateOf(TextFieldValue(initial?.price?.toString() ?: "")) }
    var listPrice by remember { mutableStateOf(TextFieldValue(initial?.listPrice?.toString() ?: "")) }
    var cashPrice by remember { mutableStateOf(TextFieldValue(initial?.cashPrice?.toString() ?: "")) }
    var transferPrice by remember { mutableStateOf(TextFieldValue(initial?.transferPrice?.toString() ?: "")) }
    var mlPrice by remember { mutableStateOf(TextFieldValue(initial?.mlPrice?.toString() ?: "")) }
    var ml3cPrice by remember { mutableStateOf(TextFieldValue(initial?.ml3cPrice?.toString() ?: "")) }
    var ml6cPrice by remember { mutableStateOf(TextFieldValue(initial?.ml6cPrice?.toString() ?: "")) }
    var stock by remember { mutableStateOf(TextFieldValue(initial?.quantity?.toString() ?: "")) }
    var description by remember { mutableStateOf(TextFieldValue(initial?.description ?: "")) }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo producto" else "Editar producto") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Código") })
                OutlinedTextField(price, { price = it }, label = { Text("Precio") })
                OutlinedTextField(listPrice, { listPrice = it }, label = { Text("Precio lista") })
                OutlinedTextField(cashPrice, { cashPrice = it }, label = { Text("Precio efectivo") })
                OutlinedTextField(transferPrice, { transferPrice = it }, label = { Text("Precio transferencia") })
                OutlinedTextField(mlPrice, { mlPrice = it }, label = { Text("Precio ML") })
                OutlinedTextField(ml3cPrice, { ml3cPrice = it }, label = { Text("Precio ML 3C") })
                OutlinedTextField(ml6cPrice, { ml6cPrice = it }, label = { Text("Precio ML 6C") })
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") })
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.text.toDoubleOrNull() ?: 0.0
                val list = listPrice.text.toDoubleOrNull()
                val cash = cashPrice.text.toDoubleOrNull()
                val transfer = transferPrice.text.toDoubleOrNull()
                val ml = mlPrice.text.toDoubleOrNull()
                val ml3c = ml3cPrice.text.toDoubleOrNull()
                val ml6c = ml6cPrice.text.toDoubleOrNull()
                val s = stock.text.toIntOrNull() ?: 0
                onSave(
                    name.text.trim(),
                    barcode.text.trim(),
                    p,
                    list,
                    cash,
                    transfer,
                    ml,
                    ml3c,
                    ml6c,
                    s,
                    description.text.trim().ifBlank { null }
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
