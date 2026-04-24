package com.example.selliaapp.ui.screens.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.domain.product.ProductSortOption
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.ProductQuickDetailDialog
import com.example.selliaapp.ui.components.StockBySizeDialog
import com.example.selliaapp.viewmodel.ManageProductsViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import com.example.selliaapp.data.local.entity.VariantEntity

@Composable
fun ManageProductsRoute(
    vm: ManageProductsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onShowQr: () -> Unit = {}
) {
    ManageProductsScreen(
        vm = vm,
        onBack = onBack,
        onShowQr = onShowQr,
        onBulkImport = {},
        onCreateProduct = {},
        onEditProduct = {},
        onAdjustStock = {},
        onViewMovements = {}
    )
}

@Composable
fun ManageProductsScreen(
    vm: ManageProductsViewModel,
    onBack: () -> Unit,
    onShowQr: () -> Unit,
    onBulkImport: () -> Unit,
    onCreateProduct: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit,
    onAdjustStock: (ProductEntity) -> Unit,
    onViewMovements: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val products by vm.filteredProducts.collectAsState(initial = emptyList())

    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showSizeEditor by remember { mutableStateOf(false) }
    var editingSizeProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var sizeStocksDraft by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var quickPoProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var variantMatrixProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var variantMatrixDraft by remember { mutableStateOf<List<VariantEntity>>(emptyList()) }
    var bulkVariantImportProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var bulkVariantInput by remember { mutableStateOf("") }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "AR")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    fun downloadProductQrFromDetail(product: ProductEntity) {
        exportQrPdf(
            context = context,
            items = listOf(product),
            fileName = "qr_${product.id}_detalle",
            includePrices = true,
            currencyFormatter = currencyFormatter,
            resolveQrValue = ::resolveSkuValue,
            resolveSkuValue = ::resolveSkuValue
        )
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Stock interno",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onBulkImport) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Carga masiva")
                    }
                    IconButton(onClick = onShowQr) {
                        Icon(Icons.Default.QrCode, contentDescription = "Ver QR")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateProduct) {
                Icon(Icons.Default.Add, contentDescription = "Añadir producto")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    label = { Text("Buscar en cualquier campo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.parentCategory,
                        onValueChange = vm::setParentCategory,
                        label = { Text("Categoría") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = vm::setCategory,
                        label = { Text("Subcategoría") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.color,
                        onValueChange = vm::setColor,
                        label = { Text("Color") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.size,
                        onValueChange = vm::setSize,
                        label = { Text("Talle") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.minPrice,
                        onValueChange = vm::setMinPrice,
                        label = { Text("Precio mín") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.maxPrice,
                        onValueChange = vm::setMaxPrice,
                        label = { Text("Precio máx") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sortExpanded = true }, modifier = Modifier.weight(1f)) {
                        Text("Orden: ${state.sort.label}")
                    }
                    Button(onClick = vm::clearFilters, modifier = Modifier.weight(1f)) {
                        Text("Limpiar filtros")
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        ProductSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    vm.setSort(option)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.onlyLowStock,
                        onClick = vm::toggleLowStock,
                        label = { Text("Bajo stock") }
                    )
                    FilterChip(
                        selected = state.onlyNoImage,
                        onClick = vm::toggleNoImage,
                        label = { Text("Sin imagen") }
                    )
                    FilterChip(
                        selected = state.onlyNoBarcode,
                        onClick = vm::toggleNoBarcode,
                        label = { Text("Sin código") }
                    )
                }
                Text("Resultados: ${products.size}")
            }

            if (products.isEmpty()) {
                val hasActiveFilters = state.query.isNotBlank() ||
                    state.parentCategory.isNotBlank() ||
                    state.category.isNotBlank() ||
                    state.color.isNotBlank() ||
                    state.size.isNotBlank() ||
                    state.minPrice.isNotBlank() ||
                    state.maxPrice.isNotBlank() ||
                    state.onlyLowStock ||
                    state.onlyNoImage ||
                    state.onlyNoBarcode
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (hasActiveFilters) "Sin resultados para la búsqueda"
                               else "Todavía no hay productos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (hasActiveFilters)
                                   "Intentá con otros filtros o limpiá la búsqueda."
                               else "Cargá tu primer producto para comenzar a gestionar el stock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    if (hasActiveFilters) {
                        Button(onClick = vm::clearFilters) {
                            Text("Limpiar filtros")
                        }
                    } else {
                        Button(onClick = onCreateProduct) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar producto")
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(products, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = {
                                val sizesInfo = if (p.sizes.isEmpty()) "Talles: sin info por el momento" else "Talles: ${p.sizes.joinToString()}"
                                val effectiveTransferPrice = p.cashPrice ?: p.transferPrice ?: p.listPrice ?: 0.0
                                Text(
                                    "Lista: ${p.listPrice ?: 0.0} · " +
                                        "Efectivo/Transferencia: $effectiveTransferPrice · " +
                                        "Stock: ${p.quantity} · Código: ${p.barcode ?: "—"} · $sizesInfo"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { selectedProduct = p }
                        )
                    }
                }
            }
        }
    }

    selectedProduct?.let { product ->
        ProductQuickDetailDialog(
            product = product,
            onDismiss = { selectedProduct = null },
            onEdit = {
                selectedProduct = null
                onEditProduct(product)
            },
            onDelete = {
                selectedProduct = null
                scope.launch { vm.deleteById(product.id) }
            },
            onAdjustStock = {
                selectedProduct = null
                onAdjustStock(product)
            },
            onViewMovements = {
                selectedProduct = null
                onViewMovements()
            },
            onPrintQr = {
                downloadProductQrFromDetail(product)
            },
            onOrderFromProvider = if (!product.providerName.isNullOrBlank() && product.providerId != null) {
                {
                    selectedProduct = null
                    quickPoProduct = product
                }
            } else null,
            onEditVariantsMatrix = {
                selectedProduct = null
                variantMatrixProduct = product
            },
            onBulkImportVariants = {
                selectedProduct = null
                bulkVariantImportProduct = product
                bulkVariantInput = ""
            }
        )
    }

    quickPoProduct?.let { product ->
        QuickPurchaseOrderDialog(
            product = product,
            onDismiss = { quickPoProduct = null },
            onCreate = { qty, price ->
                vm.createQuickPurchaseOrder(product, qty, price)
                quickPoProduct = null
            }
        )
    }


    if (showSizeEditor && editingSizeProduct != null) {
        val productForSizes = editingSizeProduct!!

        LaunchedEffect(productForSizes.id) {
            sizeStocksDraft = vm.getSizeStockMap(productForSizes.id)
        }

        StockBySizeDialog(
            totalStock = productForSizes.quantity,
            availableSizes = productForSizes.sizes,
            initialQuantities = sizeStocksDraft,
            onDismiss = {
                showSizeEditor = false
                editingSizeProduct = null
            },
            onSave = { sizeMap ->
                vm.saveSizeStocks(productForSizes, sizeMap)
                showSizeEditor = false
                editingSizeProduct = null
            }
        )
    }

    variantMatrixProduct?.let { product ->
        LaunchedEffect(product.id) {
            variantMatrixDraft = vm.getVariantMatrix(product.id)
        }
        VariantMatrixDialog(
            product = product,
            variants = variantMatrixDraft,
            onDismiss = { variantMatrixProduct = null },
            onSave = { rows ->
                vm.saveVariantMatrix(product, rows)
                variantMatrixProduct = null
            }
        )
    }

    bulkVariantImportProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { bulkVariantImportProduct = null },
            title = { Text("Carga masiva de variantes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Formato por línea: talle,color,stock[,sku]")
                    OutlinedTextField(
                        value = bulkVariantInput,
                        onValueChange = { bulkVariantInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        label = { Text("Pegar CSV") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importVariantsFromCsv(product, bulkVariantInput)
                    bulkVariantImportProduct = null
                }) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkVariantImportProduct = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Atención") },
            text = { Text(message ?: "") },
            confirmButton = {
                Button(onClick = { vm.clearMessage() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun VariantMatrixDialog(
    product: ProductEntity,
    variants: List<VariantEntity>,
    onDismiss: () -> Unit,
    onSave: (List<VariantEntity>) -> Unit
) {
    val sizes = remember(product) { product.sizes.ifEmpty { listOf("Único") } }
    val colors = remember(product, variants) {
        val variantColors = variants.mapNotNull { it.option2?.trim()?.takeIf(String::isNotBlank) }
        (variantColors + listOfNotNull(product.color?.trim()?.takeIf(String::isNotBlank))).distinct().ifEmpty { listOf("Base") }
    }
    var draft by remember(variants) {
        mutableStateOf(
            variants.associateBy { "${it.option1}|${it.option2}" }.mapValues { it.value.quantity }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Matriz talle × color · ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { color ->
                    Text(color, style = MaterialTheme.typography.titleSmall)
                    sizes.forEach { size ->
                        val key = "$size|$color"
                        OutlinedTextField(
                            value = (draft[key] ?: 0).toString(),
                            onValueChange = { draft = draft + (key to (it.toIntOrNull() ?: 0).coerceAtLeast(0)) },
                            label = { Text("Talle $size") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rows = draft.map { (key, qty) ->
                    val split = key.split("|")
                    VariantEntity(
                        productId = product.id,
                        sku = null,
                        option1 = split.getOrNull(0),
                        option2 = split.getOrNull(1),
                        quantity = qty
                    )
                }
                onSave(rows)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun QuickPurchaseOrderDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onCreate: (quantity: Double, unitPrice: Double) -> Unit
) {
    var qty by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var price by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                product.purchasePrice?.toString() ?: ""
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pedir a ${product.providerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Producto: ${product.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Cantidad") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Precio unitario (costo)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val q = qty.text.toDoubleOrNull() ?: return@TextButton
                    val p = price.text.toDoubleOrNull() ?: return@TextButton
                    if (q > 0 && p >= 0) onCreate(q, p)
                }
            ) { Text("Crear pedido") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
