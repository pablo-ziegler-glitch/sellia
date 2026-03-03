package com.floki.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Diálogo genérico para pedir cantidad (entero >= 1)
 */
@Composable
fun QuantityInputDialog(
    title: String,
    maxValue: Int? = null,            // límite opcional (stock disponible)
    confirmText: String,
    cancelText: String,
    initialValue: Int = 1,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue.coerceAtLeast(1).toString()) }
    var qty by remember(initialValue) { mutableStateOf(initialValue.coerceAtLeast(1)) }

    // Sincroniza el entero con el campo de texto (solo dígitos)
    LaunchedEffect(text) {
        val n = text.toIntOrNull()
        qty = (n ?: 1).coerceAtLeast(1)
    }

    val tooHigh = maxValue != null && qty > maxValue
    val atMax = maxValue != null && qty == maxValue
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Stepper: − [cantidad] +
                    Row {
                        IconButton(
                            onClick = {
                                val newQty = (qty - 1).coerceAtLeast(1)
                                qty = newQty
                                text = newQty.toString()
                            },
                            enabled = qty > 1
                        ) { Icon(Icons.Default.Remove, contentDescription = "Restar 1") }

                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.width(96.dp),
                            singleLine = true,
                            isError = tooHigh || qty < 1,                   // error visual en tiempo real
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        IconButton(
                            onClick = {
                                val newQty = if (maxValue != null) (qty + 1).coerceAtMost(maxValue) else qty + 1
                                qty = newQty
                                text = newQty.toString()
                            },
                            enabled = maxValue?.let { qty < it } ?: true
                        ) { Icon(Icons.Default.Add, contentDescription = "Sumar 1") }
                    }

                    // Botones rápidos:  Max  |  1   (nuevo botón a la derecha de "Max")
                    Row {
                        if (maxValue != null) {
                            OutlinedButton(
                                onClick = {
                                    qty = maxValue
                                    text = maxValue.toString()
                                }
                            ) { Text("Max") }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                qty = 1
                                text = "1"
                            }
                        ) { Text("1") }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Mensajes live
                if (maxValue != null) {
                    if (tooHigh) {
                        Text("Excede el stock disponible (máx: $maxValue).", color = androidx.compose.ui.graphics.Color.Red)
                    } else if (atMax) {
                        Text("Alcanzaste el stock disponible.", color = androidx.compose.ui.graphics.Color(0xFFCC7700))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qty) },
                enabled = !tooHigh && qty >= 1                 // no se puede confirmar si se excede
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(cancelText) } }
    )
}