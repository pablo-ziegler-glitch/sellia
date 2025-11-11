package com.example.selliaapp.ui.screens.expenses


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.ExpenseTemplate
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTemplatesScreen(
    repo: ExpenseRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val items by repo.observeTemplates().collectAsState(initial = emptyList())

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseTemplate?>(null) }

    Scaffold(
        topBar = { BackTopAppBar(title = "Tipos de Gasto", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) { Text("+") }
        }
    ) { inner ->
        LazyColumn(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { t ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(t.name) },
                        supportingContent = { Text("Obligatorio: ${t.required}  •  Monto sugerido: ${t.defaultAmount ?: "-"}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = t; showEditor = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { scope.launch { repo.deleteTemplate(t) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Borrar")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showEditor) {
        TemplateEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { e ->
                scope.launch { repo.upsertTemplate(e) }
                showEditor = false
            }
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    initial: ExpenseTemplate?,
    onDismiss: () -> Unit,
    onSave: (ExpenseTemplate) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial?.name.orEmpty())) }
    var amount by remember { mutableStateOf(TextFieldValue(initial?.defaultAmount?.toString().orEmpty())) }
    var required by remember { mutableStateOf(initial?.required ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo Tipo de Gasto" else "Editar Tipo de Gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre *") })
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Monto sugerido (opcional)") })
                Row { Checkbox(checked = required, onCheckedChange = { required = it }); Text("Obligatorio") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val base = initial ?: ExpenseTemplate(name = name.text.trim())
                onSave(
                    base.copy(
                        name = name.text.trim(),
                        defaultAmount = amount.text.toDoubleOrNull(),
                        required = required
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
