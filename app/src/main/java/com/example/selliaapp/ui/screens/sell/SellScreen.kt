package com.example.selliaapp.ui.screens.sell

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.ui.components.ProductDetailSheet
import com.example.selliaapp.ui.navigation.Routes
import com.example.selliaapp.viewmodel.ProductViewModel
import com.example.selliaapp.viewmodel.SellViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla de ventas (unificada a `ui`):
 * - Usa SOLO `ui` = sellVm.state.collectAsState() (no referencias a state.cart/error/lastInvoiceId)
 * - Recalcula remanentes y total desde `ui.items`
 * - Flujo de escaneo: si no existe → AddProduct con barcode precargado
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    sellVm: SellViewModel = hiltViewModel(),
    productVm: ProductViewModel = hiltViewModel(),
    onScanClick: () -> Unit,
    onBack: () -> Boolean,
    navController: NavController
) {
    val ui by sellVm.state.collectAsState()
    val allProducts by productVm.products.collectAsState(initial = emptyList())
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    var descuentoAdicionalActivo by remember { mutableStateOf(false) }
    var descuentoInput by remember { mutableStateOf("") }

    LaunchedEffect(ui.discountPercent) {
        descuentoAdicionalActivo = ui.discountPercent > 0
        descuentoInput = if (ui.discountPercent == 0) "" else ui.discountPercent.toString()
    }

    // Estado para mostrar mensajes en pantalla
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


    // Stock remanente por producto (stock total menos cantidad en carrito)
    val remainingById = remember(ui.items, allProducts) {
        val qtyById = ui.items.associate { it.productId to it.qty }
        allProducts.associate { p ->
            val inCart = qtyById[p.id] ?: 0
            p.id to (p.quantity - inCart).coerceAtLeast(0)
        }
    }

    // Selección de productos
    val searchFocusRequester = remember { FocusRequester() }
    var searchQuery by remember { mutableStateOf("") }
    var detailFor by remember { mutableStateOf<ProductEntity?>(null) }
    var showCancelPreSaleDialog by remember { mutableStateOf(false) }
    var variantSelectionProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val searchableByProductId = remember(allProducts) {
        allProducts.associate { product ->
            product.id to buildString {
                append(product.name.lowercase())
                append('|')
                append(product.barcode?.lowercase().orEmpty())
                append('|')
                append(product.code?.lowercase().orEmpty())
            }
        }
    }
    val filteredProducts by remember(allProducts, remainingById, searchableByProductId, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            allProducts.asSequence()
                .filter { (remainingById[it.id] ?: 0) > 0 }
                .let { sequence ->
                    if (q.isBlank()) sequence else sequence.filter { searchableByProductId[it.id]?.contains(q) == true }
                }
                .toList()
        }
    }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }
    LaunchedEffect(ui.selectedCustomerId, ui.selectedCustomerName) {
        if (ui.selectedCustomerId != null || !ui.selectedCustomerName.equals("Consumidor Final", ignoreCase = true)) {
            sellVm.setCustomer(null, "Consumidor Final")
        }
    }

    val currentEntry = navController.currentBackStackEntry
    val pendingProductId by currentEntry
        ?.savedStateHandle
        ?.getStateFlow<Int?>("pending_product_id", null)
        ?.collectAsState(initial = null)
        ?: remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pendingProductId, allProducts) {
        val targetId = pendingProductId
        if (targetId != null) {
            val product = allProducts.firstOrNull { it.id == targetId }
            if (product != null) {
                detailFor = product
                currentEntry?.savedStateHandle?.set("pending_product_id", null)
            }
        }
    }

    variantSelectionProduct?.let { product ->
        VariantSelectorDialog(
            product = product,
            onDismiss = { variantSelectionProduct = null },
            onConfirm = {
                variantSelectionProduct = null
                detailFor = product
            }
        )
    }

    if (showCancelPreSaleDialog) {
        AlertDialog(
            onDismissRequest = { showCancelPreSaleDialog = false },
            title = { Text("Cancelar preventa") },
            text = { Text("Se eliminará el carrito actual y no podrás recuperarlo.") },
            confirmButton = {
                Button(
                    onClick = {
                        sellVm.clear()
                        showCancelPreSaleDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Preventa cancelada.")
                        }
                    }
                ) {
                    Text("Cancelar preventa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelPreSaleDialog = false }) {
                    Text("Volver")
                }
            }
        )
    }

    detailFor?.let { product ->
        val remaining = remainingById[product.id] ?: product.quantity
        val maxQty = remaining.coerceAtLeast(0)
        ProductDetailSheet(
            product = product,
            initialQty = 1,
            maxQty = maxQty,
            currency = currency,
            onAddToCart = { qty ->
                val productBarcode = product.barcode
                if (!productBarcode.isNullOrBlank()) {
                    sellVm.addToCartByScan(
                        barcode = productBarcode,
                        qty = qty,
                        onNotFound = {
                            scope.launch {
                                snackbarHostState.showSnackbar("No se encontró el producto escaneado.")
                            }
                        }
                    )
                } else {
                    sellVm.addToCart(product, qty)
                }
                detailFor = null
            },
            onDismiss = { detailFor = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Venta", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { searchFocusRequester.requestFocus() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Buscar producto")
                        }
                        Button(
                            onClick = onScanClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scanear producto")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        label = { Text("Buscar producto") },
                        placeholder = { Text("Nombre, código o código de barras") }
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        filteredProducts.isEmpty() -> {
                            Text(
                                text = "Sin resultados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.height(220.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredProducts, key = { it.id }) { product ->
                                    TextButton(
                                        onClick = {
                                            if (product.sizes.isNotEmpty()) {
                                                variantSelectionProduct = product
                                            } else {
                                                detailFor = product
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text(
                                                text = product.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Stock: ${remainingById[product.id] ?: 0} · ${product.barcode ?: product.code ?: "Sin código"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (ui.items.isEmpty()) {
                Text("No hay productos en el carrito.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(ui.items, key = { it.productId }) { item ->
                        val canIncrease = item.qty < item.maxStock
                        val atMax = item.qty == item.maxStock && item.maxStock > 0
                        CartItemRow(
                            name = item.name,
                            barcode = item.barcode,
                            unitPrice = item.unitPrice,
                            qty = item.qty,
                            maxStock = item.maxStock,
                            lineTotal = item.lineTotal,
                            canIncrease = canIncrease,
                            onIncrease = { sellVm.increment(item.productId) },
                            onDecrease = { sellVm.decrement(item.productId) },
                            onRemove = { sellVm.remove(item.productId) },
                            onQtyChange = { qty -> sellVm.updateQty(item.productId, qty) },
                            currency = currency,
                            showAtMaxHint = atMax
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    item {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = "Resumen de la venta",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                ResumenRow(
                                    etiqueta = "Subtotal",
                                    valor = currency.format(ui.subtotal)
                                )
                                if (ui.customerDiscountPercent > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    ResumenRow(
                                        etiqueta = "Descuento cliente (${ui.customerDiscountPercent}%)",
                                        valor = "-${currency.format(ui.customerDiscountAmount)}",
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Descuento adicional", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = descuentoAdicionalActivo,
                                        onCheckedChange = { activo ->
                                            descuentoAdicionalActivo = activo
                                            if (!activo) {
                                                descuentoInput = ""
                                                sellVm.setDiscountPercent(0)
                                            }
                                        }
                                    )
                                }
                                if (descuentoAdicionalActivo) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = descuentoInput,
                                        onValueChange = { value ->
                                            val sanitized = value.filter { it.isDigit() }.take(3)
                                            descuentoInput = sanitized
                                            val maxAllowed = (100 - ui.customerDiscountPercent).coerceAtLeast(0)
                                            val percent = sanitized.toIntOrNull()?.coerceIn(0, maxAllowed) ?: 0
                                            sellVm.setDiscountPercent(percent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Porcentaje de descuento (máx. ${(100 - ui.customerDiscountPercent).coerceAtLeast(0)}%)") },
                                        placeholder = { Text("Ej: 10") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        suffix = { Text("%") }
                                    )
                                    if (ui.discountPercent > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        ResumenRow(
                                            etiqueta = "Descuento (${ui.discountPercent}%)",
                                            valor = "-${currency.format(ui.manualDiscountAmount)}",
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                ResumenRow(
                                    etiqueta = "Total a cobrar",
                                    valor = currency.format(ui.total),
                                    resaltar = true
                                )
                                if (!ui.canCheckout) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Hay cantidades inválidas en el carrito.", color = Color.Red)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = onScanClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PointOfSale, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Scanear producto", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { showCancelPreSaleDialog = true },
                            enabled = ui.items.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Cancelar preventa")
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (!ui.canCheckout) {
                                    scope.launch {
                                        val first = ui.stockViolations.entries.firstOrNull()
                                        val msg = if (first != null) {
                                            "Stock insuficiente en al menos un producto (disponible: ${first.value})."
                                        } else {
                                            "Agregá productos para continuar."
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                } else {
                                    navController.navigate(Routes.PosCheckout.route)
                                }
                            },
                            enabled = ui.items.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        ) {
                            Text("Ir a cobrar")
                        }
                    }
                }
            }
        }
    }
}


/** Fila de ítem con +/− de a 1 y edición inline de cantidad (tap en el número). */
@Composable
fun CartItemRow(
    name: String,
    barcode: String?,
    unitPrice: Double,
    qty: Int,
    maxStock: Int,
    lineTotal: Double,
    canIncrease: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onQtyChange: (Int) -> Unit,
    currency: NumberFormat,
    showAtMaxHint: Boolean
) {
    var isEditingQty by remember { mutableStateOf(false) }
    var editQtyText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun applyEdit() {
        val newQty = editQtyText.toIntOrNull()?.coerceIn(1, maxStock.coerceAtLeast(1)) ?: qty
        if (newQty != qty) onQtyChange(newQty)
        isEditingQty = false
    }

    LaunchedEffect(isEditingQty) {
        if (isEditingQty) focusRequester.requestFocus()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!barcode.isNullOrBlank()) {
                        Text("Código: $barcode", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease, enabled = qty > 1) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }

                    val qtyColor = when {
                        qty > maxStock -> Color.Red
                        showAtMaxHint  -> Color(0xFFCC7700)
                        else           -> MaterialTheme.colorScheme.onSurface
                    }

                    if (isEditingQty) {
                        BasicTextField(
                            value = editQtyText,
                            onValueChange = { editQtyText = it.filter { c -> c.isDigit() }.take(3) },
                            modifier = Modifier
                                .widthIn(min = 48.dp, max = 72.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (!it.isFocused && isEditingQty) applyEdit() },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { applyEdit() }),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) { innerTextField() }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    editQtyText = qty.toString()
                                    isEditingQty = true
                                }
                                .border(
                                    width = 1.dp,
                                    color = when {
                                        qty > maxStock -> Color.Red
                                        showAtMaxHint  -> Color(0xFFCC7700)
                                        else           -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$qty",
                                style = MaterialTheme.typography.titleSmall,
                                color = qtyColor
                            )
                        }
                    }

                    IconButton(onClick = onIncrease, enabled = canIncrease) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currency.format(unitPrice), modifier = Modifier.padding(end = 12.dp))
                    Text(currency.format(lineTotal), style = MaterialTheme.typography.titleSmall)
                }
            }

            if (qty > maxStock) {
                Text("Cantidad supera el stock (máx: $maxStock).", color = Color.Red)
            } else if (showAtMaxHint) {
                Text("Llegaste al stock máximo disponible.", color = Color(0xFFCC7700))
            }
        }
    }
}

@Composable
private fun ResumenRow(
    etiqueta: String,
    valor: String,
    resaltar: Boolean = false,
    color: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = etiqueta,
            style = if (resaltar) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
        Text(
            text = valor,
            style = if (resaltar) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color ?: if (resaltar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PorcentajeControl(
    titulo: String,
    valor: Int,
    onValorChange: (Int) -> Unit,
    descripcion: String,
    colorDescripcion: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium)
            Text("$valor%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = valor.toFloat(),
            onValueChange = { onValorChange(it.roundToInt()) },
            valueRange = 0f..50f,
            steps = 49,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )
        Text(descripcion, color = colorDescripcion, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VariantSelectorDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var selectedSize by remember(product) { mutableStateOf(product.sizes.firstOrNull().orEmpty()) }
    val colorOptions = remember(product) {
        product.color
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
    }
    var selectedColor by remember(product) { mutableStateOf(colorOptions.firstOrNull().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar variante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name, style = MaterialTheme.typography.bodyMedium)
                if (product.sizes.isNotEmpty()) {
                    Text("Talle", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        product.sizes.forEach { size ->
                            OutlinedButton(onClick = { selectedSize = size }) {
                                Text(if (selectedSize == size) "✓ $size" else size)
                            }
                        }
                    }
                }
                if (colorOptions.isNotEmpty()) {
                    Text("Color", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        colorOptions.forEach { color ->
                            OutlinedButton(onClick = { selectedColor = color }) {
                                Text(if (selectedColor == color) "✓ $color" else color)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
