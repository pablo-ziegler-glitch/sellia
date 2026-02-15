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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MenuAnchorType
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

private enum class ExpenseOrderOption(val label: String) {
    DATE("Fecha"),
    AMOUNT("Monto"),
    NAME("Nombre")
}

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

    // Estado visual para paneles ocultos
    var showFilters by remember { mutableStateOf(false) }
    var showSorting by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }

    // Ordenamiento
    var orderOption by remember { mutableStateOf(ExpenseOrderOption.DATE) }
    var orderAscending by remember { mutableStateOf(false) }

    val records by repo.observeRecords(
        name = nameFilter.text.takeIf { it.isNotBlank() },
        month = monthFilter,
        year = yearFilter,
        status = statusFilter
    ).collectAsState(initial = emptyList())

    val sortedRecords = remember(records, orderOption, orderAscending) {
        val comparator = when (orderOption) {
            ExpenseOrderOption.DATE -> compareBy<ExpenseRecord>({ it.year }, { it.month }, { it.nameSnapshot.lowercase() })
            ExpenseOrderOption.AMOUNT -> compareBy<ExpenseRecord>({ it.amount }, { it.nameSnapshot.lowercase() })
            ExpenseOrderOption.NAME -> compareBy<ExpenseRecord>({ it.nameSnapshot.lowercase() }, { it.year }, { it.month })
        }
        val base = records.sortedWith(comparator)
        if (orderAscending) base else base.reversed()
    }

    // Alta rápida
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Gastos",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        showFilters = !showFilters
                        if (showFilters) showSorting = false
                    }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Mostrar filtros")
                    }
                    IconButton(onClick = {
                        showSorting = !showSorting
                        if (showSorting) showFilters = false
                    }) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Mostrar orden")
                    }
                    IconButton(onClick = { showActionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (showFilters) "Ocultar filtros" else "Mostrar filtros") },
                            onClick = {
                                showFilters = !showFilters
                                if (showFilters) showSorting = false
                                showActionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showSorting) "Ocultar orden" else "Mostrar orden") },
                            onClick = {
                                showSorting = !showSorting
                                if (showSorting) showFilters = false
                                showActionsMenu = false
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Text("+") }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showFilters) {
                OutlinedTextField(
                    value = nameFilter,
                    onValueChange = { nameFilter = it },
                    label = { Text("Nombre contiene") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthFilter?.toString().orEmpty(),
                        onValueChange = { monthFilter = it.toIntOrNull() },
                        label = { Text("Mes (1-12)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearFilter?.toString().orEmpty(),
                        onValueChange = { yearFilter = it.toIntOrNull() },
                        label = { Text("Año (YYYY)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                var statusExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it }
                ) {
                    OutlinedTextField(
                        value = statusFilter?.name ?: "Todos",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado") },
                        modifier = Modifier
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            )
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        (listOf<ExpenseStatus?>(null) + ExpenseStatus.entries).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option?.name ?: "Todos") },
                                onClick = {
                                    statusFilter = option
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (showSorting) {
                var orderExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = orderExpanded,
                    onExpandedChange = { orderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = orderOption.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ordenar por") },
                        modifier = Modifier
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            )
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = orderExpanded,
                        onDismissRequest = { orderExpanded = false }
                    ) {
                        ExpenseOrderOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    orderOption = option
                                    orderExpanded = false
                                }
                            )
                        }
                    }
                }

                TextButton(onClick = { orderAscending = !orderAscending }) {
                    Text(if (orderAscending) "Dirección: Ascendente" else "Dirección: Descendente")
                }
            }

            if (showFilters || showSorting) {
                HorizontalDivider()
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortedRecords) { record ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = {
                                Text("${record.nameSnapshot}  •  ${"%.2f".format(record.amount)}")
                            },
                            supportingContent = {
                                val attachments = if (record.receiptUris.isEmpty()) {
                                    "Sin adjuntos"
                                } else {
                                    "${record.receiptUris.size} adjunto(s)"
                                }
                                Text(
                                    "Categoría: ${record.categorySnapshot}  •  " +
                                        "Mes/Año: ${record.month}/${record.year}  •  " +
                                        "Estado: ${record.status}  •  $attachments"
                                )
                            }
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
            onSave = { rec ->
                scope.launch { repo.upsertRecord(rec) }
                showNew = false
            }
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
    var month by remember {
        mutableStateOf(TextFieldValue((Calendar.getInstance().get(Calendar.MONTH) + 1).toString()))
    }
    var year by remember {
        mutableStateOf(TextFieldValue(Calendar.getInstance().get(Calendar.YEAR).toString()))
    }
    var status by remember { mutableStateOf(ExpenseStatus.IMPAGO) }
    var receiptInput by remember { mutableStateOf(TextFieldValue("")) }
    var receiptUris by remember { mutableStateOf(listOf<String>()) }

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
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selected?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de gasto") },
                        modifier = Modifier.menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    selected = template
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it },
                        label = { Text("Mes") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Año") },
                        modifier = Modifier.weight(1f)
                    )
                }
                var statusExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it }
                ) {
                    OutlinedTextField(
                        value = status.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado") },
                        modifier = Modifier.menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                    )
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        ExpenseStatus.entries.forEach { expenseStatus ->
                            DropdownMenuItem(
                                text = { Text(expenseStatus.name) },
                                onClick = {
                                    status = expenseStatus
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = receiptInput,
                    onValueChange = { receiptInput = it },
                    label = { Text("Adjuntar ticket (URI o ruta)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        val value = receiptInput.text.trim()
                        if (value.isNotBlank()) {
                            receiptUris = receiptUris + value
                            receiptInput = TextFieldValue("")
                        }
                    }
                ) { Text("Agregar adjunto") }
                if (receiptUris.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        receiptUris.forEach { uri ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(uri, modifier = Modifier.weight(1f))
                                IconButton(onClick = { receiptUris = receiptUris - uri }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Quitar adjunto")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val template = selected ?: return@TextButton
                val amountValue = amount.text.toDoubleOrNull() ?: 0.0
                val monthValue = month.text.toIntOrNull() ?: 1
                val yearValue = year.text.toIntOrNull() ?: 1970
                onSave(
                    ExpenseRecord(
                        templateId = template.id,
                        nameSnapshot = template.name,
                        categorySnapshot = template.category,
                        amount = amountValue,
                        month = monthValue,
                        year = yearValue,
                        status = status,
                        receiptUris = receiptUris
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
