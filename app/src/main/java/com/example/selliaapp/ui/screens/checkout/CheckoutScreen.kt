package com.example.selliaapp.ui.screens.checkout

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.selliaapp.ui.navigation.Routes.SellRoutes.SELL_FLOW_ROUTE
import com.example.selliaapp.ui.state.PaymentMethod
import com.example.selliaapp.viewmodel.SellViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
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
    val state by vm.state.collectAsStateWithLifecycle()
    val moneda = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

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
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Detalle del cobro", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                ResumenCheckoutFila("Subtotal", moneda.format(state.subtotal))
                                if (state.discountPercent > 0) {
                                    ResumenCheckoutFila(
                                        etiqueta = "Descuento (${state.discountPercent}%)",
                                        valor = "-${moneda.format(state.discountAmount)}",
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
                                Divider(Modifier.padding(vertical = 12.dp))
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
                        } else {
                            isProcessing = true
                            vm.placeOrder(
                                onSuccess = { resultado ->
                                    isProcessing = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Venta #${resultado.invoiceNumber}: ${moneda.format(resultado.total)} (${resultado.paymentMethod.nombreLegible()})"
                                        )
                                        onCancel()
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
        PaymentMethod.EFECTIVO to "Efectivo",
        PaymentMethod.TARJETA to "Tarjeta",
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

private fun PaymentMethod.nombreLegible(): String = when (this) {
    PaymentMethod.EFECTIVO -> "Efectivo"
    PaymentMethod.TARJETA -> "Tarjeta"
    PaymentMethod.TRANSFERENCIA -> "Transferencia"
}
