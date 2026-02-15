package com.example.selliaapp.ui.screens.public

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ViewerCatalogViewModel

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

    val followStoreName = state.availableStores.firstOrNull { it.id == selectedStoreToFollow }?.name.orEmpty()
    val selectedCatalogStore = state.followedStores.firstOrNull { it.id == state.selectedStoreId }

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
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = state.followedStores.isNotEmpty() && !state.isLoading
                )
                ExposedDropdownMenu(
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

            Card(modifier = Modifier.fillMaxWidth()) {
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
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = state.hasStoresToFollow && !state.isLoadingStores
                        )
                        ExposedDropdownMenu(
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
}
