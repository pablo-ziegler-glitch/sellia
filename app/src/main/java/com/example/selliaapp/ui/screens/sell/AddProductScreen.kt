package com.example.selliaapp.ui.screens.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.ImageUrlListEditor
import com.example.selliaapp.ui.viewmodel.OffLookupViewModel
import com.example.selliaapp.ui.viewmodel.OffLookupViewModel.UiState
import com.example.selliaapp.viewmodel.PrefillData
import com.example.selliaapp.viewmodel.ProductViewModel

/**
 * AddProductScreen con:
 * - Autodetección de Nombre/Marca/Imagen por Open Food Facts al recibir prefillBarcode o prefill.
 * - Botón Guardar visible (alta/edición).
 * - Corrección del manejo de barcode como estado mutable.
 */
@Composable
fun AddProductScreen(
    viewModel: ProductViewModel,
    prefillBarcode: String? = null,     // barcode sugerido (viene de escaneo sin match local)
    prefill: PrefillData? = null,       // datos prellenados (si venís de OFF o de otra pantalla)
    editId: Int? = null,
    prefillName: String? = null,     // barcode sugerido (viene de escaneo sin match local) // si no es null, estás editando
    onSaved: () -> Unit,
    navController: NavController,
    offVm: OffLookupViewModel = hiltViewModel() // VM para hablar con OFF
) {

    val state by viewModel.autoFillState.collectAsState()
    val imageUploadState by viewModel.imageUploadState.collectAsState()
    val context = LocalContext.current

    // --- Estado de campos (con prefill si existe) ---
    var name by remember { mutableStateOf(prefill?.name ?: prefillName ?: "") }
    var code by remember { mutableStateOf("") } // SKU interno
    var brand by remember { mutableStateOf(prefill?.brand ?: "") }
    val imageUrls: SnapshotStateList<String> = remember {
        mutableStateListOf<String>().apply {
            val fromVm = viewModel.imageUrls?.filter { it.isNotBlank() }.orEmpty()
            val legacyVm = viewModel.imageUrl?.takeIf { it.isNotBlank() }
            val prefillUrl = prefill?.imageUrl?.takeIf { it.isNotBlank() }
            when {
                fromVm.isNotEmpty() -> addAll(fromVm)
                legacyVm != null -> add(legacyVm)
                prefillUrl != null -> add(prefillUrl)
            }
        }
    }
    val pendingImageUris = remember { mutableStateListOf<android.net.Uri>() }

    // Corregimos el bug: barcode debe ser VAR (mutable) y recordado
    var barcode by remember { mutableStateOf(prefill?.barcode ?: prefillBarcode ?: "") }

    // Precios y stock (tu bloque E4, intacto)
    var purchasePriceText by remember { mutableStateOf("") }
    var listPriceText by remember { mutableStateOf("") }
    var cashPriceText by remember { mutableStateOf("") }
    var transferPriceText by remember { mutableStateOf("") }
    var mlPriceText by remember { mutableStateOf("") }
    var ml3cPriceText by remember { mutableStateOf("") }
    var ml6cPriceText by remember { mutableStateOf("") }
    var stockText by remember { mutableStateOf("0") }

    // Extras
    var description by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("") }
    var selectedProviderName by remember { mutableStateOf("") }
    var providerSku by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var minStockText by remember { mutableStateOf("") }

    // Listas
    val categories: List<String> =
        viewModel.getAllCategoryNames().collectAsState(initial = emptyList()).value
    val providers: List<String> =
        viewModel.getAllProviderNames().collectAsState(initial = emptyList()).value


    // Si editás, precargamos desde DB
    LaunchedEffect(editId) {
        if (editId != null) {
            val p = viewModel.getProductById(editId)
            if (p != null) {
                name = p.name
                code = p.code.orEmpty()
                barcode = p.barcode.orEmpty()

                listPriceText = p.listPrice?.toString() ?: ""
                cashPriceText = p.cashPrice?.toString() ?: ""
                transferPriceText = p.transferPrice?.toString() ?: ""
                purchasePriceText = p.purchasePrice?.toString() ?: ""
                mlPriceText = p.mlPrice?.toString() ?: ""
                ml3cPriceText = p.ml3cPrice?.toString() ?: ""
                ml6cPriceText = p.ml6cPrice?.toString() ?: ""
                stockText = p.quantity.toString()
                description = p.description.orEmpty()
                val loadedImageUrls = if (p.imageUrls.isNotEmpty()) {
                    p.imageUrls
                } else {
                    p.imageUrl?.let { listOf(it) }.orEmpty()
                }
                imageUrls.clear()
                imageUrls.addAll(loadedImageUrls)
                pendingImageUris.clear()

                selectedCategoryName = p.category.orEmpty()
                // Si tu modelo aún no tiene providerName, quedará vacío
                selectedProviderName = p.providerName.orEmpty()
                providerSku = p.providerSku.orEmpty()

                minStockText = p.minStock?.toString() ?: ""
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (editId != null) {
                val contentType = context.contentResolver.getType(uri)
                viewModel.uploadProductImage(
                    productId = editId,
                    localUri = uri,
                    contentType = contentType
                ) { result ->
                    result.onSuccess { imageUrls.add(it) }
                }
            } else {
                pendingImageUris.add(uri)
            }
        }
    }

    // --- Autodetección OFF: si tenemos barcode y faltan name/brand, buscamos ---
    val offState by offVm.state.collectAsState()

    LaunchedEffect(barcode) {
        val needsLookup = barcode.isNotBlank() && (name.isBlank() || brand.isBlank())
        // No dispares si venís editando (ya tenés datos locales)
        if (needsLookup && editId == null) {
            offVm.fetch(barcode)
        }
    }

    // Reaccionamos a resultado OFF: solo completar si usuario NO escribió aún
    LaunchedEffect(offState) {
        when (val s = offState) {
            is UiState.Success -> {
                val d = s.data
                if (name.isBlank()) name = d.name
                if (brand.isBlank()) brand = d.brand
                if (imageUrls.isEmpty() && !d.imageUrl.isNullOrBlank()) {
                    imageUrls.add(d.imageUrl)
                }
                // Mantener barcode detectado
                if (barcode.isBlank()) barcode = d.barcode
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            val title = if (editId == null) "Agregar producto" else "Editar producto"
            BackTopAppBar(title = title, onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(padding)
                .padding(16.dp),
         ) {
        // --- Estado de OFF (loading/error/imagen) ---
        when (offState) {
            UiState.Loading -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Buscando datos en Open Food Facts…")
                }
            }
            is UiState.Error -> {
                val msg = (offState as UiState.Error).message
                InfoMessage(text = msg)
            }
            else -> Unit
        }

        // --- Campos principales ---
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Código interno (SKU)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = barcode,
            onValueChange = { barcode = it }, // permito editar por si OFF trae otro formato
            label = { Text("Código de barras") },
            modifier = Modifier.fillMaxWidth()
        )

        // Botón manual para reintentar OFF (opcional)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.autocompleteFromOff(barcode) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.loading) "Consultando OFF..." else "Autocompletar con OFF") }
            Spacer(Modifier.height(16.dp))

        }


        // Marca
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            label = { Text("Marca") },
            modifier = Modifier.fillMaxWidth()
        )

        // --- Precios (E4) ---
        OutlinedTextField(
            value = purchasePriceText,
            onValueChange = { purchasePriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
            label = { Text("Costo de adquisición") },
            modifier = Modifier.fillMaxWidth()
        )


        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = listPriceText,
                onValueChange = {
                    listPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                },
                label = { Text("Precio de lista") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = cashPriceText,
                onValueChange = {
                    cashPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                },
                label = { Text("Precio en efectivo") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = transferPriceText,
                onValueChange = {
                    transferPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                },
                label = { Text("Precio transferencia") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = mlPriceText,
                onValueChange = {
                    mlPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                },
                label = { Text("Precio ML") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = ml3cPriceText,
                onValueChange = {
                    ml3cPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                },
                label = { Text("Precio ML 3C") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ml6cPriceText,
                onValueChange = {
                    ml6cPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                },
                label = { Text("Precio ML 6C") },
                modifier = Modifier.weight(1f)
            )
        }

        // Stock
        OutlinedTextField(
            value = stockText,
            onValueChange = { stockText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Stock") },
            modifier = Modifier.fillMaxWidth()
        )

        // Descripción / Imagen (manual)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Subir imagen")
                }
                if (imageUploadState.uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Subiendo...")
                }
            }
            if (!imageUploadState.message.isNullOrBlank()) {
                Text("Error: ${imageUploadState.message}")
            }
            if (pendingImageUris.isNotEmpty()) {
                Text("Imágenes pendientes (se suben al guardar)")
                pendingImageUris.forEachIndexed { index, uri ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Imagen pendiente ${index + 1}",
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                        )
                        IconButton(onClick = { pendingImageUris.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar pendiente")
                        }
                    }
                }
            }
        }

        ImageUrlListEditor(imageUrls = imageUrls)

        // --- Categoría ---
        Column {
            OutlinedTextField(
                value = selectedCategoryName,
                onValueChange = { selectedCategoryName = it },
                label = { Text("Categoría") },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.clickable { categoryMenuExpanded = !categoryMenuExpanded }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = { categoryMenuExpanded = false }
            ) {
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            selectedCategoryName = cat
                            categoryMenuExpanded = false
                        }
                    )
                }
            }
        }

        // --- Proveedor ---
        Column {
            OutlinedTextField(
                value = selectedProviderName,
                onValueChange = { selectedProviderName = it },
                label = { Text("Proveedor") },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.clickable { providerMenuExpanded = !providerMenuExpanded }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = providerMenuExpanded,
                onDismissRequest = { providerMenuExpanded = false }
            ) {
                providers.forEach { prov ->
                    DropdownMenuItem(
                        text = { Text(prov) },
                        onClick = {
                            selectedProviderName = prov
                            providerMenuExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = providerSku,
            onValueChange = { providerSku = it },
            label = { Text("SKU proveedor") },
            modifier = Modifier.fillMaxWidth()
        )

        // Stock mínimo
        OutlinedTextField(
            value = minStockText,
            onValueChange = { minStockText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Stock mínimo") },
            modifier = Modifier.fillMaxWidth()
        )

        // --- Acciones ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    // Parseo robusto (E4)
                    val purchase = purchasePriceText.replace(',', '.').toDoubleOrNull()
                    val listPrice = listPriceText.replace(',', '.').toDoubleOrNull()
                    val cashPrice = cashPriceText.replace(',', '.').toDoubleOrNull()
                    val transferPrice = transferPriceText.replace(',', '.').toDoubleOrNull()
                    val transferNetPrice = null
                    val mlPrice = mlPriceText.replace(',', '.').toDoubleOrNull()
                    val ml3cPrice = ml3cPriceText.replace(',', '.').toDoubleOrNull()
                    val ml6cPrice = ml6cPriceText.replace(',', '.').toDoubleOrNull()
                    val qty = stockText.toIntOrNull() ?: 0
                    val minStock = minStockText.toIntOrNull()

                    val normalizedImages = imageUrls.map { it.trim() }.filter { it.isNotBlank() }

                    if (editId == null) {
                        viewModel.addProduct(
                            name = name,
                            barcode = barcode.ifBlank { null },
                            purchasePrice = purchase,
                            legacyPrice = null,
                            listPrice = listPrice,
                            cashPrice = cashPrice,
                            transferPrice = transferPrice,
                            transferNetPrice = transferNetPrice,
                            mlPrice = mlPrice,
                            ml3cPrice = ml3cPrice,
                            ml6cPrice = ml6cPrice,
                            stock = qty,
                            code = code.ifBlank { null },
                            description = description.ifBlank { null },
                            imageUrls = normalizedImages,
                            categoryName = selectedCategoryName.ifBlank { null },
                            providerName = selectedProviderName.ifBlank { null },
                            providerSku = providerSku.ifBlank { null },
                            minStock = minStock,
                            pendingImageUris = pendingImageUris.toList()
                        ) { result ->
                            if (result.isSuccess) {
                                onSaved()
                            }
                        )
                    } else {
                        viewModel.updateProduct(
                            id = editId,
                            name = name,
                            barcode = barcode.ifBlank { null },
                            purchasePrice = purchase,
                            legacyPrice = null,
                            listPrice = listPrice,
                            cashPrice = cashPrice,
                            transferPrice = transferPrice,
                            transferNetPrice = transferNetPrice,
                            mlPrice = mlPrice,
                            ml3cPrice = ml3cPrice,
                            ml6cPrice = ml6cPrice,
                            stock = qty,
                            code = code.ifBlank { null },
                            description = description.ifBlank { null },
                            imageUrls = normalizedImages,
                            categoryName = selectedCategoryName.ifBlank { null },
                            providerName = selectedProviderName.ifBlank { null },
                            providerSku = providerSku.ifBlank { null },
                            minStock = minStock
                        )
                        onSaved()
                    }
                }) {
                    Text(if (editId == null) "Guardar" else "Actualizar")
                }

                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Cancelar")
                }
            }
        }
    }
}

/**
 * Mensaje simple para estados de info/error.
 */
@Composable
private fun InfoMessage(text: String) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            TextButton(onClick = { }) { Text("OK") }
        },
        title = { Text("Información") },
        text = { Text(text) }
    )
}
