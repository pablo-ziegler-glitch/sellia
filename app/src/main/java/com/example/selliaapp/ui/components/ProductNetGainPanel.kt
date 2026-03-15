package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

data class NetGainChannel(val label: String, val price: Double, val applyPosnet: Boolean)

/**
 * Panel que muestra la ganancia neta estimada por canal de precio.
 * Descuenta costo de adquisición, costos operativos y (para el canal Lista) comisión posnet.
 */
@Composable
fun ProductNetGainPanel(
    purchasePrice: Double,
    posnetPercent: Double,
    operativosPercent: Double,
    channels: List<NetGainChannel>
) {
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    var showGainHelp by remember { mutableStateOf(false) }

    if (showGainHelp) {
        AlertDialog(
            onDismissRequest = { showGainHelp = false },
            confirmButton = { TextButton(onClick = { showGainHelp = false }) { Text("Entendido") } },
            title = { Text("Ganancia bruta vs. neta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ganancia bruta", fontWeight = FontWeight.Bold)
                        Text(
                            "Es la diferencia entre el precio de venta y el costo de adquisición del producto. " +
                            "No descuenta costos fijos (alquiler, servicios, sueldos, etc.).\n" +
                            "Fórmula: precio de venta − costo de adquisición."
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ganancia neta", fontWeight = FontWeight.Bold)
                        Text(
                            "Es la ganancia real que queda luego de descontar el costo de adquisición " +
                            "y todos los costos fijos y variables del negocio (comisiones, impuestos, " +
                            "logística, gastos operativos, etc.).\n" +
                            "Fórmula: ganancia bruta − todos los costos y gastos."
                        )
                    }
                    Text(
                        "Este panel muestra la ganancia NETA estimada por canal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Ganancia neta estimada por canal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showGainHelp = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "¿Qué es ganancia bruta vs neta?",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "Costo de adquisición: ${currency.format(purchasePrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            if (channels.isEmpty()) {
                Text(
                    "Cargá al menos un precio para ver la ganancia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                channels.forEach { (label, price, applyPosnet) ->
                    val operativosFee = purchasePrice * (operativosPercent / 100.0)
                    val posnetRate = posnetPercent / 100.0
                    val posnetFee = if (applyPosnet && posnetRate > 0) price * posnetRate / (1 + posnetRate) else 0.0
                    val gain = price - purchasePrice - operativosFee - posnetFee
                    val gainPct = if (purchasePrice > 0) (gain / purchasePrice) * 100.0 else 0.0
                    val isPositive = gain >= 0
                    val gainColor = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            currency.format(price),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${if (isPositive) "+" else ""}${currency.format(gain)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = gainColor,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${if (isPositive) "+" else ""}${String.format("%.1f", gainPct)}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = gainColor
                        )
                    }
                }
            }
        }
    }
}
