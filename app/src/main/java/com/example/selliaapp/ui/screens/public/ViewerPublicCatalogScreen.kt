package com.example.selliaapp.ui.screens.public

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Alignment
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.selliaapp.R
import com.example.selliaapp.repository.PublicCatalogProduct
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ViewerCatalogViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerPublicCatalogScreen(
    onBack: () -> Unit,
    viewModel: ViewerCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var followExpanded by remember { mutableStateOf(false) }
    var selectedStoreToFollow by remember { mutableStateOf<String?>(null) }
    var selectedCatalogExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var detailProduct by remember { mutableStateOf<PublicCatalogProduct?>(null) }
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }

    val followStoreName = state.availableStores.firstOrNull { it.id == selectedStoreToFollow }?.name.orEmpty()
    val selectedCatalogStore = state.followedStores.firstOrNull { it.id == state.selectedStoreId }
    val normalizedQuery = searchQuery.trim()
    val filteredProducts = remember(state.products, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            state.products
        } else {
            val query = normalizedQuery.lowercase()
            state.products.filter { product ->
                product.name.lowercase().contains(query) ||
                    product.category.orEmpty().lowercase().contains(query) ||
                    product.subcategory.orEmpty().lowercase().contains(query)
            }
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Catálogo público", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Seleccioná la tienda que querés visualizar",
                style = MaterialTheme.typography.titleMedium
            )

            ExposedDropdownMenuBox(
                expanded = selectedCatalogExpanded,
                onExpandedChange = { selectedCatalogExpanded = !selectedCatalogExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCatalogStore?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tienda preseleccionada") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(selectedCatalogExpanded) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                    enabled = state.followedStores.isNotEmpty() && !state.isLoading
                )
                DropdownMenu(
                    expanded = selectedCatalogExpanded,
                    onDismissRequest = { selectedCatalogExpanded = false }
                ) {
                    state.followedStores.forEach { store ->
                        DropdownMenuItem(
                            text = { Text(store.name) },
                            onClick = {
                                viewModel.selectStore(store.id)
                                selectedCatalogExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por nombre o categoría") },
                singleLine = true,
                enabled = state.selectedStoreId != null && !state.isLoading
            )

            Text(
                text = "Resultados: ${filteredProducts.size}",
                style = MaterialTheme.typography.bodyMedium
            )

            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.selectedStoreId == null -> {
                    EmptyStateMessage(
                        title = "Elegí una tienda para ver su catálogo",
                        description = "Cuando selecciones una tienda adherida, verás sus productos aquí."
                    )
                }

                state.products.isEmpty() -> {
                    EmptyStateMessage(
                        title = "Esta tienda no tiene productos publicados",
                        description = "La tienda ${selectedCatalogStore?.name.orEmpty()} todavía no cargó productos en su catálogo público."
                    )
                }

                filteredProducts.isEmpty() -> {
                    EmptyStateMessage(
                        title = "No encontramos resultados para \"$normalizedQuery\"",
                        description = "Probá con otro nombre o categoría."
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ViewerCatalogProductItem(
                                product = product,
                                currencyFormatter = currencyFormatter,
                                onSeeDetail = { detailProduct = product }
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Seguir o adherirse a tienda", style = MaterialTheme.typography.titleSmall)
                    ExposedDropdownMenuBox(
                        expanded = followExpanded,
                        onExpandedChange = { followExpanded = !followExpanded }
                    ) {
                        OutlinedTextField(
                            value = followStoreName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tienda disponible") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(followExpanded) },
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                            enabled = state.hasStoresToFollow && !state.isLoadingStores
                        )
                        DropdownMenu(
                            expanded = followExpanded,
                            onDismissRequest = { followExpanded = false }
                        ) {
                            state.availableStores.forEach { store ->
                                DropdownMenuItem(
                                    text = { Text(store.name) },
                                    onClick = {
                                        selectedStoreToFollow = store.id
                                        followExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { selectedStoreToFollow?.let(viewModel::followSelectedStore) },
                        enabled = !selectedStoreToFollow.isNullOrBlank() && !state.isFollowingStore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isFollowingStore) "Guardando..." else "Seguir tienda")
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!state.hasFollowedStores) {
                Text(
                    text = "Todavía no seguís ninguna tienda. Podés crear tu cuenta y adherirte después.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Productos públicos disponibles: ${state.products.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    detailProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { detailProduct = null },
            title = { Text(product.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Precio lista: ${formatViewerPrice(product.listPrice, currencyFormatter)}")
                    Text("Precio efectivo: ${formatViewerPrice(product.cashPrice, currencyFormatter)}")
                    Text("Precio transferencia: ${formatViewerPrice(product.transferPrice, currencyFormatter)}")
                    if (!product.category.isNullOrBlank()) {
                        Text("Categoría: ${product.category}")
                    }
                    if (!product.subcategory.isNullOrBlank()) {
                        Text("Subcategoría: ${product.subcategory}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailProduct = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun ViewerCatalogProductItem(
    product: PublicCatalogProduct,
    currencyFormatter: NumberFormat,
    onSeeDetail: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageUrl = product.imageUrl?.takeIf { it.isNotBlank() }
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Imagen de ${product.name}",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_sell),
                    error = painterResource(id = R.drawable.ic_sell)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_sell),
                    contentDescription = "Sin imagen",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Precio: ${formatViewerPrice(product.listPrice, currencyFormatter)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!product.category.isNullOrBlank()) {
                    Text(
                        text = product.category.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(onClick = onSeeDetail) {
                Text("Ver detalle")
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatViewerPrice(price: Double?, formatter: NumberFormat): String =
    price?.let(formatter::format) ?: "-"
