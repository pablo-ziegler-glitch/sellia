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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ClientMetricsViewModel

@Composable
fun ClientMetricsScreen(
    vm: ClientMetricsViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    Scaffold(topBar = { BackTopAppBar(title = "Métricas de clientes", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard("Altas hoy", state.day)
            MetricCard("Altas esta semana", state.week)
            MetricCard("Altas este mes", state.month)
            MetricCard("Altas este año", state.year)

            Button(onClick = { vm.refresh() }) { Text("Actualizar") }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Int) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label)
            Text(value.toString())
        }
    }
}
