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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.ui.components.CustomerEditorDialog
import kotlinx.coroutines.launch

/**
 * Gestión de clientes con búsqueda, edición, alta y borrado.
 */
@Composable
fun ManageCustomersScreen(
    customerRepository: CustomerRepository,
    onBack: () -> Unit
) {
        val scope = rememberCoroutineScope()
        val customers by customerRepository.observeAll().collectAsState(initial = emptyList())

        var editing by remember { mutableStateOf<CustomerEntity?>(null) }
        var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BackTopAppBar(title = "Clientes", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = "Nuevo cliente") }
        }
    ) { padding ->
        if (customers.isEmpty()) {
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
            LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
                items(customers) { c ->
                    ListItem(
                        headlineContent = {
                            Text(
                                buildString {
                                    append(c.name)
                                    if (!c.nickname.isNullOrBlank()) {
                                        append(" -> ${c.nickname}")
                                    }
                                }
                            )
                        },
                        supportingContent = { Text(c.email ?: "-") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    editing = c
                                    showEditor = true
                                }) { Icon(Icons.Default.Edit, null) }
                                IconButton(onClick = { scope.launch { customerRepository.delete(c) } }) {
                                    Icon(Icons.Default.Delete, null)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = c
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
                    scope.launch {
                        customerRepository.upsert(customer)
                        showEditor = false
                    }
                }
            )
        }
    }