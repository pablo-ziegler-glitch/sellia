package com.example.selliaapp.ui.screens.public

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.selliaapp.R
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ProductViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProductCatalogScreen(
    onBack: () -> Unit,
    onProductSelected: (Int) -> Unit,
    vm: ProductViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsStateWithLifecycle(emptyList())
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    Scaffold(
        topBar = { BackTopAppBar(title = "Catálogo público", onBack = onBack) }
    ) { padding ->
        if (products.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Todavía no hay productos publicados.",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Apenas cargues productos se mostrarán acá.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    PublicCatalogItem(
                        product = product,
                        currency = currency,
                        onClick = { onProductSelected(product.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicCatalogItem(
    product: ProductEntity,
    currency: NumberFormat,
    onClick: () -> Unit
) {
    val imageModel: Any = product.imageUrls.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: product.imageUrl?.takeIf { it.isNotBlank() }
        ?: R.drawable.ic_sell

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Imagen del producto ${product.name}",
                modifier = Modifier
                    .size(72.dp)
                    .padding(2.dp),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_sell),
                error = painterResource(id = R.drawable.ic_sell)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "PRECIO LISTA: ${formatPrice(product.listPrice, currency)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "PRECIO EFECTIVO: ${formatPrice(product.cashPrice, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "PRECIO TRANSFERENCIA: ${formatPrice(product.transferPrice, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!product.brand.isNullOrBlank()) {
                    Text(
                        text = "Marca: ${product.brand}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!product.category.isNullOrBlank()) {
                    Text(
                        text = "Subcategoría: ${product.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = ">",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatPrice(value: Double?, currency: NumberFormat): String =
    value?.let { currency.format(it) } ?: "-"
