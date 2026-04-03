package com.example.selliaapp.ui.screens.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.selliaapp.R
import com.example.selliaapp.repository.PublicCatalogProduct
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.StoreBanner
import com.example.selliaapp.viewmodel.CustomerHomeViewModel
import com.example.selliaapp.viewmodel.StoreDetailViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StoreDetailScreen(
    tenantId: String,
    tenantName: String,
    onBack: () -> Unit,
    onOpenProduct: (Int) -> Unit = {},
    customerHomeVm: CustomerHomeViewModel = hiltViewModel(),
    storeDetailVm: StoreDetailViewModel = hiltViewModel()
) {
    val state by customerHomeVm.uiState.collectAsStateWithLifecycle()
    val detailState by storeDetailVm.uiState.collectAsStateWithLifecycle()
    val config = state.selectedStoreConfig

    LaunchedEffect(tenantId, state.stores) {
        val store = state.stores.firstOrNull { it.id == tenantId }
        if (store != null && state.selectedStore?.id != tenantId) {
            customerHomeVm.selectStore(store)
        }
    }

    LaunchedEffect(tenantId) {
        storeDetailVm.loadCatalog(tenantId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopAppBar(title = tenantName, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config != null && (config.bannerTitle.isNotBlank() || config.storeName.isNotBlank())) {
                item {
                    StoreBanner(
                        storeName = config.storeName.ifBlank { tenantName },
                        tagline = config.tagline,
                        bannerTitle = config.bannerTitle,
                        bannerSubtitle = config.bannerSubtitle,
                        logoUrl = config.logoUrl,
                        bannerImageUrl = config.bannerImageUrl
                    )
                }
            }

            if (config != null && (config.contactWhatsapp.isNotBlank() || config.contactInstagram.isNotBlank() || config.contactAddress.isNotBlank())) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Contacto",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (config.contactWhatsapp.isNotBlank()) {
                                Text(
                                    text = "WhatsApp: ${config.contactWhatsapp}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (config.contactInstagram.isNotBlank()) {
                                Text(
                                    text = "Instagram: ${config.contactInstagram}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (config.contactAddress.isNotBlank()) {
                                Text(
                                    text = config.contactAddress,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Productos de esta tienda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                detailState.isLoadingCatalog -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                !detailState.catalogErrorMessage.isNullOrBlank() -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = detailState.catalogErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { storeDetailVm.retryCatalog() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }

                detailState.catalog.isEmpty() -> {
                    item {
                        Text(
                            text = "Esta tienda todavía no publicó productos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }

                else -> {
                    items(detailState.catalog, key = { it.id }) { product ->
                        StoreCatalogProductItem(
                            product = product,
                            onOpenProduct = onOpenProduct
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreCatalogProductItem(
    product: PublicCatalogProduct,
    onOpenProduct: (Int) -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val imageModel: Any = product.imageUrl?.takeIf { it.isNotBlank() } ?: R.drawable.ic_sell
    val priceToShow = product.listPrice ?: product.cashPrice ?: product.transferPrice

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
            AsyncImage(
                model = imageModel,
                contentDescription = "Imagen del producto ${product.name}",
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_sell),
                error = painterResource(id = R.drawable.ic_sell)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = priceToShow?.let(currency::format) ?: "Precio no disponible",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { onOpenProduct(product.id) }) {
                    Text("Ver detalle")
                }
            }
        }
    }
}
