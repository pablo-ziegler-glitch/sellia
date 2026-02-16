package com.example.selliaapp.ui.screens.manage

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.repository.MarketingSettings
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ProductViewModel
import com.example.selliaapp.viewmodel.MarketingConfigViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.NumberFormat
import java.nio.charset.StandardCharsets
import java.util.Locale

private enum class QrAudience { PUBLIC, OWNER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductQrScreen(
    onBack: () -> Unit,
    vm: ProductViewModel = hiltViewModel(),
    marketingVm: MarketingConfigViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsStateWithLifecycle(initialValue = emptyList())
    val marketingSettings by marketingVm.settings.collectAsStateWithLifecycle(
        initialValue = MarketingSettings()
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var qrAudience by remember { mutableStateOf(QrAudience.PUBLIC) }
    var includePrices by remember { mutableStateOf(true) }
    var skuQuery by remember { mutableStateOf("") }
    var previewProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val filteredProducts = remember(products, skuQuery) {
        val normalizedQuery = skuQuery.trim()
        if (normalizedQuery.isBlank()) {
            products
        } else {
            products.filter { product ->
                resolveSkuValue(product).contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "AR")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    fun resolveQrValue(product: ProductEntity): String {
        val queryValue = resolveSkuValue(product)
        val baseUrl = marketingSettings.publicStoreUrl.trim().trimEnd('/')

        if (baseUrl.isNotBlank()) {
            val encoded = URLEncoder.encode(queryValue, StandardCharsets.UTF_8.name())
            val separator = if (baseUrl.contains("?")) "&" else "?"
            val mode = if (qrAudience == QrAudience.OWNER) "owner" else "public"
            return "$baseUrl$separator" + "q=$encoded&mode=$mode"
        }

        return if (qrAudience == QrAudience.OWNER) {
            "sellia://product?q=" + URLEncoder.encode(queryValue, StandardCharsets.UTF_8.name())
        } else {
            queryValue
        }
    }




    fun exportProducts(items: List<ProductEntity>, fileName: String) {
        exportQrPdf(
            context = context,
            items = items,
            fileName = fileName,
            includePrices = includePrices,
            currencyFormatter = currencyFormatter,
            resolveQrValue = ::resolveQrValue,
            resolveSkuValue = ::resolveSkuValue
        )
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Códigos QR",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch { exportProducts(products, "qr_todos") }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar todos")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Formato de descarga: etiqueta 30mm x 15mm (SKU más legible y QR más chico/alineado hacia el margen derecho)."
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = includePrices,
                    onCheckedChange = { includePrices = it }
                )
                Text("Incluir precio lista y efectivo debajo del SKU")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = qrAudience == QrAudience.PUBLIC,
                    onClick = { qrAudience = QrAudience.PUBLIC },
                    label = { Text("QR público") }
                )
                FilterChip(
                    selected = qrAudience == QrAudience.OWNER,
                    onClick = { qrAudience = QrAudience.OWNER },
                    label = { Text("QR interno") }
                )
            }
            OutlinedTextField(
                value = skuQuery,
                onValueChange = { skuQuery = it },
                label = { Text("Buscar por código SKU") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seleccionados: ${selectedIds.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedIds = filteredProducts.map { it.id }.toSet() }) {
                        Text("Seleccionar todo")
                    }
                    TextButton(onClick = { selectedIds = emptySet() }) {
                        Text("Limpiar")
                    }
                }
            }
            val selectedProducts = filteredProducts.filter { selectedIds.contains(it.id) }
            Button(
                onClick = {
                    scope.launch { exportProducts(selectedProducts, "qr_seleccionados") }
                },
                enabled = selectedProducts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Descargar seleccionados")
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(product.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + product.id
                                    } else {
                                        selectedIds - product.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(resolveSkuValue(product))
                                Text(resolveQrValue(product))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { previewProduct = product }) {
                                Icon(Icons.Default.QrCode, contentDescription = "Ver QR")
                            }
                            IconButton(onClick = {
                                scope.launch { exportProducts(listOf(product), "qr_${product.id}") }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Descargar")
                            }
                        }
                    }
                }
            }
        }
    }

    previewProduct?.let { product ->
        val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 220.dp.roundToPx() }
        val bitmap = remember(product) { generateQrBitmap(resolveQrValue(product), sizePx) }
        AlertDialog(
            onDismissRequest = { previewProduct = null },
            title = { Text(resolveSkuValue(product)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR del producto",
                        modifier = Modifier.size(220.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(resolveQrValue(product))
                }
            },
            confirmButton = {
                TextButton(onClick = { previewProduct = null }) { Text("Cerrar") }
            }
        )
    }
}
