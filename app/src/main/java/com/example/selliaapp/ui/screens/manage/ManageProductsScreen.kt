package com.example.selliaapp.ui.screens.manage


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.selliaapp.ui.components.BackTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.ui.components.ProductEditorDialog
import com.example.selliaapp.viewmodel.ManageProductsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ManageProductsRoute(
    vm: ManageProductsViewModel = hiltViewModel()
) {
    ManageProductsScreen(vm = vm)
}

/**
 * Pantalla de gestión de productos:
 * - Lista de productos
 * - Alta/edición con diálogo
 * - Borrado
 */
@Composable
fun ManageProductsScreen(
    vm: ManageProductsViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    // PagingData<ProductEntity>
    val lazyItems: LazyPagingItems<ProductEntity> = vm.productsPaged.collectAsLazyPagingItems()

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var q by remember { mutableStateOf(state.query) }

    Scaffold(
        topBar = { BackTopAppBar(title = "Productos", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir producto")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ====== Filtros ======
            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = q,
                    onValueChange = {
                        q = it
                        vm.setQuery(it)
                    },
                    label = { Text("Buscar") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.onlyLowStock,
                    onClick = { vm.toggleLowStock() },
                    label = { Text("Bajo stock") }
                )
                FilterChip(
                    selected = state.onlyNoImage,
                    onClick = { vm.toggleNoImage() },
                    label = { Text("Sin imagen") }
                )
                FilterChip(
                    selected = state.onlyNoBarcode,
                    onClick = { vm.toggleNoBarcode() },
                    label = { Text("Sin código") }
                )
            }

            // ====== Lista paginada ======
            LazyColumn(Modifier.fillMaxSize()) {
                // API estándar de Paging 3: indexar por itemCount
                items(lazyItems.itemCount) { index ->
                    val p: ProductEntity? = lazyItems[index]
                    if (p == null) {
                        // Placeholder de carga (podés dibujar shimmer)
                        ListItem(
                            headlineContent = { Text("Cargando…") }
                        )
                        return@items
                    }

                    // Aplica filtros simples del estado (client-side)
                    val passLow = !state.onlyLowStock || ((p.minStock ?: 0) > 0 && p.quantity < (p.minStock ?: 0))
                    val passNoImage = !state.onlyNoImage || p.imageUrl.isNullOrBlank()
                    val passNoBarcode = !state.onlyNoBarcode || p.barcode.isNullOrBlank()
                    if (!passLow || !passNoImage || !passNoBarcode) return@items

                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text("Precio: ${p.price ?: 0.0} · Stock: ${p.quantity} · Código: ${p.barcode ?: "—"}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    editing = p
                                    showEditor = true
                                }) { Icon(Icons.Default.Edit, contentDescription = null) }

                                IconButton(onClick = {
                                    scope.launch { vm.deleteById(p.id) }
                                }) { Icon(Icons.Default.Delete, contentDescription = null) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = p
                                showEditor = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // ====== Diálogo de alta/edición ======
    if (showEditor) {
        ProductEditorDialog(
            initial = editing, // ahora asumimos ProductEditorDialog<ProductEntity?>
            onDismiss = { showEditor = false },
            onSave = { name, barcode, price, stock, description ->
                scope.launch {
                    val base: ProductEntity = editing ?: ProductEntity(
                        id = 0,
                        code = null,
                        barcode = barcode,
                        name = name,
                        price = price,
                        quantity = stock,
                        description = description,
                        imageUrl = null,
                        category = null,
                        minStock = null,
                        updatedAt = LocalDate.now()
                    )

                    val toSave: ProductEntity = base.copy(
                        name = name,
                        barcode = barcode,
                        price = price,
                        quantity = stock,
                        description = description,
                        updatedAt = LocalDate.now()
                    )

                    vm.upsert(toSave) { /* id -> podrías mostrar snackbar */ }
                    showEditor = false
                }
            }
        )
    }
}