package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.PublicCatalogSettings
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.PublicCatalogConfigViewModel

private val SORT_OPTIONS = listOf(
    "name_asc" to "Nombre A-Z",
    "name_desc" to "Nombre Z-A",
    "updated_desc" to "Recientes primero"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicCatalogConfigScreen(
    vm: PublicCatalogConfigViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState(initial = PublicCatalogSettings())
    var catalogEnabled by remember { mutableStateOf(settings.catalogEnabled) }
    var showPrices by remember { mutableStateOf(settings.showPrices) }
    var showCashPrice by remember { mutableStateOf(settings.showCashPrice) }
    var heroTitle by remember { mutableStateOf(settings.heroTitle) }
    var heroSubtitle by remember { mutableStateOf(settings.heroSubtitle) }
    var footerText by remember { mutableStateOf(settings.footerText) }
    var defaultSort by remember { mutableStateOf(settings.defaultSort) }

    LaunchedEffect(settings) {
        catalogEnabled = settings.catalogEnabled
        showPrices = settings.showPrices
        showCashPrice = settings.showCashPrice
        heroTitle = settings.heroTitle
        heroSubtitle = settings.heroSubtitle
        footerText = settings.footerText
        defaultSort = settings.defaultSort
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Catálogo público", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("General", style = MaterialTheme.typography.titleSmall)

            SwitchRow(
                label = "Catálogo habilitado",
                checked = catalogEnabled,
                onCheckedChange = { catalogEnabled = it }
            )

            HorizontalDivider()
            Text("Precios", style = MaterialTheme.typography.titleSmall)

            SwitchRow(
                label = "Mostrar precios",
                checked = showPrices,
                onCheckedChange = { showPrices = it }
            )
            SwitchRow(
                label = "Mostrar precio contado",
                checked = showCashPrice,
                onCheckedChange = { showCashPrice = it }
            )

            HorizontalDivider()
            Text("Apariencia", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = heroTitle,
                onValueChange = { heroTitle = it },
                label = { Text("Título hero") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = heroSubtitle,
                onValueChange = { heroSubtitle = it },
                label = { Text("Subtítulo hero") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = footerText,
                onValueChange = { footerText = it },
                label = { Text("Texto pie de catálogo") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Orden por defecto", style = MaterialTheme.typography.titleSmall)

            SortDropdown(
                selectedSort = defaultSort,
                onSortSelected = { defaultSort = it }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    vm.updateSettings(
                        PublicCatalogSettings(
                            catalogEnabled = catalogEnabled,
                            showPrices = showPrices,
                            showCashPrice = showCashPrice,
                            heroTitle = heroTitle,
                            heroSubtitle = heroSubtitle,
                            footerText = footerText,
                            defaultSort = defaultSort
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar configuración")
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    selectedSort: String,
    onSortSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = SORT_OPTIONS.firstOrNull { it.first == selectedSort }?.second ?: "Nombre A-Z"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SORT_OPTIONS.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSortSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
