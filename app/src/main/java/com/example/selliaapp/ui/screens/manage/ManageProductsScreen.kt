package com.example.selliaapp.ui.screens.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.domain.product.ProductSortOption
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.ProductEditorDialog
import com.example.selliaapp.ui.components.ProductQuickDetailDialog
import com.example.selliaapp.ui.components.StockBySizeDialog
import com.example.selliaapp.viewmodel.ManageProductsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

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
        onBulkImport = {}
    )
}

@Composable
fun ManageProductsScreen(
    vm: ManageProductsViewModel,
    onBack: () -> Unit,
    onShowQr: () -> Unit,
    onBulkImport: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val products by vm.filteredProducts.collectAsState(initial = emptyList())

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showSizeEditor by remember { mutableStateOf(false) }
    var editingSizeProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var sizeStocksDraft by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

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
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) {
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

            LazyColumn(Modifier.fillMaxSize()) {
                items(products, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            val sizesInfo = if (p.sizes.isEmpty()) "Talles: sin info por el momento" else "Talles: ${p.sizes.joinToString()}"
                            Text(
                                "Lista: ${p.listPrice ?: 0.0} · " +
                                    "Efectivo: ${p.cashPrice ?: p.listPrice ?: 0.0} · " +
                                    "Transferencia: ${p.transferPrice ?: p.listPrice ?: 0.0} · " +
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

    if (showEditor) {
        ProductEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, barcode, purchasePrice, listPrice, cashPrice, transferPrice, mlPrice, ml3cPrice, ml6cPrice, stock, minStock, description, imageUrls ->
                scope.launch {
                    val normalizedImages = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
                    val base: ProductEntity = editing ?: ProductEntity(
                        id = 0,
                        code = null,
                        barcode = barcode,
                        name = name,
                        purchasePrice = purchasePrice,
                        listPrice = listPrice,
                        cashPrice = cashPrice,
                        transferPrice = transferPrice,
                        mlPrice = mlPrice,
                        ml3cPrice = ml3cPrice,
                        ml6cPrice = ml6cPrice,
                        quantity = stock,
                        description = description,
                        imageUrl = normalizedImages.firstOrNull(),
                        imageUrls = normalizedImages,
                        category = null,
                        minStock = minStock,
                        updatedAt = LocalDate.now()
                    )

                    val toSave: ProductEntity = base.copy(
                        name = name,
                        barcode = barcode,
                        purchasePrice = purchasePrice,
                        listPrice = listPrice,
                        cashPrice = cashPrice,
                        transferPrice = transferPrice,
                        mlPrice = mlPrice,
                        ml3cPrice = ml3cPrice,
                        ml6cPrice = ml6cPrice,
                        quantity = stock,
                        description = description,
                        imageUrl = normalizedImages.firstOrNull(),
                        imageUrls = normalizedImages,
                        minStock = minStock,
                        updatedAt = LocalDate.now()
                    )

                    vm.upsert(toSave)
                    showEditor = false
                }
            }
        )
    }

    selectedProduct?.let { product ->
        ProductQuickDetailDialog(
            product = product,
            onDismiss = { selectedProduct = null },
            onEdit = {
                selectedProduct = null
                editing = product
                showEditor = true
                editingSizeProduct = product
                showSizeEditor = true
            },
            onDelete = {
                selectedProduct = null
                scope.launch { vm.deleteById(product.id) }
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
