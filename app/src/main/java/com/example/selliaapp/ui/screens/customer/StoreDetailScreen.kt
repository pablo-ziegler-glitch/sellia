package com.example.selliaapp.ui.screens.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.StoreBanner
import com.example.selliaapp.viewmodel.CustomerHomeViewModel

@Composable
fun StoreDetailScreen(
    tenantId: String,
    tenantName: String,
    onBack: () -> Unit,
    onOpenProduct: (Int) -> Unit = {},
    customerHomeVm: CustomerHomeViewModel = hiltViewModel()
) {
    val state by customerHomeVm.uiState.collectAsStateWithLifecycle()
    val config = state.selectedStoreConfig

    LaunchedEffect(tenantId) {
        val store = state.stores.firstOrNull { it.id == tenantId }
        if (store != null) {
            customerHomeVm.selectStore(store)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopAppBar(title = tenantName, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Store banner
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

            // Contact info
            if (config != null && config.contactWhatsapp.isNotBlank()) {
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

            // Placeholder for products
            item {
                Text(
                    text = "Productos de esta tienda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Text(
                    text = "Los productos de esta tienda se mostrarán aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        }
    }
}
