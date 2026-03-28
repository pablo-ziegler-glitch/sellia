package com.example.selliaapp.ui.screens.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.selliaapp.ui.components.BackTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.ui.components.CustomerEditorDialog
import com.example.selliaapp.viewmodel.CustomerFrequencyFilter
import com.example.selliaapp.viewmodel.ManageCustomersViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestión de clientes con búsqueda, edición, alta y borrado.
 */
@Composable
fun ManageCustomersScreen(
    vm: ManageCustomersViewModel,
    onSellToCustomer: (CustomerEntity) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val customerInsights by vm.customerInsights.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es", "AR")) }

    var editing by remember { mutableStateOf<CustomerEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CustomerFrequencyFilter.ALL) }

    Scaffold(
        topBar = { BackTopAppBar(title = "Clientes", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = "Nuevo cliente") }
        }
    ) { padding ->
        if (customerInsights.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Todavía no hay clientes",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Agregá tu primer cliente para empezar a registrar compras y métricas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { editing = null; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar cliente")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                vm.setQuery(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Buscar cliente") },
                            placeholder = { Text("Nombre, email o teléfono") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomerFrequencyFilter.values().forEach { option ->
                                Button(
                                    onClick = {
                                        filter = option
                                        vm.setFrequencyFilter(option)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        when (option) {
                                            CustomerFrequencyFilter.ALL -> "Todos"
                                            CustomerFrequencyFilter.TOP_CLIENTS -> "Top clientes"
                                            CustomerFrequencyFilter.NO_RECENT_PURCHASES -> "Sin compras recientes"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                items(customerInsights) { insight ->
                    val customer = insight.customer
                    ListItem(
                        headlineContent = {
                            Text(
                                buildString {
                                    append(customer.name)
                                    if (!customer.nickname.isNullOrBlank()) {
                                        append(" -> ${customer.nickname}")
                                    }
                                }
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(customer.email ?: customer.phone ?: "-")
                                Text("Compras totales: ${currency.format(insight.totalSpent)}")
                                Text(
                                    "Última visita: ${
                                        insight.lastPurchaseMillis?.let { dateFormat.format(Date(it)) } ?: "Sin visitas"
                                    }"
                                )
                                Text(
                                    "Producto más comprado: ${
                                        insight.mostPurchasedProduct?.let {
                                            "$it (${insight.mostPurchasedUnits}u)"
                                        } ?: "Sin datos"
                                    }"
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onSellToCustomer(customer) }) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = "Vender a este cliente")
                                }
                                IconButton(onClick = {
                                    editing = customer
                                    showEditor = true
                                }) { Icon(Icons.Default.Edit, null) }
                                IconButton(onClick = { scope.launch { vm.delete(customer) } }) {
                                    Icon(Icons.Default.Delete, null)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = customer
                                showEditor = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }

        if (showEditor) {
            CustomerEditorDialog(
                initial = editing,
                onDismiss = { showEditor = false },
                onSave = { customer ->
                    vm.save(customer) { showEditor = false }
                }
            )
        }
    }
