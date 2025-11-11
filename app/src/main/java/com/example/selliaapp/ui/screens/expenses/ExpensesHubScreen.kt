package com.example.selliaapp.ui.screens.expenses


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.BackTopAppBar

@Composable
fun ExpensesHubScreen(
    onTemplates: () -> Unit,
    onEntries: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = { BackTopAppBar(title = "Gastos", onBack = onBack) }) { padding ->
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
            Button(onClick = onTemplates) { Text("ABM de Tipos de Gasto") }
            Button(onClick = onEntries) { Text("Carga/Listado de Gastos") }
        }
    }
}
