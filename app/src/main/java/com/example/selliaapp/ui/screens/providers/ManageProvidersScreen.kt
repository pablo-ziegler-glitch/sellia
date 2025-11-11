package com.example.selliaapp.ui.screens.providers

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import com.example.selliaapp.data.model.PaymentMethod
import com.example.selliaapp.data.model.PaymentTerm
import com.example.selliaapp.data.model.Provider
import com.example.selliaapp.repository.ProviderRepository
import com.example.selliaapp.ui.components.MultiSelectChipPicker
import kotlinx.coroutines.launch
import com.example.selliaapp.ui.components.BackTopAppBar

/**
 * Gestión de Proveedores con:
 * - CRUD
 * - Filtro por múltiples Rubros (chips multi-selección)
 *
 * El filtro se aplica en el cliente (UI) sobre la lista completa observada desde Room.
 * Criterio: si hay rubros seleccionados, se muestran los proveedores que contengan
 * AL MENOS UNO de los rubros elegidos (match ANY). Si querés cambiar a "match ALL",
 * ver comentario donde se filtra la lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProvidersScreen(
    repo: ProviderRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 🔁 Tomamos la API de dominio (Provider)
    val providers by repo.observeAllModels().collectAsState(initial = emptyList())

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Provider?>(null) }

    // ----------- RUBROS DISPONIBLES (sugeridos) -----------
    val baseRubros = remember {
        listOf(
            "Chasinados", "Snacks", "Almacen", "Quesos",
            "Bebidas", "Bedidas Alcoholicas", "Lacteos", "Limpieza"
        )
    }
    val discoveredRubros: List<String> = remember(providers) {
        providers.flatMap { it.rubrosSet().toList() }
            .distinct()
            .sorted()
    }
    val pickerOptions = remember(baseRubros, discoveredRubros) {
        (baseRubros + discoveredRubros).distinct()
    }

    // ----------- ESTADO DEL FILTRO POR RUBROS -----------
    var rubrosFilter by remember { mutableStateOf<List<String>>(emptyList()) }

    // ----------- APLICACIÓN DEL FILTRO -----------
    val filteredProviders = remember(providers, rubrosFilter) {
        if (rubrosFilter.isEmpty()) {
            providers
        } else {
            providers.filter { p ->
                val provRubros = p.rubrosSet()
                // Criterio ANY: al menos uno de los seleccionados está en el proveedor
                rubrosFilter.any { it in provRubros }

                // Si preferís "match ALL":
                // rubrosFilter.all { it in provRubros }
            }
        }
    }


    Scaffold(
        topBar = { BackTopAppBar(title = "Proveedores", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Text("+")
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier.padding(inner).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ----------- PICKER DE FILTROS POR RUBROS -----------
            MultiSelectChipPicker(
                title = "Filtrar por rubros",
                options = pickerOptions,
                selectedOptions = rubrosFilter,
                onSelectionChange = { rubrosFilter = it },
                allowCustomAdd = true, // también podés filtrar por uno nuevo que no esté en opciones
                customPlaceholder = "Agregar rubro para filtrar…"
            )

            // ----------- LISTA (YA FILTRADA) -----------
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredProviders) { p ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = {
                                // Render corto: tel + rubros + term/method
                                val rubros = p.rubrosCsv?.takeIf { it.isNotBlank() } ?: ""
                                Text(buildString {
                                    append(p.phone ?: "-")
                                    if (rubros.isNotEmpty()) append("  •  $rubros")
                                    append("  •  ${p.paymentTerm} • ${p.paymentMethod}")
                                })
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editing = p; showEditor = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                                    }
                                    IconButton(onClick = { scope.launch { repo.delete(p) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ProviderEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { prov ->
                scope.launch { repo.upsert(prov) }
                showEditor = false
            }
        )
    }
}

/* ---------------------- Editor ---------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorDialog(
    initial: Provider?,
    onDismiss: () -> Unit,
    onSave: (Provider) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial?.name.orEmpty())) }
    var phone by remember { mutableStateOf(TextFieldValue(initial?.phone.orEmpty())) }

    // Rubros (picker con chips) — usamos las mismas opciones base
    val defaultRubros = remember {
        listOf(
            "Chasinados", "Snacks", "Almacen", "Quesos",
            "Bebidas", "Bedidas Alcoholicas", "Lacteos", "Limpieza"
        )
    }
    var selectedRubros by remember {
        mutableStateOf(
            initial?.rubrosCsv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        )
    }

    // Mapeamos String? <-> Enum con fallback seguro
    fun parseTerm(value: String?): PaymentTerm =
        value?.let { v -> runCatching { enumValueOf<PaymentTerm>(v) }.getOrNull() }
            ?: PaymentTerm.CUENTA_CORRIENTE

    fun parseMethod(value: String?): PaymentMethod =
        value?.let { v -> runCatching { enumValueOf<PaymentMethod>(v) }.getOrNull() }
            ?: PaymentMethod.EFECTIVO

    var term by remember { mutableStateOf(parseTerm(initial?.paymentTerm)) }
    var method by remember { mutableStateOf(parseMethod(initial?.paymentMethod)) }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo Proveedor" else "Editar Proveedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre *") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") })

                MultiSelectChipPicker(
                    title = "Rubros",
                    options = defaultRubros,
                    selectedOptions = selectedRubros,
                    onSelectionChange = { selectedRubros = it },
                    allowCustomAdd = true,
                    customPlaceholder = "Agregar rubro…"
                )

                // Forma de pago
                Text("Forma de pago")
                var expandedTerm by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expandedTerm, onExpandedChange = { expandedTerm = it }) {
                    OutlinedTextField(
                        value = term.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Seleccionar") },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedTerm, onDismissRequest = { expandedTerm = false }) {
                        PaymentTerm.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = { term = t; expandedTerm = false }
                            )
                        }
                    }
                }

                // Medio de pago
                Text("Medio de pago aceptado")
                var expandedMethod by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expandedMethod, onExpandedChange = { expandedMethod = it }) {
                    OutlinedTextField(
                        value = method.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Seleccionar") },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedMethod, onDismissRequest = { expandedMethod = false }) {
                        PaymentMethod.values().forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = { method = m; expandedMethod = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val base = initial ?: Provider(name = name.text.trim())
                val csv = selectedRubros.joinToString(",")
                onSave(
                    base.copy(
                        name = name.text.trim(),
                        phone = phone.text.trim().ifBlank { null },
                        rubrosCsv = csv.ifBlank { null },
                        // ⬇️ Persistimos el .name (String) del enum
                        paymentTerm = term.name,
                        paymentMethod = method.name
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/* ---------------------- Helpers ---------------------- */

/** Convierte el CSV de rubros del proveedor a Set<String> normalizado (trim y sin vacíos). */
private fun Provider.rubrosSet(): Set<String> =
    (this.rubrosCsv ?: "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()