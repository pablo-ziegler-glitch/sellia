package com.example.selliaapp.ui.screens.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.BackTopAppBar

/**
 * Hub de Clientes con 3 accesos:
 * - Gestionar (CRUD).
 * - Buscar compras por cliente.
 * - Métricas de altas.
 * Plus opcional: Exportar CSV.
 */
@Composable
fun ClientsHubScreen(
    onCrud: () -> Unit,
    onSearchPurchases: () -> Unit,
    onMetrics: () -> Unit,
    onExportCsv: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    Scaffold(topBar = { BackTopAppBar(title = "Clientes", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onCrud) { Text("Gestionar Clientes (CRUD)") }
            Button(onClick = onSearchPurchases) { Text("Buscar Compras por Cliente") }
            Button(onClick = onMetrics) { Text("Métricas de Clientes") }

            if (onExportCsv != null) {
                OutlinedButton(onClick = onExportCsv) {
                    Text("Exportar Clientes (CSV)")
                }
            }
        }
    }
}
