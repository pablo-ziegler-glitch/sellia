package com.example.selliaapp.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.model.AlertSeverity
import com.example.selliaapp.data.model.UsageAlert
import com.example.selliaapp.viewmodel.UsageAlertsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageAlertsScreen(
    onBack: () -> Unit,
    vm: UsageAlertsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas de uso") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        IconButton(onClick = vm::markAllRead) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Marcar todo leído")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.error != null -> {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.alerts.isEmpty() -> {
                    Text(
                        text = "Sin alertas por ahora.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.alerts, key = { it.id }) { alert ->
                            UsageAlertCard(
                                alert = alert,
                                formatter = formatter,
                                onMarkRead = { vm.markAlertRead(alert.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageAlertCard(
    alert: UsageAlert,
    formatter: DateTimeFormatter,
    onMarkRead: () -> Unit
) {
    val accentColor = when (alert.severity) {
        AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        AlertSeverity.HIGH -> MaterialTheme.colorScheme.tertiary
        AlertSeverity.WARNING -> MaterialTheme.colorScheme.primary
        AlertSeverity.INFO -> MaterialTheme.colorScheme.outline
    }
    val timestamp = alert.createdAtMillis?.let { formatter.format(Instant.ofEpochMilli(it)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !alert.isRead, onClick = onMarkRead)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (alert.isRead) FontWeight.Normal else FontWeight.SemiBold
                    )
                }
                if (!alert.isRead) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = "Sin leer",
                        tint = accentColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${alert.percentage}% · Límite ${alert.limitValue.toInt()} · Uso ${alert.currentValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
