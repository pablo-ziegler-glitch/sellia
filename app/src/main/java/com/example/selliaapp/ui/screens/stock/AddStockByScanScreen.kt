package com.example.selliaapp.ui.screens.stock


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.selliaapp.ui.components.QuantityInputDialog
import com.example.selliaapp.ui.navigation.Routes // [NUEVO] Import de rutas tipadas
import com.example.selliaapp.viewmodel.StockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [NUEVO]
 * Pantalla para "Agregar stock por escaneo".
 * Ajuste principal: navegación hacia AddProduct usando Routes.AddProduct.build(barcode = ...)
 * en lugar de la ruta legacy "addProduct?prefillBarcode=...".
 */
@Composable
fun AddStockByScanScreen(
    onOpenScanner: () -> Unit,
    navController: NavController,
    vm: StockViewModel = hiltViewModel()
) {
    val snackbar = remember { SnackbarHostState() }

    // Recupera el código escaneado desde el back stack (lo setea la pantalla del scanner).
    val scannedCodeState = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("scannedCode", "")
        ?.collectAsStateWithLifecycle()

    var showQtyDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var lastCode by remember { mutableStateOf<String?>(null) }

    // Cuando llega un código: decidimos si existe (abrir diálogo) o si vamos a alta.
    LaunchedEffect(scannedCodeState?.value) {
        val code = scannedCodeState?.value?.takeIf { it.isNotBlank() }
        if (!code.isNullOrBlank()) {
            // Limpiamos enseguida para evitar reprocesar al recomponer.
            navController.currentBackStackEntry?.savedStateHandle?.set("scannedCode", "")

            // Consultamos al VM: ¿existe producto con este barcode?
            val result = withContext(Dispatchers.IO) { vm.onScanBarcode(code) }

            result.errorMessage?.let { message ->
                snackbar.showSnackbar(message)
            }

            if (result.foundId != null) {
                // Producto existente -> pedimos cantidad a sumar.
                lastCode = code
                showQtyDialog = true
            } else {
                // No existe -> navegamos a ALTA con el código precargado usando Routes.
                val route = Routes.AddProduct.build(
                    prefillBarcode = result.prefillBarcode,
                    prefillName = result.name
                )
                navController.navigate(route)
            }
        }
    }

    // UI principal
    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbar) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Agregar stock por escaneo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenScanner) {
                        Text("Abrir scanner")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Apuntá al código. Si el producto existe te pediremos la cantidad; si no existe, se abrirá el alta con datos precargados.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Diálogo de cantidad (sólo si el producto existe y venimos del escaneo)
    if (showQtyDialog && lastCode != null) {
        QuantityInputDialog(
            title = "Cantidad a agregar",
            confirmText = "Agregar",
            cancelText = "Cancelar",
            initialValue = 1,
            onConfirm = { qty ->
                showQtyDialog = false
                lastCode?.let { code ->
                    vm.addStockByScan(
                        barcode = code,
                        qty = qty,
                        onSuccess = {
                            scope.launch {
                                snackbar.showSnackbar("Stock actualizado correctamente.")
                            }
                        },
                        onNotFound = {
                            // Si entre medio lo eliminaron: mandamos a alta con Routes.
                            val route = Routes.AddProduct.build(prefillBarcode = code)
                            navController.navigate(route)
                        },
                        onError = { error ->
                            scope.launch {
                                snackbar.showSnackbar(error.message ?: "No se pudo actualizar el stock por escaneo.")
                            }
                        }
                    )
                }
            },
            onCancel = { showQtyDialog = false },
        )
    }
}