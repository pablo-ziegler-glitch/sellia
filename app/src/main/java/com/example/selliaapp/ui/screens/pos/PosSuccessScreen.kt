package com.example.selliaapp.ui.screens.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosSuccessScreen(
    invoiceId: Long,
    total: Double,
    method: String,
    onNewSale: () -> Unit,
    onViewSale: () -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Venta confirmada", style = MaterialTheme.typography.titleLarge)
                Text("Ticket #$invoiceId", style = MaterialTheme.typography.bodyMedium)
                Text("Total: ${currency.format(total)}", style = MaterialTheme.typography.titleMedium)
                Text("Pago: $method", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth()) {
            Text("Nueva venta")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onViewSale, modifier = Modifier.fillMaxWidth()) {
            Text("Ver venta")
        }
    }
}
