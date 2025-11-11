package com.example.selliaapp.ui.screens.expenses


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.ExpenseRecord
import com.example.selliaapp.data.model.ExpenseStatus
import com.example.selliaapp.data.model.ExpenseTemplate
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntriesScreen(
    repo: ExpenseRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val templates by repo.observeTemplates().collectAsState(initial = emptyList())

    // Filtros
    var nameFilter by remember { mutableStateOf(TextFieldValue("")) }
    var monthFilter by remember { mutableStateOf<Int?>(null) }
    var yearFilter by remember { mutableStateOf<Int?>(null) }
    var statusFilter by remember { mutableStateOf<ExpenseStatus?>(null) }

    val records by repo.observeRecords(
        name = nameFilter.text.takeIf { it.isNotBlank() },
        month = monthFilter,
        year = yearFilter,
        status = statusFilter
    ).collectAsState(initial = emptyList())

    // Alta rápida
    var showNew by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopAppBar(title = "Gastos", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Text("+") }
        }
    ) { inner ->
        Column(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Filtros
            OutlinedTextField(value = nameFilter, onValueChange = { nameFilter = it }, label = { Text("Nombre contiene") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mes (1..12)
                OutlinedTextField(
                    value = (monthFilter?.toString() ?: ""),
                    onValueChange = { monthFilter = it.toIntOrNull() },
                    label = { Text("Mes (1-12)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = (yearFilter?.toString() ?: ""),
                    onValueChange = { yearFilter = it.toIntOrNull() },
                    label = { Text("Año (YYYY)") },
                    modifier = Modifier.weight(1f)
                )
            }

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = statusFilter?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Estado") },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(null) + ExpenseStatus.entries
                        .forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.name ?: "Todos") },
                                onClick = { statusFilter = opt; expanded = false }
                            )
                        }
                }
            }

            Divider()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records) { r ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("${r.nameSnapshot}  •  ${"%.2f".format(r.amount)}") },
                            supportingContent = { Text("Mes/Año: ${r.month}/${r.year}  •  Estado: ${r.status}") }
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        NewExpenseDialog(
            templates = templates,
            onDismiss = { showNew = false },
            onSave = { rec -> scope.launch { repo.upsertRecord(rec) }; showNew = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewExpenseDialog(
    templates: List<ExpenseTemplate>,
    onDismiss: () -> Unit,
    onSave: (ExpenseRecord) -> Unit
) {
    var selected: ExpenseTemplate? by remember { mutableStateOf(null) }
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var month by remember { mutableStateOf(TextFieldValue((Calendar.getInstance().get(Calendar.MONTH) + 1).toString())) }
    var year by remember { mutableStateOf(TextFieldValue(Calendar.getInstance().get(Calendar.YEAR).toString())) }
    var status by remember { mutableStateOf(ExpenseStatus.IMPAGO) }

    LaunchedEffect(selected) {
        if (selected?.defaultAmount != null && amount.text.isBlank()) {
            amount = TextFieldValue(selected!!.defaultAmount.toString())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // selector de plantilla
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selected?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de gasto") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        templates.forEach { t ->
                            DropdownMenuItem(text = { Text(t.name) }, onClick = { selected = t; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Monto") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("Mes") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Año") }, modifier = Modifier.weight(1f))
                }
                // estado
                var exp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = exp, onExpandedChange = { exp = it }) {
                    OutlinedTextField(
                        value = status.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                        ExpenseStatus.values().forEach { st ->
                            DropdownMenuItem(text = { Text(st.name) }, onClick = { status = st; exp = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = selected ?: return@TextButton
                val amt = amount.text.toDoubleOrNull() ?: 0.0
                val m = month.text.toIntOrNull() ?: 1
                val y = year.text.toIntOrNull() ?: 1970
                onSave(
                    ExpenseRecord(
                        templateId = t.id,
                        nameSnapshot = t.name,
                        amount = amt,
                        month = m,
                        year = y,
                        status = status
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
