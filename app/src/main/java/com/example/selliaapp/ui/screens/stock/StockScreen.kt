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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberScrollState
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    vm: ProductViewModel = hiltViewModel(),
    onAddProduct: () -> Unit,
    onScan: () -> Unit,
    onImportCsv: () -> Unit,
    onProductClick: (ProductEntity) -> Unit,
    onBack: () -> Unit
) {
    val products by vm.products.collectAsState(initial = emptyList())

    /* [ANTERIOR]
    var query by remember { mutableStateOf("") }
    */
    var query by rememberSaveable { mutableStateOf("") } // [NUEVO] preserva al rotar

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
    val filtered = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products
        else products.filter { p ->
            p.name.contains(q, ignoreCase = true) ||
                    (p.barcode?.contains(q, ignoreCase = true) == true) ||
                    (p.code?.contains(q, ignoreCase = true) == true)
        }
    }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    // Estado de “menú de funcionalidades” (speed dial)
    /* [ANTERIOR]
    var fabExpanded by remember { mutableStateOf(false) }
    */
    var fabExpanded by rememberSaveable { mutableStateOf(false) } // [NUEVO]

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
                    label = { Text("Buscar por nombre o código") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
                            // Importar archivo de productos
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
                            // Escanear
                            SmallFabWithLabel(
                                label = "Escanear",
                                icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Escanear") },
                                onClick = {
                                    fabExpanded = false
                                    onScan()
                                }
                            )
                            SmallFabWithLabel(
                                label = "Eliminar seleccionados",
                                icon = { Icon(Icons.Default.Delete, contentDescription = "Eliminar seleccionados") },
                                onClick = {
                                    fabExpanded = false
                                    selectionModeEnabled = true
                                }
                            )
                            SmallFabWithLabel(
                                label = "Eliminar todos",
                                icon = { Icon(Icons.Default.Delete, contentDescription = "Eliminar todos") },
                                onClick = {
                                    fabExpanded = false
                                    showDeleteAllDialog = true
                                }
                            )
                            // Agregar producto
                            SmallFabWithLabel(
                                label = "Agregar producto",
                                icon = { Icon(Icons.Default.Add, contentDescription = "Agregar producto") },
                                onClick = {
                                    fabExpanded = false
                                    onAddProduct()
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
                                onProductClick(p)
                            }
                        },
                        onLongClick = {
                            selectionModeEnabled = true
                            selectedProductIds = selectedProductIds + p.id
                        }
                    )
                }
            }
        }
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
                    vm.deleteProductsByIds(idsToDelete) { result ->
                        result.onSuccess { deleted ->
                            importMessage = "Se eliminaron $deleted productos."
                            selectionModeEnabled = false
                            selectedProductIds = emptySet()
                        }.onFailure { error ->
                            importMessage = error.message ?: "No se pudieron eliminar los productos seleccionados."
                        }
                    }
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
                    vm.deleteAllProducts { result ->
                        result.onSuccess { deleted ->
                            importMessage = "Se eliminaron $deleted productos del stock."
                            selectionModeEnabled = false
                            selectedProductIds = emptySet()
                        }.onFailure { error ->
                            importMessage = error.message ?: "No se pudo eliminar todo el stock."
                        }
                    }
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

/** Ítem de la lista de productos (card simple). */
@Composable
private fun ProductRow(
    product: ProductEntity,
    currency: NumberFormat,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val listPrice = product.listPrice ?: 0.0
    val cashPrice = product.cashPrice ?: product.listPrice ?: 0.0
    val transferPrice = product.transferPrice ?: product.listPrice ?: 0.0
    val mlPriceLabel = product.mlPrice?.let { currency.format(it) } ?: "-"
    val ml3cPriceLabel = product.ml3cPrice?.let { currency.format(it) } ?: "-"
    val ml6cPriceLabel = product.ml6cPrice?.let { currency.format(it) } ?: "-"
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stock: ${product.quantity}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    "Lista: ${currency.format(listPrice)} · Efectivo: ${currency.format(cashPrice)} · Transferencia: ${currency.format(transferPrice)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "ML: $mlPriceLabel · ML 3C: $ml3cPriceLabel · ML 6C: $ml6cPriceLabel",
                style = MaterialTheme.typography.bodySmall
            )
            if (isSelectionMode) {
                Spacer(Modifier.size(2.dp))
                Text(
                    text = if (isSelected) "Seleccionado" else "Mantener presionado para seleccionar",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            product.barcode?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.size(2.dp))
                Text("Barcode: $it", style = MaterialTheme.typography.bodySmall)
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
