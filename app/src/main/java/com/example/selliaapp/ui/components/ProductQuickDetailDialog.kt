package com.example.selliaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.selliaapp.R
import com.example.selliaapp.data.local.entity.ProductEntity
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductQuickDetailDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    val images: List<Any> = product.imageUrls.takeIf { it.isNotEmpty() }
        ?: product.imageUrl?.takeIf { it.isNotBlank() }?.let { listOf(it) }
        ?: listOf(R.drawable.ic_sell)
    val pagerState = rememberPagerState(pageCount = { images.size })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) { page ->
                    AsyncImage(
                        model = images[page],
                        contentDescription = "Imagen ${page + 1} de ${product.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.ic_sell),
                        error = painterResource(id = R.drawable.ic_sell)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(images.size) { index ->
                        Spacer(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .background(
                                    color = if (pagerState.currentPage == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }

                PriceLine("Lista", product.listPrice, currency)
                PriceLine("Efectivo", product.cashPrice ?: product.listPrice, currency)
                PriceLine("Transferencia", product.transferPrice ?: product.listPrice, currency)
                PriceLine("ML", product.mlPrice, currency)
                PriceLine("ML 3C", product.ml3cPrice, currency)
                PriceLine("ML 6C", product.ml6cPrice, currency)

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Stock: ${product.quantity}")
                        product.code?.takeIf { it.isNotBlank() }?.let { Text("Código: $it") }
                        product.barcode?.takeIf { it.isNotBlank() }?.let { Text("Barcode: $it") }
                        product.parentCategory?.takeIf { it.isNotBlank() }?.let { Text("Categoría: $it") }
                        product.category?.takeIf { it.isNotBlank() }?.let { Text("Subcategoría: $it") }
                        product.color?.takeIf { it.isNotBlank() }?.let { Text("Color: $it") }
                        if (product.sizes.isNotEmpty()) {
                            Text("Talles: ${product.sizes.joinToString()}")
                        }
                        product.description?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.size(2.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun PriceLine(label: String, value: Double?, currency: NumberFormat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value?.let { currency.format(it) } ?: "-", style = MaterialTheme.typography.bodyMedium)
    }
}
