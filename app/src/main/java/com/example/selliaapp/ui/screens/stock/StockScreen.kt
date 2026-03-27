package com.example.selliaapp.ui.screens.stock

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.ProductQuickDetailDialog
import com.example.selliaapp.viewmodel.ProductViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla de Stock:
 * - Franja superior con buscador.
 * - Debajo, listado de productos.
 * - FAB redondo “+” con speed-dial (Importar archivo / Escanear / Agregar).
 *
 * [NUEVO] Mejores UX:
 *   - Snackbar para mensajes de importación.
 *   - Indicador lineal de progreso durante importación.
 *   - rememberSaveable en estados claves.
 */
private enum class StockFilter { ALL, LOW_STOCK, OUT_OF_STOCK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    vm: ProductViewModel = hiltViewModel(),
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

    /* [ANTERIOR]
    var query by remember { mutableStateOf("") }
    */
    var query by rememberSaveable { mutableStateOf("") } // [NUEVO] preserva al rotar
    var stockFilter by rememberSaveable { mutableStateOf(StockFilter.ALL) }

    val context = LocalContext.current

    // Estado de UI para feedback
    /* [ANTERIOR]
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var lastFileName by remember { mutableStateOf<String?>(null) }
    */
    var isImporting by rememberSaveable { mutableStateOf(false) }        // [NUEVO]
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) } // [NUEVO]
    var lastFileName by rememberSaveable { mutableStateOf<String?>(null) }   // [NUEVO]
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

    // Snackbar host para mostrar mensajes del import
    val snackbarHostState = remember { SnackbarHostState() } // [NUEVO]
    LaunchedEffect(importMessage) { // [NUEVO]
        importMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
        }
    }

    // Launcher SAF para abrir documento (CSV/Excel/Sheets)
    val openImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        // (Opcional) permiso persistente
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Si el proveedor no soporta persistencia, no pasa nada.
        }

        // Obtener nombre amigable (opcional)
        lastFileName = queryDisplayName(context.contentResolver, uri)

        // Lanzar importación
        isImporting = true
        importMessage = null
        vm.importProductsFromFile(
            context = context,
            fileUri = uri,
            // strategy: "append" suma al stock existente; "replace" pisa valores
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

    // Filtro local (null-safe para barcode/code)
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

    // Conteos para los chips
    val outOfStockCount = remember(products) { products.count { it.quantity == 0 } }
    val lowStockCount = remember(products) {
        products.count { p ->
            val min = p.minStock ?: 0
            p.quantity > 0 && min > 0 && p.quantity < min
        }
    }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    // Estado de “menú de funcionalidades” (speed dial)
    /* [ANTERIOR]
    var fabExpanded by remember { mutableStateOf(false) }
    */
    var fabExpanded by rememberSaveable { mutableStateOf(false) } // [NUEVO]

    val onForcePriceRefresh = {
        vm.forceRecalculateAutoPricing()
        importMessage = "Recalculando precios automáticos en segundo plano."
    }

    Scaffold(
        /* [ANTERIOR]
        // sin snackbarHost
        */
        snackbarHost = { SnackbarHost(snackbarHostState) }, // [NUEVO]
        topBar = {
            // Barra superior con la franja de búsqueda
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
                // Indicador de importación bajo la AppBar
                if (isImporting) { // [NUEVO]
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
                // “Franja” de búsqueda ocupando el ancho
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(“Buscar por nombre o código”) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                // Chips de filtro de stock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = stockFilter == StockFilter.ALL,
                        onClick = { stockFilter = StockFilter.ALL },
                        label = { Text(“Todos (${products.size})”) }
                    )
                    if (lowStockCount > 0) {
                        FilterChip(
                            selected = stockFilter == StockFilter.LOW_STOCK,
                            onClick = { stockFilter = StockFilter.LOW_STOCK },
                            label = { Text(“Stock bajo ($lowStockCount)”) }
                        )
                    }
                    if (outOfStockCount > 0) {
                        FilterChip(
                            selected = stockFilter == StockFilter.OUT_OF_STOCK,
                            onClick = { stockFilter = StockFilter.OUT_OF_STOCK },
                            label = { Text(“Sin stock ($outOfStockCount)”) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Columna con los mini-botones (aparecen encima del “+” cuando está expandido)
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
                            // Agregar producto manualmente
                            SmallFabWithLabel(
                                label = "Agregar producto",
                                icon = { Icon(Icons.Default.Add, contentDescription = "Agregar producto") },
                                onClick = {
                                    fabExpanded = false
                                    onAddProduct()
                                }
                            )
                            // Escanear código
                            SmallFabWithLabel(
                                label = "Escanear código",
                                icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Escanear") },
                                onClick = {
                                    fabExpanded = false
                                    onScan()
                                }
                            )
                            // Historial de movimientos
                            SmallFabWithLabel(
                                label = "Historial de movimientos",
                                icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                                onClick = {
                                    fabExpanded = false
                                    onViewMovements()
                                }
                            )
                            SmallFabWithLabel(
                                label = "Cargar por foto (IA)",
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Cargar por foto") },
                                onClick = {
                                    fabExpanded = false
                                    onPhotoIntake()
                                }
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
                                onClick = {
                                    fabExpanded = false
                                    selectionModeEnabled = true
                                }
                            )
                        }
                    }

                    // FAB principal “+”, redondo y separado del borde
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        shape = CircleShape,
                        modifier = Modifier // margen extra para que quede “alejado” del extremo
                            .align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Acciones")
                    }
                }
            }
        }
    ) { padding ->
        // Contenido: solo lista (LazyColumn) debajo de la franja
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 96.dp, // deja espacio extra para que el FAB no tape el último ítem
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
                    ProductRow(
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
                        onQuickAdjust = { onAdjustStock(p) }
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
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancelar")
                }
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
                }) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
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
                ) {
                    Text("Confirmar eliminación")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showDeleteBackupConfirmDialog = false
                    deleteBackupConfirmationInput = ""
                    pendingDeleteAction = null
                }) {
                    Text("Cancelar")
                }
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
                        importErrorDetails.forEach { error ->
                            Text("• $error")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        importErrorSummary = null
                        importErrorDetails = emptyList()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

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

/** Ítem de la lista de productos — diseño simplificado con badge de stock. */
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
    val (stockLabel, stockColor) = when {
        product.quantity == 0 ->
            "Sin stock" to MaterialTheme.colorScheme.error
        (product.minStock ?: 0) > 0 && product.quantity < (product.minStock ?: 0) ->
            "Stock bajo" to MaterialTheme.colorScheme.tertiary
        else ->
            null to null
    }
    val cardBorderColor = if (isSelected) MaterialTheme.colorScheme.primary else null

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
            // Contenido principal
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
                    // Cantidad con color de estado
                    val qtyColor = stockColor ?: MaterialTheme.colorScheme.onSurface
                    Text(
                        text = "${product.quantity} uds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = qtyColor
                    )
                    // Badge de alerta solo si hay problema
                    if (stockLabel != null && stockColor != null) {
                        Text(
                            text = "· $stockLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = stockColor
                        )
                    }
                    // Precio principal
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
            // Botón de ajuste rápido (solo cuando NO está en selección)
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

/**
 * Utilidad para mostrar el nombre del archivo seleccionado con SAF.
 */
private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
    return cr.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}

/**
 * Convierte el resultado del import a un mensaje legible para UI.
 * Muestra archivo, insertados, actualizados y (si existen) hasta los primeros N errores.
 */
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
    // Stub de ejemplo. Tu lista real está arriba con LazyColumn.
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
