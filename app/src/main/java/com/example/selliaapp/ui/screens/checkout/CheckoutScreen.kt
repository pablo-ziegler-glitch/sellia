package com.example.selliaapp.ui.screens.checkout

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.selliaapp.ui.navigation.Routes
import com.example.selliaapp.ui.navigation.Routes.SellRoutes.SELL_FLOW_ROUTE
import com.example.selliaapp.ui.state.CustomerSummaryUi
import com.example.selliaapp.ui.state.OrderType
import com.example.selliaapp.ui.state.PaymentMethod
import com.example.selliaapp.viewmodel.CustomersViewModel
import com.example.selliaapp.viewmodel.SellViewModel
import com.example.selliaapp.viewmodel.checkout.CheckoutViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    navController: NavController,
    onCancel: () -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val parentEntry = remember(navBackStackEntry) {
        navController.getBackStackEntry(SELL_FLOW_ROUTE)
    }

    val vm: SellViewModel = hiltViewModel(parentEntry)
    val customersVm: CustomersViewModel = hiltViewModel()
    val paymentVm: CheckoutViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val customers by customersVm.customers.collectAsState(initial = emptyList())
    val paymentState by paymentVm.paymentState.collectAsStateWithLifecycle()
    val moneda = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/uuuu") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var customerDiscountInput by remember { mutableStateOf("") }
    var showCustomerValidationDialog by remember { mutableStateOf(false) }
    var pendingCheckoutAction by remember { mutableStateOf(PendingCheckoutAction.CONFIRM_SALE) }

    LaunchedEffect(state.customerDiscountPercent) {
        customerDiscountInput = if (state.customerDiscountPercent == 0) "" else state.customerDiscountPercent.toString()
    }

    LaunchedEffect(paymentState.initPoint) {
        val initPoint = paymentState.initPoint
        if (!initPoint.isNullOrBlank()) {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, Uri.parse(initPoint))
            paymentVm.consumeInitPoint()
        }
    }

    LaunchedEffect(paymentState.errorMessage) {
        val message = paymentState.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            paymentVm.clearPaymentError()
        }
    }


    LaunchedEffect(paymentState.fallbackPaymentMethod) {
        when (paymentState.fallbackPaymentMethod?.trim()?.uppercase()) {
            "TRANSFERENCIA" -> {
                vm.updatePaymentMethod(PaymentMethod.TRANSFERENCIA)
                paymentVm.consumeFallbackPaymentMethod()
            }
            "EFECTIVO" -> {
                vm.updatePaymentMethod(PaymentMethod.EFECTIVO)
                paymentVm.consumeFallbackPaymentMethod()
            }
            "LISTA" -> {
                vm.updatePaymentMethod(PaymentMethod.LISTA)
                paymentVm.consumeFallbackPaymentMethod()
            }
            else -> Unit
        }
    }


    fun proceedWithResolvedCustomer(customerId: Long?, customerName: String?) {
        if (pendingCheckoutAction == PendingCheckoutAction.MERCADO_PAGO) {
            paymentVm.createPaymentPreference(
                amount = state.total,
                items = state.items,
                customerName = customerName
            )
            return
        }
        isProcessing = true
        vm.placeOrder(
            customerId = customerId,
            customerName = customerName,
            onSuccess = { resultado ->
                isProcessing = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Venta #${resultado.invoiceNumber}: ${moneda.format(resultado.total)} (${resultado.paymentMethod.nombreLegible()})"
                    )
                }
                navController.navigate(
                    Routes.PosSuccess.build(
                        invoiceId = resultado.invoiceId,
                        total = resultado.total,
                        method = resultado.paymentMethod.nombreLegible()
                    )
                ) {
                    popUpTo(Routes.Pos.route) { inclusive = false }
                }
            },
            onError = { error ->
                isProcessing = false
                scope.launch {
                    val mensaje = error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo confirmar la venta."
                    snackbarHostState.showSnackbar(mensaje)
                }
            }
        )
    }


    if (showCustomerValidationDialog) {
        CustomerValidationDialog(
            customers = customers,
            onDismiss = { showCustomerValidationDialog = false },
            onPickExisting = { customer ->
                vm.setCustomer(customer.id, customer.name)
                showCustomerValidationDialog = false
                proceedWithResolvedCustomer(customer.id.toLong(), customer.name)
            },
            onCreateQuick = { quickName ->
                vm.ensureQuickCustomer(
                    rawName = quickName,
                    onSuccess = { customerId, customerName ->
                        showCustomerValidationDialog = false
                        proceedWithResolvedCustomer(customerId.toLong(), customerName)
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err.message ?: "No se pudo guardar el cliente rápido.") }
                    }
                )
            },
            onContinueWithoutCustomer = {
                showCustomerValidationDialog = false
                proceedWithResolvedCustomer(null, "Consumidor final")
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Confirmar cobro",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (state.items.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No hay productos en el carrito.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onCancel) {
                            Text("Volver a vender")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.productId }) { item ->
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name ?: "-", style = MaterialTheme.typography.titleMedium)
                                        if (!item.barcode.isNullOrBlank()) {
                                            Text(
                                                text = "Código: ${item.barcode}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${item.qty} × ${moneda.format(item.unitPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = moneda.format(item.lineTotal),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                    item {
                        if (!state.selectedCustomerName.isNullOrBlank()) {
                            CustomerSummaryCard(
                                customerName = state.selectedCustomerName ?: "",
                                summary = state.customerSummary,
                                moneda = moneda,
                                dateFormatter = dateFormatter
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    item {
                        if (!state.selectedCustomerName.isNullOrBlank()) {
                            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Descuento por cliente", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = customerDiscountInput,
                                        onValueChange = { value ->
                                            val sanitized = value.filter { it.isDigit() }.take(3)
                                            customerDiscountInput = sanitized
                                            val percent = sanitized.toIntOrNull()?.coerceIn(0, 100) ?: 0
                                            vm.setCustomerDiscountPercent(percent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Porcentaje de descuento") },
                                        placeholder = { Text("Ej: 10") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Definí el descuento a discreción para este cliente.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    item {
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Detalle del cobro", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                ResumenCheckoutFila("Subtotal", moneda.format(state.subtotal))
                                if (state.customerDiscountPercent > 0) {
                                    ResumenCheckoutFila(
                                        etiqueta = "Descuento cliente (${state.customerDiscountPercent}%)",
                                        valor = "-${moneda.format(state.customerDiscountAmount)}",
                                        colorValor = Color(0xFF2E7D32)
                                    )
                                }
                                if (state.surchargePercent > 0) {
                                    ResumenCheckoutFila(
                                        etiqueta = "Recargo (${state.surchargePercent}%)",
                                        valor = "+${moneda.format(state.surchargeAmount)}",
                                        colorValor = Color(0xFFB71C1C)
                                    )
                                }
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                ResumenCheckoutFila(
                                    etiqueta = "Total a cobrar",
                                    valor = moneda.format(state.total),
                                    resaltar = true
                                )
                                if (state.stockViolations.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Revisá el stock antes de cobrar.",
                                        color = Color(0xFFB71C1C),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Tipo de pedido", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TipoPedidoSelector(
                    seleccionado = state.orderType,
                    onSeleccion = { vm.updateOrderType(it) }
                )

                Spacer(Modifier.height(16.dp))

                Text("Método de pago", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                MetodoPagoSelector(
                    seleccionado = state.paymentMethod,
                    onSeleccion = { vm.updatePaymentMethod(it) }
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.paymentNotes,
                    onValueChange = { vm.updatePaymentNotes(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notas del cobro") },
                    placeholder = { Text("Agregá número de comprobante, referencia o comentarios") },
                    keyboardOptions = KeyboardOptions.Default,
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!state.canCheckout) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No podés cobrar hasta corregir el stock.")
                        }
                    } else if (state.selectedCustomerName.isNullOrBlank()) {
                        pendingCheckoutAction = PendingCheckoutAction.MERCADO_PAGO
                        showCustomerValidationDialog = true
                    } else {
                        paymentVm.createPaymentPreference(
                            amount = state.total,
                            items = state.items,
                            customerName = state.selectedCustomerName
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canCheckout && !paymentState.isLoading && !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ) {
                if (paymentState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                } else {
                    Text("Pagar con Mercado Pago")
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (!state.canCheckout) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No podés cobrar hasta corregir el stock.")
                            }
                        } else if (state.selectedCustomerName.isNullOrBlank()) {
                            pendingCheckoutAction = PendingCheckoutAction.CONFIRM_SALE
                            showCustomerValidationDialog = true
                        } else {
                            isProcessing = true
                            vm.placeOrder(
                                onSuccess = { resultado ->
                                    isProcessing = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Venta #${resultado.invoiceNumber}: ${moneda.format(resultado.total)} (${resultado.paymentMethod.nombreLegible()})"
                                        )
                                    }
                                    navController.navigate(
                                        Routes.PosSuccess.build(
                                            invoiceId = resultado.invoiceId,
                                            total = resultado.total,
                                            method = resultado.paymentMethod.nombreLegible()
                                        )
                                    ) {
                                        popUpTo(Routes.Pos.route) { inclusive = false }
                                    }
                                },
                                onError = { error ->
                                    isProcessing = false
                                    scope.launch {
                                        val mensaje = error.message?.takeIf { it.isNotBlank() }
                                            ?: "No se pudo confirmar la venta."
                                        snackbarHostState.showSnackbar(mensaje)
                                    }
                                }
                            )
                        }
                    },
                    enabled = state.items.isNotEmpty() && !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Confirmar cobro")
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomerValidationDialog(
    customers: List<com.example.selliaapp.data.local.entity.CustomerEntity>,
    onDismiss: () -> Unit,
    onPickExisting: (com.example.selliaapp.data.local.entity.CustomerEntity) -> Unit,
    onCreateQuick: (String) -> Unit,
    onContinueWithoutCustomer: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var quickCustomerName by remember { mutableStateOf("") }
    val filteredCustomers = remember(customers, query) {
        if (query.isBlank()) customers.take(10)
        else customers.filter { customer ->
            customer.name.contains(query, ignoreCase = true) ||
                (customer.email ?: "").contains(query, ignoreCase = true) ||
                (customer.phone ?: "").contains(query, ignoreCase = true)
        }.take(10)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cliente para cobrar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Antes de cerrar la venta podés buscar un cliente existente, cargar uno rápido o seguir como Consumidor final."
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar cliente existente") },
                    placeholder = { Text("Nombre, email o teléfono") },
                    singleLine = true
                )
                if (filteredCustomers.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            TextButton(onClick = { onPickExisting(customer) }) {
                                Text(customer.name)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = quickCustomerName,
                    onValueChange = { quickCustomerName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cliente rápido") },
                    placeholder = { Text("Ej: Juan Pérez") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreateQuick(quickCustomerName) }) {
                Text("Guardar rápido y continuar")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onContinueWithoutCustomer) {
                    Text("Continuar como Consumidor final")
                }
                TextButton(onClick = onDismiss) { Text("Volver") }
            }
        }
    )
}

private enum class PendingCheckoutAction {
    CONFIRM_SALE,
    MERCADO_PAGO
}

@Composable
private fun ResumenCheckoutFila(
    etiqueta: String,
    valor: String,
    resaltar: Boolean = false,
    colorValor: Color = MaterialTheme.colorScheme.onSurface
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
            color = if (resaltar) MaterialTheme.colorScheme.primary else colorValor
        )
    }
}

@Composable
private fun MetodoPagoSelector(
    seleccionado: PaymentMethod,
    onSeleccion: (PaymentMethod) -> Unit
) {
    val opciones = listOf(
        PaymentMethod.LISTA to "Lista",
        PaymentMethod.EFECTIVO to "Efectivo",
        PaymentMethod.TRANSFERENCIA to "Transferencia"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        opciones.forEach { (metodo, etiqueta) ->
            val activo = metodo == seleccionado
            OutlinedButton(
                onClick = { onSeleccion(metodo) },
                modifier = Modifier.weight(1f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activo) Color.Transparent else MaterialTheme.colorScheme.outline
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (activo) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (activo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(etiqueta)
            }
        }
    }
}

@Composable
private fun TipoPedidoSelector(
    seleccionado: OrderType,
    onSeleccion: (OrderType) -> Unit
) {
    val opciones = listOf(
        OrderType.INMEDIATA to "Inmediata",
        OrderType.RESERVA to "Reserva",
        OrderType.ENVIO to "Envío"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        opciones.forEach { (tipo, etiqueta) ->
            val activo = tipo == seleccionado
            OutlinedButton(
                onClick = { onSeleccion(tipo) },
                modifier = Modifier.weight(1f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activo) Color.Transparent else MaterialTheme.colorScheme.outline
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (activo) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (activo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(etiqueta)
            }
        }
    }
}

@Composable
private fun CustomerSummaryCard(
    customerName: String,
    summary: CustomerSummaryUi?,
    moneda: NumberFormat,
    dateFormatter: DateTimeFormatter
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Cliente", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(customerName, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (summary == null) {
                Text(
                    text = "Cargando historial...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Card
            }
            ResumenCheckoutFila(
                etiqueta = "Saldo histórico",
                valor = moneda.format(summary.totalSpent)
            )
            ResumenCheckoutFila(
                etiqueta = "Compras registradas",
                valor = summary.purchaseCount.toString()
            )
            ResumenCheckoutFila(
                etiqueta = "Última compra",
                valor = summary.lastPurchaseMillis?.let {
                    dateFormatter.format(
                        Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    )
                } ?: "-"
            )
        }
    }
}

private fun PaymentMethod.nombreLegible(): String = when (this) {
    PaymentMethod.LISTA -> "Lista"
    PaymentMethod.EFECTIVO -> "Efectivo"
    PaymentMethod.TRANSFERENCIA -> "Transferencia"
}
