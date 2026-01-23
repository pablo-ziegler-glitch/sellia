package com.example.selliaapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.local.entity.CustomerEntity

/**
 * Hoja inferior para elegir un cliente de la lista (con búsqueda local).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPickerSheet(
    customers: List<CustomerEntity>,
    onPick: (CustomerEntity) -> Unit,
    onQuickPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var quickName by remember { mutableStateOf("") }

    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query, ignoreCase = true) ||
                    (it.email ?: "").contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Elegir cliente")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar por nombre o email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
            )
            Text("Carga rápida")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quickName,
                    onValueChange = { quickName = it },
                    label = { Text("Nombre del cliente") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val trimmed = quickName.trim()
                        if (trimmed.isNotBlank()) {
                            onQuickPick(trimmed)
                            quickName = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Usar")
                }
            }
            LazyColumn {
                items(
                    items = filtered,
                    key = { it.id } // Cambiar si tu PK se llama distinto
                ) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(c.email ?: "-") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(c) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
