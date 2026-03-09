package com.example.selliaapp.ui.screens.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.selliaapp.repository.TenantSummary
import com.example.selliaapp.ui.components.AccountAvatarMenu
import com.example.selliaapp.ui.components.AccountSummary
import com.example.selliaapp.ui.components.DynamicBanner
import com.example.selliaapp.ui.components.NotificationBadge
import com.example.selliaapp.viewmodel.CustomerHomeViewModel
import com.example.selliaapp.viewmodel.NotificationViewModel

@Composable
fun CustomerHomeScreen(
    accountSummary: AccountSummary,
    onOpenPublicCatalog: () -> Unit,
    onScanPublicProduct: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenStore: (TenantSummary) -> Unit,
    onRequestStore: () -> Unit,
    onOpenNotifications: () -> Unit,
    customerHomeVm: CustomerHomeViewModel = hiltViewModel(),
    notificationVm: NotificationViewModel = hiltViewModel()
) {
    val state by customerHomeVm.uiState.collectAsStateWithLifecycle()
    val unreadCount by notificationVm.unreadCount.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                NotificationBadge(
                    unreadCount = unreadCount,
                    onClick = onOpenNotifications
                )
                AccountAvatarMenu(accountSummary = accountSummary)
            }
        }

        // Dynamic banner
        if (state.bannerMessages.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                DynamicBanner(messages = state.bannerMessages)
            }
        }

        // Section title
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "Tiendas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Loading state
        if (state.isLoading) {
            item(span = { GridItemSpan(2) }) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        // Store grid
        items(state.stores) { store ->
            StoreGridItem(
                store = store,
                onClick = { onOpenStore(store) }
            )
        }

        // Empty state
        if (!state.isLoading && state.stores.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "No hay tiendas disponibles todavía",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        }

        // Request store button
        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onRequestStore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddBusiness, contentDescription = null)
                Text("  Quiero tener mi tienda")
            }
        }
    }
}

@Composable
private fun StoreGridItem(
    store: TenantSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = store.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
