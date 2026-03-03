package com.floki.app.ui.screens.dev


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floki.app.viewmodel.ProductLookupViewModel

/**
 * Pantalla de demo para validar la migración sin tocar otras pantallas.
 */
@Composable
fun ProductLookupScreen(vm: ProductLookupViewModel = hiltViewModel()) {
    var code by remember { mutableStateOf("") }
    val product by vm.result.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Barcode") }
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.findByBarcode(code) }) {
            Text("Buscar")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = product?.let { "Encontrado: ${it.name} (id=${it.id})" } ?: "Sin resultado"
        )
    }
}
