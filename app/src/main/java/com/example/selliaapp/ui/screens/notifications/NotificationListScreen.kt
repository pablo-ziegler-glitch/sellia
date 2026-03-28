package com.example.selliaapp.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.remote.AppNotification
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.NotificationViewModel

@Composable
fun NotificationListScreen(
    onBack: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit = {},
    onQuickAdjustStock: (Int) -> Unit = {},
    onQuickCreatePurchaseOrder: (Int) -> Unit = {},
    onOpenExpenseDetail: () -> Unit = {},
    onOpenSaleDetail: (Long) -> Unit = {},
    vm: NotificationViewModel = hiltViewModel()
) {
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val unreadCount by vm.unreadCount.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopAppBar(title = "Notificaciones", onBack = onBack)

        if (unreadCount > 0) {
            TextButton(
                onClick = vm::markAllAsRead,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Marcar todas como leídas")
            }
        }

        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = "Sin notificaciones",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationItem(
                        notification = notification,
                        onQuickAdjustStock = onQuickAdjustStock,
                        onQuickCreatePurchaseOrder = onQuickCreatePurchaseOrder,
                        onOpenExpenseDetail = onOpenExpenseDetail,
                        onOpenSaleDetail = onOpenSaleDetail,
                        onClick = {
                            if (!notification.read) {
                                vm.markAsRead(notification.id)
                            }
                            onNotificationClick(notification)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    onQuickAdjustStock: (Int) -> Unit,
    onQuickCreatePurchaseOrder: (Int) -> Unit,
    onOpenExpenseDetail: () -> Unit,
    onOpenSaleDetail: (Long) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.read)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!notification.read) {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = "No leída",
                    modifier = Modifier.size(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (!notification.read) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val productId = notification.actionRoute?.substringAfterLast("/")?.toIntOrNull()
                val saleId = notification.actionRoute?.substringAfterLast("/")?.toLongOrNull()
                val isCriticalStock = notification.type.contains("stock", ignoreCase = true)
                val isBudgetExceeded = notification.type.contains("budget", ignoreCase = true) ||
                    notification.type.contains("expense", ignoreCase = true)
                val isMercadoPagoPayment = notification.type.contains("mercadopago", ignoreCase = true) ||
                    notification.type.contains("payment_received", ignoreCase = true)

                if (isCriticalStock && productId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Button(onClick = { onQuickAdjustStock(productId) }) {
                            Text("Ajustar stock")
                        }
                        TextButton(onClick = { onQuickCreatePurchaseOrder(productId) }) {
                            Text("Crear OC")
                        }
                    }
                }
                if (isBudgetExceeded) {
                    TextButton(onClick = onOpenExpenseDetail) {
                        Text("Ver detalle de gastos")
                    }
                }
                if (isMercadoPagoPayment && saleId != null) {
                    TextButton(onClick = { onOpenSaleDetail(saleId) }) {
                        Text("Ver resumen de venta")
                    }
                }
            }
        }
    }
}
