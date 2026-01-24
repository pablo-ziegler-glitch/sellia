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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.selliaapp.viewmodel.cash.CashViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashOpenScreen(
    vm: CashViewModel,
    onBack: () -> Unit
) {
    var openingAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abrir caja") },
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
            Text("Monto inicial", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = openingAmount,
                onValueChange = { openingAmount = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nota (opcional)") }
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val amount = openingAmount.toDoubleOrNull() ?: 0.0
                    if (amount >= 0) {
                        vm.openCash(amount, note.takeIf { it.isNotBlank() })
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar apertura")
            }
        }
    }
}
