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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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
    val images = remember(product) { listOfNotNull(product.imageUrl).ifEmpty { emptyList() } }

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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(images) { image ->
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.size(width = 220.dp, height = 160.dp)
                        ) {
                            AsyncImage(
                                model = image,
                                contentDescription = product.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                        onClick = { qty = (qty - 1).coerceAtLeast(1) },
                        enabled = safeMax > 0 && qty > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Quitar")
                    }
                    Text("$qty", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(
                        onClick = { qty = (qty + 1).coerceAtMost(safeMax.coerceAtLeast(1)) },
                        enabled = safeMax > 0 && qty < safeMax
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar")
                    }
                }
            }

            Button(
                onClick = { onAddToCart(qty) },
                enabled = safeMax > 0,
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
