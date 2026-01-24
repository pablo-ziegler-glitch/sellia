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
fun CashAuditScreen(
    vm: CashViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "AR")) }
    var counted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val expected = state.summary?.expectedAmount ?: 0.0
    val countedValue = counted.toDoubleOrNull() ?: 0.0
    val difference = countedValue - expected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arqueo") },
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
            Text("Teórico: ${currency.format(expected)}", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = counted,
                onValueChange = { counted = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Monto contado") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota (opcional)") }
            )
            Text(
                "Diferencia: ${currency.format(difference)}",
                color = if (difference >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.auditCash(countedValue, note.takeIf { it.isNotBlank() })
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar arqueo")
            }
        }
    }
}
