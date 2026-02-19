package com.example.selliaapp.ui.screens.sell

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.ImageUrlListEditor
import com.example.selliaapp.ui.components.MultiSelectChipPicker
import com.example.selliaapp.ui.viewmodel.OffLookupViewModel
import com.example.selliaapp.ui.viewmodel.OffLookupViewModel.UiState
import com.example.selliaapp.viewmodel.PrefillData
import com.example.selliaapp.viewmodel.ProductViewModel

private const val INDUMENTARIA_PARENT_CATEGORY = "Indumentaria"

private val INDUMENTARIA_SIZE_OPTIONS = listOf(
    "3M", "6M", "9M", "12M", "18M", "24M", "36",
    "0", "2", "4", "6", "8", "10", "12", "14", "16", "Unico"
)

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
    prefillName: String? = null,        // si no es null, estás editando / prellenando nombre
    canManagePublication: Boolean,
    onSaved: () -> Unit,
    navController: NavController,
    offVm: OffLookupViewModel = hiltViewModel()
) {
    val imageUploadState by viewModel.imageUploadState.collectAsState()
    val context = LocalContext.current

    // --- Estado de campos (con prefill si existe) ---
    var name by remember { mutableStateOf(prefill?.name ?: prefillName.orEmpty()) }
    var code by remember { mutableStateOf("") } // SKU interno
    var brand by remember { mutableStateOf(prefill?.brand.orEmpty()) }

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

    val pendingImageUris = remember { mutableStateListOf<Uri>() }

    // Barcode mutable
    var barcode by remember { mutableStateOf(prefill?.barcode ?: prefillBarcode.orEmpty()) }

    // Precios y stock
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
    var selectedParentCategoryName by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var selectedSizes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedProviderName by remember { mutableStateOf("") }
    var providerSku by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var minStockText by remember { mutableStateOf("") }
    var isPublished by remember { mutableStateOf(false) }

    // Dialog de error/info
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showCloudCatalogDialog by remember { mutableStateOf(false) }
    val selectedCloudImageUrls = remember { mutableStateListOf<String>() }
    val cloudCatalogState by viewModel.cloudCatalogState.collectAsState()

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

                selectedParentCategoryName = p.parentCategory.orEmpty()
                selectedCategoryName = p.category.orEmpty()
                color = p.color.orEmpty()
                selectedSizes = p.sizes
                selectedProviderName = p.providerName.orEmpty()
                providerSku = p.providerSku.orEmpty()
                minStockText = p.minStock?.toString() ?: ""
                isPublished = p.publicStatus == "published"
            }
        }
    }

    fun handlePickedUri(uri: Uri) {
        if (editId != null) {
            val contentType = context.contentResolver.getType(uri)
            viewModel.uploadProductImage(
                productId = editId,
                localUri = uri,
                contentType = contentType
            ) { result ->
                result.onSuccess { imageUrls.add(it) }
                result.onFailure { infoMessage = it.message ?: "Error subiendo imagen" }
            }
        } else {
            pendingImageUris.add(uri)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris
            .distinct()
            .forEach(::handlePickedUri)
    }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            persistPreviewBitmap(context = context, bitmap = bitmap)
                ?.let(::handlePickedUri)
                ?: run { infoMessage = "No se pudo guardar la foto tomada." }
        }
    }

    // --- OFF lookup ---
    val offState by offVm.state.collectAsState()

    LaunchedEffect(barcode) {
        val needsLookup = barcode.isNotBlank() && (name.isBlank() || brand.isBlank())
        if (needsLookup && editId == null) {
            offVm.fetch(barcode)
        }
    }

    LaunchedEffect(offState) {
        when (val s = offState) {
            is UiState.Success -> {
                val d = s.data
                if (name.isBlank()) name = d.name
                if (brand.isBlank()) brand = d.brand
                if (imageUrls.isEmpty() && !d.imageUrl.isNullOrBlank()) imageUrls.add(d.imageUrl)
                if (barcode.isBlank()) barcode = d.barcode
            }
            is UiState.Error -> infoMessage = s.message
            else -> Unit
        }
    }

    if (showCloudCatalogDialog) {
        AlertDialog(
            onDismissRequest = { showCloudCatalogDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        imageUrls.addAll(selectedCloudImageUrls.filterNot { imageUrls.contains(it) })
                        selectedCloudImageUrls.clear()
                        showCloudCatalogDialog = false
                    },
                    enabled = selectedCloudImageUrls.isNotEmpty()
                ) {
                    Text("Agregar seleccionadas")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedCloudImageUrls.clear()
                    showCloudCatalogDialog = false
                }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Biblioteca cloud") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        cloudCatalogState.loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("Cargando imágenes del catálogo...")
                        }
                        cloudCatalogState.images.isEmpty() -> {
                            Text(cloudCatalogState.message ?: "No hay imágenes disponibles.")
                        }
                        else -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cloudCatalogState.images, key = { it.fullPath }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (selectedCloudImageUrls.contains(item.downloadUrl)) {
                                                    selectedCloudImageUrls.remove(item.downloadUrl)
                                                } else {
                                                    selectedCloudImageUrls.add(item.downloadUrl)
                                                }
                                            },
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedCloudImageUrls.contains(item.downloadUrl),
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (!selectedCloudImageUrls.contains(item.downloadUrl)) {
                                                        selectedCloudImageUrls.add(item.downloadUrl)
                                                    }
                                                } else {
                                                    selectedCloudImageUrls.remove(item.downloadUrl)
                                                }
                                            }
                                        )
                                        AsyncImage(
                                            model = item.downloadUrl,
                                            contentDescription = "Imagen cloud",
                                            modifier = Modifier.size(72.dp)
                                        )
                                        Text(
                                            text = item.fullPath.substringAfterLast('/'),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (infoMessage != null) {
        InfoMessage(
            text = infoMessage.orEmpty(),
            onDismiss = { infoMessage = null }
        )
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
                .padding(16.dp)
        ) {
            // --- Estado OFF loading ---
            if (offState == UiState.Loading) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("Buscando datos en Open Food Facts…")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre*") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = stockText,
                onValueChange = { stockText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Stock*") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Si se deja vacío, se guarda en 0.") }
            )

            OutlinedTextField(
                value = purchasePriceText,
                onValueChange = { purchasePriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text("Costo de adquisición*") },
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
                onValueChange = { barcode = it },
                label = { Text("Código QR público") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Marca") },
                modifier = Modifier.fillMaxWidth()
            )

            // --- Precios ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = listPriceText,
                    onValueChange = { listPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio de lista") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cashPriceText,
                    onValueChange = { cashPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio en efectivo") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = transferPriceText,
                    onValueChange = { transferPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio transferencia") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mlPriceText,
                    onValueChange = { mlPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio ML") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = ml3cPriceText,
                    onValueChange = { ml3cPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio ML 3C") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ml6cPriceText,
                    onValueChange = { ml6cPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio ML 6C") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )

            // Imágenes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { takePicturePreviewLauncher.launch(null) }) {
                        Text("Sacar foto")
                    }
                    Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Text("Subir desde celular (múltiple)")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        selectedCloudImageUrls.clear()
                        showCloudCatalogDialog = true
                        viewModel.loadPublicCatalogImages()
                    }) {
                        Text("Elegir desde nube")
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

            // Categoría
            Column {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = { selectedCategoryName = it },
                    label = { Text("Subcategoría") },
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

            // Categoría padre
            OutlinedTextField(
                value = selectedParentCategoryName,
                onValueChange = { selectedParentCategoryName = it },
                label = { Text("Categoría padre") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Si es Indumentaria se habilitan color y talle/s.") }
            )

            if (selectedParentCategoryName.trim().equals(INDUMENTARIA_PARENT_CATEGORY, ignoreCase = true)) {
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color") },
                    modifier = Modifier.fillMaxWidth()
                )

                MultiSelectChipPicker(
                    title = "Talle/s",
                    options = INDUMENTARIA_SIZE_OPTIONS,
                    selectedOptions = selectedSizes,
                    onSelectionChange = { selectedSizes = it },
                    allowCustomAdd = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Proveedor
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

            OutlinedTextField(
                value = minStockText,
                onValueChange = { minStockText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Stock mínimo") },
                modifier = Modifier.fillMaxWidth()
            )

            if (canManagePublication) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Publicado en catálogo público")
                    Switch(
                        checked = isPublished,
                        onCheckedChange = { isPublished = it }
                    )
                }
            }

            // --- Acciones (FIX: un solo Button, llaves ok) ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (purchasePriceText.isBlank()) {
                            infoMessage = "El costo de adquisición es obligatorio."
                            return@Button
                        }
                        val purchase = purchasePriceText.replace(',', '.').toDoubleOrNull()
                        if (purchase == null) {
                            infoMessage = "Ingresá un costo de adquisición válido."
                            return@Button
                        }
                        val listPrice = listPriceText.replace(',', '.').toDoubleOrNull()
                        val cashPrice = cashPriceText.replace(',', '.').toDoubleOrNull()
                        val transferPrice = transferPriceText.replace(',', '.').toDoubleOrNull()
                        val transferNetPrice: Double? = null
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
                                parentCategoryName = selectedParentCategoryName.ifBlank { null },
                                categoryName = selectedCategoryName.ifBlank { null },
                                providerName = selectedProviderName.ifBlank { null },
                                providerSku = providerSku.ifBlank { null },
                                brand = brand.ifBlank { null },
                                color = color.ifBlank { null },
                                sizes = selectedSizes,
                                minStock = minStock,
                                canManagePublication = canManagePublication,
                                publishRequested = isPublished,
                                pendingImageUris = pendingImageUris.toList()
                            ) { result ->
                                if (result.isSuccess) {
                                    onSaved()
                                } else {
                                    infoMessage = result.exceptionOrNull()?.message ?: "Error guardando producto"
                                }
                            }
                        } else {
                            viewModel.updateProduct(
                                id = editId,
                                name = name,
                                barcode = barcode.ifBlank { null },
                                purchasePrice = purchase,
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
                                parentCategoryName = selectedParentCategoryName.ifBlank { null },
                                categoryName = selectedCategoryName.ifBlank { null },
                                providerName = selectedProviderName.ifBlank { null },
                                providerSku = providerSku.ifBlank { null },
                                brand = brand.ifBlank { null },
                                color = color.ifBlank { null },
                                sizes = selectedSizes,
                                minStock = minStock,
                                canManagePublication = canManagePublication,
                                publishRequested = isPublished
                            ) { result ->
                                if (result.isSuccess) {
                                    onSaved()
                                } else {
                                    infoMessage = result.exceptionOrNull()?.message ?: "Error actualizando producto"
                                }
                            }
                        }
                    }
                ) {
                    Text(if (editId == null) "Guardar" else "Actualizar")
                }

                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Cancelar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Mensaje simple para estados de info/error.
 */
private fun persistPreviewBitmap(context: android.content.Context, bitmap: Bitmap): Uri? {
    return runCatching {
        val file = java.io.File.createTempFile(
            "product_camera_",
            ".jpg",
            context.cacheDir
        )
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        Uri.fromFile(file)
    }.getOrNull()
}

@Composable
private fun InfoMessage(
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Información") },
        text = { Text(text) }
    )
}
