package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun StockBySizeDialog(
    totalStock: Int,
    availableSizes: List<String>,
    initialQuantities: Map<String, Int>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Int>) -> Unit
) {
    val normalizedSizes = remember(availableSizes) {
        availableSizes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val quantities = remember(normalizedSizes, initialQuantities) {
        mutableStateMapOf<String, Int>().apply {
            normalizedSizes.forEach { size -> put(size, (initialQuantities[size] ?: 0).coerceAtLeast(0)) }
        }
    }
    val fields = remember(normalizedSizes, quantities) {
        mutableStateMapOf<String, TextFieldValue>().apply {
            normalizedSizes.forEach { size -> put(size, TextFieldValue((quantities[size] ?: 0).toString())) }
        }
    }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    val globalErrors = remember { mutableStateListOf<String>() }

    fun distributedTotal(): Int = quantities.values.sum()
    fun remainingStock(): Int = totalStock - distributedTotal()

    fun validateAll(): Boolean {
        globalErrors.clear()
        fieldErrors.clear()

        if (normalizedSizes.isEmpty()) {
            globalErrors += "Este producto no tiene talles definidos. Cargalos primero en el campo de talles."
            return false
        }

        normalizedSizes.forEach { size ->
            val raw = fields[size]?.text?.trim().orEmpty()
            when {
                raw.isBlank() -> fieldErrors[size] = "Ingresá una cantidad."
                raw.any { !it.isDigit() } -> fieldErrors[size] = "Solo se permiten números enteros."
                else -> {
                    val parsed = raw.toIntOrNull()
                    if (parsed == null) {
                        fieldErrors[size] = "Valor numérico inválido."
                    } else {
                        quantities[size] = parsed
                    }
                }
            }
        }

        val total = distributedTotal()
        if (total > totalStock) {
            globalErrors += "La suma por talle ($total) no puede superar el stock total ($totalStock)."
        }
        if (quantities.values.any { it < 0 }) {
            globalErrors += "No se permiten cantidades negativas."
        }
        if (quantities.values.all { it == 0 }) {
            globalErrors += "Ingresá al menos un talle con cantidad mayor a 0."
        }

        return globalErrors.isEmpty() && fieldErrors.isEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock por talle") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Stock total: $totalStock · Distribuido: ${distributedTotal()} · Restante: ${remainingStock()}",
                    style = MaterialTheme.typography.bodyMedium
                )

                normalizedSizes.forEach { size ->
                    val qty = quantities[size] ?: 0
                    val error = fieldErrors[size]

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = size,
                            modifier = Modifier.weight(1f).padding(top = 16.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                        IconButton(
                            onClick = {
                                val next = (qty - 1).coerceAtLeast(0)
                                quantities[size] = next
                                fields[size] = TextFieldValue(next.toString())
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Restar")
                        }
                        OutlinedTextField(
                            value = fields[size] ?: TextFieldValue(qty.toString()),
                            onValueChange = { tf ->
                                fields[size] = tf
                                val txt = tf.text.trim()
                                when {
                                    txt.isEmpty() -> {
                                        fieldErrors[size] = "Ingresá una cantidad."
                                    }
                                    txt.any { !it.isDigit() } -> {
                                        fieldErrors[size] = "Solo se permiten números enteros."
                                    }
                                    else -> {
                                        val parsed = txt.toIntOrNull()
                                        if (parsed == null) {
                                            fieldErrors[size] = "Valor numérico inválido."
                                        } else {
                                            fieldErrors.remove(size)
                                            quantities[size] = parsed
                                        }
                                    }
                                }
                            },
                            isError = error != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            supportingText = {
                                if (error != null) {
                                    Text(error)
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                val next = qty + 1
                                quantities[size] = next
                                fields[size] = TextFieldValue(next.toString())
                                fieldErrors.remove(size)
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Sumar")
                        }
                    }
                }

                globalErrors.forEach { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (validateAll()) {
                    onSave(quantities.filterValues { it > 0 })
                }
            }) {
                Text("Guardar talles")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
