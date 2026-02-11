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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.selliaapp.domain.product.ProductFilterParams
import com.example.selliaapp.domain.product.ProductSortOption
import com.example.selliaapp.domain.product.filterAndSortProducts
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

    var query by remember { mutableStateOf("") }
    var parentCategory by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProductSortOption.UPDATED_DESC) }
    var sortExpanded by remember { mutableStateOf(false) }

    val filteredProducts = remember(
        products,
        query,
        parentCategory,
        category,
        color,
        size,
        minPrice,
        maxPrice,
        sort
    ) {
        filterAndSortProducts(
            products,
            ProductFilterParams(
                query = query,
                parentCategory = parentCategory.ifBlank { null },
                category = category.ifBlank { null },
                color = color.ifBlank { null },
                size = size.ifBlank { null },
                minPrice = minPrice.toDoubleOrNull(),
                maxPrice = maxPrice.toDoubleOrNull(),
                sort = sort
            )
        )
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Catálogo público", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por cualquier campo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = parentCategory,
                        onValueChange = { parentCategory = it },
                        label = { Text("Categoría") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Subcategoría") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text("Color") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = { Text("Talle") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minPrice,
                        onValueChange = { minPrice = it },
                        label = { Text("Precio mín") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxPrice,
                        onValueChange = { maxPrice = it },
                        label = { Text("Precio máx") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { sortExpanded = true }, modifier = Modifier.weight(1f)) {
                        Text("Orden: ${sort.label}")
                    }
                    Button(onClick = {
                        query = ""
                        parentCategory = ""
                        category = ""
                        color = ""
                        size = ""
                        minPrice = ""
                        maxPrice = ""
                        sort = ProductSortOption.UPDATED_DESC
                    }, modifier = Modifier.weight(1f)) {
                        Text("Limpiar")
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        ProductSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sort = option
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
                Text("Resultados: ${filteredProducts.size}")
            }

            if (filteredProducts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No hay productos que coincidan con los filtros.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Probá ampliar la búsqueda o limpiar filtros.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
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
                if (!product.parentCategory.isNullOrBlank()) {
                    Text(
                        text = "Categoría: ${product.parentCategory}",
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
