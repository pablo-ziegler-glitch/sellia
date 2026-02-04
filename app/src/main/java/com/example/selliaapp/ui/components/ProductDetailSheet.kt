package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.selliaapp.data.local.entity.ProductEntity
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailSheet(
    product: ProductEntity,
    initialQty: Int,
    maxQty: Int,
    currency: NumberFormat,
    onAddToCart: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val safeMax = maxQty.coerceAtLeast(0)
    val startingQty = if (safeMax == 0) 0 else initialQty.coerceIn(1, safeMax)
    var qty by remember { mutableIntStateOf(startingQty) }
    var qtyText by remember { mutableStateOf(startingQty.toString()) }
    val images = remember(product) { product.imageUrls }

    LaunchedEffect(qtyText) {
        val parsed = qtyText.toIntOrNull() ?: 0
        qty = parsed.coerceAtLeast(0)
    }

    val tooHigh = safeMax > 0 && qty > safeMax

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (images.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { images.size })

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) { page ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = images[page],
                            contentDescription = "Imagen ${page + 1} de ${images.size} - ${product.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(images.size) { index ->
                        val color = if (pagerState.currentPage == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .background(color = color, shape = CircleShape)
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Sin imágenes", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PriceRow("Lista", product.listPrice ?: product.price, currency)
                PriceRow("Efectivo", product.cashPrice ?: product.listPrice ?: product.price, currency)
                PriceRow("Transferencia", product.transferPrice ?: product.listPrice ?: product.price, currency)
                PriceRow("ML", product.mlPrice, currency)
                PriceRow("ML 3C", product.ml3cPrice, currency)
                PriceRow("ML 6C", product.ml6cPrice, currency)
            }

            HorizontalDivider()

            Text(
                text = "Stock disponible: ${product.quantity}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = product.description?.takeIf { it.isNotBlank() } ?: "Sin descripción",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cantidad", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val newQty = (qty - 1).coerceAtLeast(1)
                            qty = newQty
                            qtyText = newQty.toString()
                        },
                        enabled = safeMax > 0 && qty > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Quitar")
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = qtyText,
                        onValueChange = { input -> qtyText = input.filter { it.isDigit() } },
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .width(96.dp),
                        singleLine = true,
                        isError = qty < 1 || tooHigh,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(
                        onClick = {
                            val newQty = if (safeMax > 0) (qty + 1).coerceAtMost(safeMax) else qty + 1
                            qty = newQty
                            qtyText = newQty.toString()
                        },
                        enabled = safeMax > 0 && qty < safeMax
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar")
                    }
                }
            }

            if (tooHigh) {
                Text(
                    text = "Excede el stock disponible (máx: $safeMax).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = { onAddToCart(qty) },
                enabled = safeMax > 0 && qty >= 1 && !tooHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar al carrito")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PriceRow(label: String, value: Double?, currency: NumberFormat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val text = value?.let { currency.format(it) } ?: "-"
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
