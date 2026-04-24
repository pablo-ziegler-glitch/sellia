package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.ui.components.ImageUrlListEditor
import com.example.selliaapp.ui.components.NetGainChannel
import com.example.selliaapp.ui.components.ProductNetGainPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorDialog(
    initial: ProductEntity?,
    posnetPercent: Double = 0.0,
    operativosPercent: Double = 0.0,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        barcode: String,
        purchasePrice: Double?,
        listPrice: Double?,
        cashPrice: Double?,
        transferPrice: Double?,
        mlPrice: Double?,
        ml3cPrice: Double?,
        ml6cPrice: Double?,
        stock: Int,
        minStock: Int?,
        description: String?,
        imageUrls: List<String>,
        gainTargetPercent: Double?,
        providerName: String?,
        providerSku: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial?.name.orEmpty())) }
    var barcode by remember { mutableStateOf(TextFieldValue(initial?.barcode.orEmpty())) }
    var purchasePrice by remember { mutableStateOf(TextFieldValue(initial?.purchasePrice?.toString() ?: "")) }
    var gainTargetPercent by remember { mutableStateOf(TextFieldValue(initial?.gainTargetPercent?.toString() ?: "")) }
    var listPrice by remember { mutableStateOf(TextFieldValue(initial?.listPrice?.toString() ?: "")) }
    var effectiveTransferPrice by remember {
        mutableStateOf(TextFieldValue((initial?.cashPrice ?: initial?.transferPrice)?.toString() ?: ""))
    }
    var mlPrice by remember { mutableStateOf(TextFieldValue(initial?.mlPrice?.toString() ?: "")) }
    var ml3cPrice by remember { mutableStateOf(TextFieldValue(initial?.ml3cPrice?.toString() ?: "")) }
    var ml6cPrice by remember { mutableStateOf(TextFieldValue(initial?.ml6cPrice?.toString() ?: "")) }
    var stock by remember { mutableStateOf(TextFieldValue(initial?.quantity?.toString() ?: "")) }
    var minStock by remember { mutableStateOf(TextFieldValue(initial?.minStock?.toString() ?: "")) }
    var description by remember { mutableStateOf(TextFieldValue(initial?.description ?: "")) }
    var providerName by remember { mutableStateOf(TextFieldValue(initial?.providerName.orEmpty())) }
    var providerSku by remember { mutableStateOf(TextFieldValue(initial?.providerSku.orEmpty())) }

    // Per-field error states (null = no error)
    var nameError by remember { mutableStateOf<String?>(null) }
    var purchasePriceError by remember { mutableStateOf<String?>(null) }
    var stockError by remember { mutableStateOf<String?>(null) }
    var minStockError by remember { mutableStateOf<String?>(null) }
    var listPriceError by remember { mutableStateOf<String?>(null) }
    var effectiveTransferPriceError by remember { mutableStateOf<String?>(null) }
    var mlPriceError by remember { mutableStateOf<String?>(null) }
    var ml3cPriceError by remember { mutableStateOf<String?>(null) }
    var ml6cPriceError by remember { mutableStateOf<String?>(null) }
    var gainTargetError by remember { mutableStateOf<String?>(null) }

    val imageUrls: SnapshotStateList<String> = remember {
        mutableStateListOf<String>().apply {
            val initialUrls = if (initial?.imageUrls?.isNotEmpty() == true) {
                initial.imageUrls
            } else {
                initial?.imageUrl?.let { listOf(it) }.orEmpty()
            }
            addAll(initialUrls)
        }
    }

    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun validateAndSave() {
        var hasError = false
        if (name.text.isBlank()) {
            nameError = "El nombre es obligatorio."
            hasError = true
        }
        val purchase = purchasePrice.text.toDoubleOrNull()
        if (purchasePrice.text.isBlank() || purchase == null) {
            purchasePriceError = "Ingresá un costo de adquisición válido."
            hasError = true
        }
        if (stock.text.isNotBlank() && stock.text.toIntOrNull() == null) {
            stockError = "El stock debe ser un número entero."
            hasError = true
        }
        val minStockValue = minStock.text.toIntOrNull()
        if (minStock.text.isNotBlank() && minStockValue == null) {
            minStockError = "Ingresá un stock mínimo válido."
            hasError = true
        }
        if (hasError) return
        val normalizedImages = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
        onSave(
            name.text.trim(),
            barcode.text.trim(),
            purchase,
            listPrice.text.toDoubleOrNull(),
            effectiveTransferPrice.text.toDoubleOrNull(),
            effectiveTransferPrice.text.toDoubleOrNull(),
            mlPrice.text.toDoubleOrNull(),
            ml3cPrice.text.toDoubleOrNull(),
            ml6cPrice.text.toDoubleOrNull(),
            stock.text.toIntOrNull() ?: 0,
            minStockValue,
            description.text.trim().ifBlank { null },
            normalizedImages,
            gainTargetPercent.text.toDoubleOrNull(),
            providerName.text.trim().ifBlank { null },
            providerSku.text.trim().ifBlank { null }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (initial == null) "Nuevo producto" else "Editar producto",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Nombre*") },
                isError = nameError != null,
                supportingText = nameError?.let { msg -> { Text(msg) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && name.text.isBlank()) {
                            nameError = "El nombre es obligatorio."
                        }
                    }
            )

            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it; stockError = null },
                label = { Text("Stock*") },
                isError = stockError != null,
                supportingText = if (stockError != null) {
                    { Text(stockError!!) }
                } else {
                    { Text("Si se deja vacío, se guarda en 0.") }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && stock.text.isNotBlank() && stock.text.toIntOrNull() == null) {
                            stockError = "El stock debe ser un número entero."
                        }
                    }
            )

            OutlinedTextField(
                value = purchasePrice,
                onValueChange = { purchasePrice = it; purchasePriceError = null },
                label = { Text("Costo de adquisición*") },
                isError = purchasePriceError != null,
                supportingText = purchasePriceError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused) {
                            if (purchasePrice.text.isBlank() || purchasePrice.text.toDoubleOrNull() == null) {
                                purchasePriceError = "Ingresá un costo de adquisición válido."
                            }
                        }
                    }
            )

            OutlinedTextField(
                value = gainTargetPercent,
                onValueChange = { gainTargetPercent = it; gainTargetError = null },
                label = { Text("Ganancia individual (%)") },
                isError = gainTargetError != null,
                supportingText = if (gainTargetError != null) {
                    { Text(gainTargetError!!) }
                } else {
                    { Text("Vacío = usa la ganancia general de pricing.") }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && gainTargetPercent.text.isNotBlank() && gainTargetPercent.text.toDoubleOrNull() == null) {
                            gainTargetError = "Ingresá un porcentaje válido."
                        }
                    }
            )

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Código QR público") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = listPrice,
                onValueChange = { listPrice = it; listPriceError = null },
                label = { Text("Precio lista") },
                isError = listPriceError != null,
                supportingText = listPriceError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && listPrice.text.isNotBlank() && listPrice.text.toDoubleOrNull() == null) {
                            listPriceError = "Ingresá un precio válido."
                        }
                    }
            )

            OutlinedTextField(
                value = effectiveTransferPrice,
                onValueChange = { effectiveTransferPrice = it; effectiveTransferPriceError = null },
                label = { Text("Precio efectivo/transferencia") },
                isError = effectiveTransferPriceError != null,
                supportingText = effectiveTransferPriceError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && effectiveTransferPrice.text.isNotBlank() && effectiveTransferPrice.text.toDoubleOrNull() == null) {
                            effectiveTransferPriceError = "Ingresá un precio válido."
                        }
                    }
            )

            OutlinedTextField(
                value = mlPrice,
                onValueChange = { mlPrice = it; mlPriceError = null },
                label = { Text("Precio ML") },
                isError = mlPriceError != null,
                supportingText = mlPriceError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && mlPrice.text.isNotBlank() && mlPrice.text.toDoubleOrNull() == null) {
                            mlPriceError = "Ingresá un precio válido."
                        }
                    }
            )

            OutlinedTextField(
                value = ml3cPrice,
                onValueChange = { ml3cPrice = it; ml3cPriceError = null },
                label = { Text("Precio ML 3C") },
                isError = ml3cPriceError != null,
                supportingText = ml3cPriceError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && ml3cPrice.text.isNotBlank() && ml3cPrice.text.toDoubleOrNull() == null) {
                            ml3cPriceError = "Ingresá un precio válido."
                        }
                    }
            )

            OutlinedTextField(
                value = ml6cPrice,
                onValueChange = { ml6cPrice = it; ml6cPriceError = null },
                label = { Text("Precio ML 6C") },
                isError = ml6cPriceError != null,
                supportingText = ml6cPriceError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && ml6cPrice.text.isNotBlank() && ml6cPrice.text.toDoubleOrNull() == null) {
                            ml6cPriceError = "Ingresá un precio válido."
                        }
                    }
            )

            val purchaseVal = purchasePrice.text.toDoubleOrNull()
            if (purchaseVal != null && purchaseVal > 0) {
                ProductNetGainPanel(
                    purchasePrice = purchaseVal,
                    posnetPercent = posnetPercent,
                    operativosPercent = operativosPercent,
                    channels = listOfNotNull(
                        listPrice.text.toDoubleOrNull()?.let { NetGainChannel("Lista", it, applyPosnet = true) },
                        effectiveTransferPrice.text.toDoubleOrNull()?.let { NetGainChannel("Efectivo/Transferencia", it, applyPosnet = false) },
                        mlPrice.text.toDoubleOrNull()?.let { NetGainChannel("ML (0C)", it, applyPosnet = false) },
                        ml3cPrice.text.toDoubleOrNull()?.let { NetGainChannel("ML (3C)", it, applyPosnet = false) },
                        ml6cPrice.text.toDoubleOrNull()?.let { NetGainChannel("ML (6C)", it, applyPosnet = false) }
                    )
                )
            }

            OutlinedTextField(
                value = minStock,
                onValueChange = { minStock = it; minStockError = null },
                label = { Text("Stock mínimo") },
                isError = minStockError != null,
                supportingText = minStockError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && minStock.text.isNotBlank() && minStock.text.toIntOrNull() == null) {
                            minStockError = "Ingresá un stock mínimo válido."
                        }
                    }
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = providerName,
                onValueChange = { providerName = it },
                label = { Text("Proveedor") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Se crea automáticamente si no existe.") }
            )

            OutlinedTextField(
                value = providerSku,
                onValueChange = { providerSku = it },
                label = { Text("SKU del proveedor") },
                modifier = Modifier.fillMaxWidth()
            )

            ImageUrlListEditor(imageUrls = imageUrls)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = ::validateAndSave) { Text("Guardar") }
            }
        }
    }
}
