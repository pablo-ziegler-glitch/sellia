package com.example.selliaapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.Product
import java.time.format.DateTimeFormatter

/**
 * Item de listado de producto con información clave.
 * Muestra: Código, Nombre, Cantidad, MinStock, Precios, Actualizado.
 */
@Composable
fun ProductListItem(
    product: Product,
    minStock: Int? = null, // si Product tiene minStock propio, podés ignorar este param
    onClick: (() -> Unit)? = null
) {
    val qtyColor = when {
        minStock != null && product.quantity <= minStock -> MaterialTheme.colorScheme.error
        product.quantity == 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = product.code ?: product.barcode ?: "SIN-COD",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Cant: ${product.quantity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = qtyColor
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium
            )
            product.category?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Divider(Modifier.padding(vertical = 8.dp))
            Text(
                text = "Lista: ${product.listPrice ?: product.price ?: "-"} · " +
                    "Efectivo: ${product.cashPrice ?: product.listPrice ?: product.price ?: "-"} · " +
                    "Transferencia: ${product.transferPrice ?: product.listPrice ?: product.price ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "ML: ${product.mlPrice ?: "-"} · ML 3C: ${product.ml3cPrice ?: "-"} · ML 6C: ${product.ml6cPrice ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                product.updatedAt?.let { instant ->
                    val date = DateTimeFormatter.ISO_LOCAL_DATE_TIME // ajustar si guardás zoned
                    Text(
                        text = "Act: ${instant.toString()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
