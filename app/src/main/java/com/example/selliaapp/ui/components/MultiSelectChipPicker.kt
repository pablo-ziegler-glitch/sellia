package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Componente de chips multi-selección con posibilidad de agregar opciones custom.
 *
 * @param title Título visible arriba de los chips
 * @param options Lista de opciones disponibles (se muestran como chips)
 * @param selectedOptions Selección actual (valores de options o agregados custom)
 * @param onSelectionChange Callback con el nuevo Set/List seleccionado
 * @param allowCustomAdd Si true, permite agregar chips nuevos por input
 * @param customPlaceholder Placeholder del input para agregar chip nuevo
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectChipPicker(
    title: String,
    options: List<String>,
    selectedOptions: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    allowCustomAdd: Boolean = false,
    customPlaceholder: String = "Agregar…",
    modifier: Modifier = Modifier
) {
    Text(title, modifier = modifier.padding(bottom = 8.dp))

    var input by remember { mutableStateOf("") }

    if (allowCustomAdd) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(customPlaceholder) },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val v = input.trim()
                    if (v.isNotEmpty()) {
                        val next = (selectedOptions + v).distinct()
                        onSelectionChange(next)
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // Chips disponibles
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val selected = opt in selectedOptions
            FilterChip(
                selected = selected,
                onClick = {
                    val next = if (selected) {
                        selectedOptions.filter { it != opt }
                    } else {
                        (selectedOptions + opt).distinct()
                    }
                    onSelectionChange(next)
                },
                label = { Text(opt) }
            )
        }

        // Chips seleccionados que NO están en options (customs)
        selectedOptions.filter { it !in options }.forEach { custom ->
            AssistChip(
                onClick = { /*noop*/ },
                label = { Text(custom) },
                colors = AssistChipDefaults.assistChipColors()
            )
        }
    }
}
