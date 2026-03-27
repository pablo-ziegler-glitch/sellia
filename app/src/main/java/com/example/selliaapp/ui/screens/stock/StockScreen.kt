package com.example.selliaapp.ui.screens.stock

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.ProductQuickDetailDialog
import com.example.selliaapp.viewmodel.ProductViewModel
import com.example.selliaapp.viewmodel.StockFilter
import com.example.selliaapp.viewmodel.StockViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla de Stock:
 * - Franja superior con buscador.
 * - Debajo, listado de productos.
 * - FAB redondo "+" con speed-dial (Importar archivo / Escanear / Agregar).
 *
 * Mejoras:
 *   1. Filtros persistidos en StockViewModel (sobreviven navegación).
 *   2. Color progresivo de stock: verde (ok), amarillo (bajo), rojo (crítico).
 *   3. Swipe +1 / -1 en cada ítem sin abrir QuickStockAdjustScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    vm: ProductViewModel = hiltViewModel(),
    stockVm: StockViewModel = hiltViewModel(),
    onAddProduct: () -> Unit,
    onScan: () -> Unit,
    onImportCsv: () -> Unit,
    onPhotoIntake: () -> Unit,
    onOpenPriceAudit: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit,
    onOpenQrLabels: () -> Unit,
    onAdjustStock: (ProductEntity) -> Unit = {},
    onViewMovements: () -> Unit = {},
    onProductClick: (ProductEntity) -> Unit,
    onBack: () -> Unit
) {
    val products by vm.products.collectAsState(initial = emptyList())

    // [MEJORA 1] query y filtro viven en el ViewModel → sobreviven a la navegación
    val query by stockVm.searchQuery.collectAsState()
    val stockFilter by stockVm.stockFilter.collectAsState()

    val context = LocalContext.current

    var isImporting by rememberSaveable { mutableStateOf(false) }
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var importErrorSummary by remember { mutableStateOf<String?>(null) }
    var importErrorDetails by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectionModeEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedProductIds by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var showDeleteSelectedDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllDialog by rememberSaveable { mutableStateOf(false) }
    var detailProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showDeleteBackupConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var deleteBackupConfirmationInput by rememberSaveable { mutableStateOf("") }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(importMessage) {
        importMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
        }
    }

    val openImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        lastFileName = queryDisplayName(context.contentResolver, uri)
        isImporting = true
        importMessage = null
        vm.importProductsFromFile(
            context = context,
            fileUri = uri,
            strategy = ProductRepository.ImportStrategy.Append
        ) { result ->
            isImporting = false
            if (result.hasErrors) {
                importErrorSummary = result.toUserMessage(fileName = lastFileName, includeErrors = false)
                importErrorDetails = result.errors
                importMessage = null
            } else {
                importMessage = result.toUserMessage(fileName = lastFileName)
            }
        }
    }

    // Filtro local — usa los valores del ViewModel
    val filtered = remember(products, query, stockFilter) {
        val q = query.trim()
        products
            .filter { p ->
                q.isEmpty() ||
                    p.name.contains(q, ignoreCase = true) ||
                    (p.barcode?.contains(q, ignoreCase = true) == true) ||
                    (p.code?.contains(q, ignoreCase = true) == true)
            }
            .filter { p ->
                when (stockFilter) {
                    StockFilter.ALL -> true
                    StockFilter.OUT_OF_STOCK -> p.quantity == 0
                    StockFilter.LOW_STOCK -> {
                        val min = p.minStock ?: 0
                        p.quantity > 0 && min > 0 && p.quantity < min
                    }
                }
            }
    }

    val outOfStockCount = remember(products) { products.count { it.quantity == 0 } }
    val lowStockCount = remember(products) {
        products.count { p ->
            val min = p.minStock ?: 0
            p.quantity > 0 && min > 0 && p.quantity < min
        }
    }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    var fabExpanded by rememberSaveable { mutableStateOf(false) }

    val onForcePriceRefresh = {
        vm.forceRecalculateAutoPricing()
        importMessage = "Recalculando precios automáticos en segundo plano."
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                BackTopAppBar(
                    title = if (selectionModeEnabled) "Seleccionados: ${selectedProductIds.size}" else "Stock",
                    onBack = {
                        if (selectionModeEnabled) {
                            selectionModeEnabled = false
                            selectedProductIds = emptySet()
                        } else {
                            onBack()
                        }
                    },
                    actions = {
                        if (selectionModeEnabled) {
                            IconButton(onClick = {
                                if (selectedProductIds.isNotEmpty()) {
                                    showDeleteSelectedDialog = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar seleccionados"
                                )
                            }
                        } else {
                            Row {
                                IconButton(onClick = onForcePriceRefresh) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Forzar actualización de precios"
                                    )
                                }
                                IconButton(onClick = onOpenPriceAudit) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Auditoría de precios"
                                    )
                                }
                            }
                            IconButton(onClick = onOpenQrLabels) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Imprimir QR"
                                )
                            }
                        }
                    }
                )
                if (isImporting) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
                // [MEJORA 1] onValueChange delega al ViewModel
                OutlinedTextField(
                    value = query,
                    onValueChange = { stockVm.setSearchQuery(it) },
                    label = { Text("Buscar por nombre o código") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = stockFilter == StockFilter.ALL,
                        onClick = { stockVm.setStockFilter(StockFilter.ALL) },
                        label = { Text("Todos (${products.size})") }
                    )
                    if (lowStockCount > 0) {
                        FilterChip(
                            selected = stockFilter == StockFilter.LOW_STOCK,
                            onClick = { stockVm.setStockFilter(StockFilter.LOW_STOCK) },
                            label = { Text("Stock bajo ($lowStockCount)") }
                        )
                    }
                    if (outOfStockCount > 0) {
                        FilterChip(
                            selected = stockFilter == StockFilter.OUT_OF_STOCK,
                            onClick = { stockVm.setStockFilter(StockFilter.OUT_OF_STOCK) },
                            label = { Text("Sin stock ($outOfStockCount)") }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(end = 24.dp, bottom = 28.dp)) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmallFabWithLabel(
                                label = "Agregar producto",
                                icon = { Icon(Icons.Default.Add, contentDescription = "Agregar producto") },
                                onClick = { fabExpanded = false; onAddProduct() }
                            )
                            SmallFabWithLabel(
                                label = "Escanear código",
                                icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Escanear") },
                                onClick = { fabExpanded = false; onScan() }
                            )
                            SmallFabWithLabel(
                                label = "Historial de movimientos",
                                icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                                onClick = { fabExpanded = false; onViewMovements() }
                            )
                            SmallFabWithLabel(
                                label = "Cargar por foto (IA)",
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Cargar por foto") },
                                onClick = { fabExpanded = false; onPhotoIntake() }
                            )
                            SmallFabWithLabel(
                                label = "Importar archivo",
                                icon = { Icon(Icons.Default.Description, contentDescription = "Importar archivo") },
                                onClick = {
                                    fabExpanded = false
                                    openImportLauncher.launch(
                                        arrayOf(
                                            "text/*",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.google-apps.spreadsheet"
                                        )
                                    )
                                }
                            )
                            SmallFabWithLabel(
                                label = "Seleccionar para eliminar",
                                icon = { Icon(Icons.Default.Delete, contentDescription = "Seleccionar para eliminar") },
                                onClick = { fabExpanded = false; selectionModeEnabled = true }
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Acciones")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No hay productos que coincidan.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { p ->
                    // [MEJORA 3] Swipe +1 / -1 wrappea el ProductRow
                    SwipeableProductRow(
                        product = p,
                        currency = currency,
                        isSelectionMode = selectionModeEnabled,
                        isSelected = selectedProductIds.contains(p.id),
                        onClick = {
                            if (selectionModeEnabled) {
                                selectedProductIds = if (selectedProductIds.contains(p.id)) {
                                    selectedProductIds - p.id
                                } else {
                                    selectedProductIds + p.id
                                }
                            } else {
                                detailProduct = p
                            }
                        },
                        onLongClick = {
                            selectionModeEnabled = true
                            selectedProductIds = selectedProductIds + p.id
                        },
                        onQuickAdjust = { onAdjustStock(p) },
                        onSwipeDelta = { delta -> stockVm.quickAdjust(p.id, delta) }
                    )
                }
            }
        }
    }


    detailProduct?.let { product ->
        ProductQuickDetailDialog(
            product = product,
            onDismiss = { detailProduct = null },
            onEdit = {
                detailProduct = null
                onEditProduct(product)
            },
            onDelete = {
                detailProduct = null
                deleteBackupConfirmationInput = ""
                pendingDeleteAction = {
                    vm.deleteById(product.id) { result ->
                        result.onSuccess {
                            importMessage = "Producto eliminado correctamente. También se purgó de los backups en la nube."
                        }.onFailure { error ->
                            importMessage = error.message ?: "No se pudo eliminar el producto."
                        }
                    }
                }
                showDeleteBackupConfirmDialog = true
            },
            onAdjustStock = {
                detailProduct = null
                onAdjustStock(product)
            },
            onViewMovements = {
                detailProduct = null
                onViewMovements()
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Eliminar productos seleccionados") },
            text = { Text("Se van a eliminar ${selectedProductIds.size} productos seleccionados. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    val idsToDelete = selectedProductIds
                    showDeleteSelectedDialog = false
                    deleteBackupConfirmationInput = ""
                    pendingDeleteAction = {
                        vm.deleteProductsByIds(idsToDelete) { result ->
                            result.onSuccess { deleted ->
                                importMessage = "Se eliminaron $deleted productos. También se purgaron de los backups en la nube."
                                selectionModeEnabled = false
                                selectedProductIds = emptySet()
                            }.onFailure { error ->
                                importMessage = error.message ?: "No se pudieron eliminar los productos seleccionados."
                            }
                        }
                    }
                    showDeleteBackupConfirmDialog = true
                }) { Text("Eliminar") }
            },
            dismissButton = {
                Button(onClick = { showDeleteSelectedDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Eliminar todo el stock") },
            text = { Text("Se van a eliminar todos los productos del stock. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteAllDialog = false
                    deleteBackupConfirmationInput = ""
                    pendingDeleteAction = {
                        vm.deleteAllProducts { result ->
                            result.onSuccess { deleted ->
                                importMessage = "Se eliminaron $deleted productos del stock. También se purgaron de los backups en la nube."
                                selectionModeEnabled = false
                                selectedProductIds = emptySet()
                            }.onFailure { error ->
                                importMessage = error.message ?: "No se pudo eliminar todo el stock."
                            }
                        }
                    }
                    showDeleteBackupConfirmDialog = true
                }) { Text("Eliminar todo") }
            },
            dismissButton = {
                Button(onClick = { showDeleteAllDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteBackupConfirmDialog) {
        val confirmationPhrase = "ELIMINAR"
        AlertDialog(
            onDismissRequest = {
                showDeleteBackupConfirmDialog = false
                deleteBackupConfirmationInput = ""
                pendingDeleteAction = null
            },
            title = { Text("Confirmación final") },
            text = {
                Column {
                    Text("Esta acción también eliminará el producto de los backups en Firebase y no se podrá recuperar en futuras restauraciones.")
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Escribí $confirmationPhrase para confirmar.")
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = deleteBackupConfirmationInput,
                        onValueChange = { deleteBackupConfirmationInput = it },
                        singleLine = true,
                        label = { Text("Confirmación") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBackupConfirmDialog = false
                        pendingDeleteAction?.invoke()
                        pendingDeleteAction = null
                        deleteBackupConfirmationInput = ""
                    },
                    enabled = deleteBackupConfirmationInput.trim().equals(confirmationPhrase, ignoreCase = true)
                ) { Text("Confirmar eliminación") }
            },
            dismissButton = {
                Button(onClick = {
                    showDeleteBackupConfirmDialog = false
                    deleteBackupConfirmationInput = ""
                    pendingDeleteAction = null
                }) { Text("Cancelar") }
            }
        )
    }

    if (importErrorSummary != null) {
        val scrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Errores de importación") },
            text = {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Text(importErrorSummary ?: "")
                    if (importErrorDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(8.dp))
                        importErrorDetails.forEach { error -> Text("• $error") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    importErrorSummary = null
                    importErrorDetails = emptyList()
                }) { Text("OK") }
            }
        )
    }
}

// ─────────────────────────────────────────────
// [MEJORA 3] Swipe +1 / -1
// ─────────────────────────────────────────────

private val SwipeThreshold = 110f   // px para disparar la acción
private val SwipeMaxOffset = 180f   // px máximo de arrastre visible

/** Envuelve [ProductRow] con gestos de swipe izquierda (−1) y derecha (+1). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableProductRow(
    product: ProductEntity,
    currency: NumberFormat,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickAdjust: () -> Unit,
    onSwipeDelta: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {

        // — Fondo: acciones reveladas por el swipe —
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swipe → derecha: +1 (verde)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(
                        color = Color(0xFF388E3C),
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "+1",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("+1", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Swipe ← izquierda: -1 (rojo)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "-1",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("-1", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // — Frente: tarjeta del producto, desplazable —
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(isSelectionMode) {
                    // En modo selección el swipe está deshabilitado
                    if (isSelectionMode) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value >= SwipeThreshold -> {
                                        onSwipeDelta(+1)
                                    }
                                    offsetX.value <= -SwipeThreshold -> {
                                        onSwipeDelta(-1)
                                    }
                                }
                                offsetX.animateTo(0f)
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(-SwipeMaxOffset, SwipeMaxOffset)
                                )
                            }
                        }
                    )
                }
        ) {
            ProductRow(
                product = product,
                currency = currency,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
                onQuickAdjust = onQuickAdjust
            )
        }
    }
}

// ─────────────────────────────────────────────
// ProductRow
// ─────────────────────────────────────────────

/** Ítem de la lista de productos con color de stock progresivo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductRow(
    product: ProductEntity,
    currency: NumberFormat,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickAdjust: () -> Unit
) {
    // [MEJORA 2] Color progresivo según porcentaje sobre el mínimo
    val (stockLabel, stockColor) = stockIndicator(product)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${product.quantity} uds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = stockColor
                    )
                    if (stockLabel != null) {
                        Text(
                            text = "· $stockLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = stockColor
                        )
                    }
                    product.listPrice?.let {
                        HorizontalDivider(
                            modifier = Modifier.size(width = 1.dp, height = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = currency.format(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                product.barcode?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelectionMode) {
                    Text(
                        text = if (isSelected) "✓ Seleccionado" else "Toque largo para seleccionar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isSelectionMode) {
                IconButton(onClick = onQuickAdjust) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Ajustar stock",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// [MEJORA 2] Lógica de color progresivo
// ─────────────────────────────────────────────

private val StockColorOk       = Color(0xFF388E3C)  // verde
private val StockColorLow      = Color(0xFFF9A825)  // amarillo/ámbar
private val StockColorCritical = Color(0xFFD32F2F)  // rojo

/**
 * Devuelve (etiqueta, color) según el nivel de stock relativo al mínimo:
 * - OK      (≥ mínimo o sin mínimo): verde, sin etiqueta
 * - Bajo    (entre 50 % y 100 % del mínimo): amarillo, "Stock bajo"
 * - Crítico (< 50 % del mínimo o en 0): rojo, "Crítico" / "Sin stock"
 */
@Composable
private fun stockIndicator(product: ProductEntity): Pair<String?, Color> {
    val min = product.minStock ?: 0
    return when {
        product.quantity == 0 ->
            "Sin stock" to StockColorCritical
        min > 0 && product.quantity.toFloat() / min.toFloat() < 0.5f ->
            "Crítico" to StockColorCritical
        min > 0 && product.quantity < min ->
            "Stock bajo" to StockColorLow
        else ->
            null to StockColorOk
    }
}

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

/** Mini-FAB con etiqueta alineada a la derecha (para el speed-dial). */
@Composable
private fun SmallFabWithLabel(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        SmallFloatingActionButton(onClick = onClick) { icon() }
    }
}

private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
    return cr.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}

private fun ImportResult.toUserMessage(
    fileName: String? = null,
    maxErrorsToShow: Int = 25,
    includeErrors: Boolean = true
): String {
    val sb = StringBuilder()
    fileName?.let { sb.appendLine("Archivo: $it") }
    sb.appendLine("Insertados: $inserted")
    sb.appendLine("Actualizados: $updated")

    if (errors.isNotEmpty()) {
        if (includeErrors) {
            sb.appendLine("Errores (${errors.size}):")
            errors.take(maxErrorsToShow).forEachIndexed { i, msg ->
                sb.appendLine(" • [${i + 1}] $msg")
            }
            if (errors.size > maxErrorsToShow) {
                sb.appendLine(" …y ${errors.size - maxErrorsToShow} errores más.")
            }
        } else {
            sb.appendLine("Errores: ${errors.size}")
        }
    } else {
        sb.appendLine("Sin errores.")
    }
    return sb.toString().trim()
}

/** Tipos "UI" de ejemplo: adaptá a los tuyos reales */
data class ProductUi(val id: Int, val name: String)
@Composable
private fun ProductList(
    products: List<ProductUi>,
    onScan: () -> Unit,
    onClick: (ProductUi) -> Unit
) {
    if (products.isEmpty()) {
        Text("No hay productos cargados.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            products.forEach { p ->
                ElevatedCard(onClick = { onClick(p) }) {
                    Text(
                        p.name,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
