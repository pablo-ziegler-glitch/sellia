package com.example.selliaapp.ui.screens.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.viewmodel.cash.CashViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashCloseScreen(
    vm: CashViewModel,
    onBack: () -> Unit,
    onCloseSuccess: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    var counted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val summary = state.summary
    val expected = summary?.expectedAmount ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerrar caja") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Resumen", style = MaterialTheme.typography.titleMedium)
            Text("Apertura: ${currency.format(summary?.session?.openingAmount ?: 0.0)}")
            Text("Ventas efectivo: ${currency.format(summary?.cashSalesTotal ?: 0.0)}")
            Text("Teórico final: ${currency.format(expected)}")
            OutlinedTextField(
                value = counted,
                onValueChange = { counted = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Monto final contado") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota (opcional)") }
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val amount = counted.toDoubleOrNull()
                    vm.closeCash(amount, note.takeIf { it.isNotBlank() })
                    onCloseSuccess()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar cierre")
            }
            Text(
                "Exportar/Compartir resumen (próximamente)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
