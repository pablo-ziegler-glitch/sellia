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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
 import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.model.AlertSeverity
import com.example.selliaapp.data.model.UsageAlert
import com.example.selliaapp.viewmodel.UsageAlertsViewModel
import com.example.selliaapp.viewmodel.UsageLimitSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageAlertsScreen(
    onBack: () -> Unit,
    canEditLimits: Boolean,
    vm: UsageAlertsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault())
    }
    val numberFormatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    var editingSummary by remember { mutableStateOf<UsageLimitSummary?>(null) }
    var limitText by remember { mutableStateOf("") }

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
        if (editingSummary != null) {
            AlertDialog(
                onDismissRequest = { editingSummary = null },
                title = { Text("Editar tope") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(editingSummary?.title.orEmpty())
                        OutlinedTextField(
                            value = limitText,
                            onValueChange = { limitText = it },
                            label = { Text("Nuevo tope") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val value = limitText.replace(',', '.').toDoubleOrNull()
                            if (value != null && value > 0) {
                                vm.updateLimit(editingSummary!!.metric, value)
                                editingSummary = null
                            }
                        }
                    ) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { editingSummary = null }) { Text("Cancelar") }
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        UsageLimitsOverviewCard(
                            summaries = state.limitSummaries,
                            numberFormatter = numberFormatter,
                            canEditLimits = canEditLimits,
                            onEditLimit = { summary ->
                                editingSummary = summary
                                limitText = summary.limitValue.toString()
                            }
                        )
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        UsageLimitsOverviewCard(
                            summaries = state.limitSummaries,
                            numberFormatter = numberFormatter,
                            canEditLimits = canEditLimits,
                            onEditLimit = { summary ->
                                editingSummary = summary
                                limitText = summary.limitValue.toString()
                            }
                        )
                        Text(
                            text = state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            UsageLimitsOverviewCard(
                                summaries = state.limitSummaries,
                                numberFormatter = numberFormatter,
                                canEditLimits = canEditLimits,
                                onEditLimit = { summary ->
                                    editingSummary = summary
                                    limitText = summary.limitValue.toString()
                                }
                            )
                        }
                        if (state.alerts.isEmpty()) {
                            item {
                                Text(
                                    text = "Sin alertas por ahora.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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

@Composable
private fun UsageLimitsOverviewCard(
    summaries: List<UsageLimitSummary>,
    numberFormatter: NumberFormat,
    canEditLimits: Boolean,
    onEditLimit: (UsageLimitSummary) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Consumo y topes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (canEditLimits) {
                    Text(
                        text = "Editable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            summaries.forEach { summary ->
                UsageLimitRow(
                    summary = summary,
                    numberFormatter = numberFormatter,
                    canEditLimits = canEditLimits,
                    onEditLimit = onEditLimit
                )
            }
        }
    }
}

@Composable
private fun UsageLimitRow(
    summary: UsageLimitSummary,
    numberFormatter: NumberFormat,
    canEditLimits: Boolean,
    onEditLimit: (UsageLimitSummary) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(summary.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Uso ${numberFormatter.format(summary.currentValue)} · Tope ${numberFormatter.format(summary.limitValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canEditLimits) {
                TextButton(onClick = { onEditLimit(summary) }) {
                    Text("Editar")
                }
            }
        }
        UsageLimitBar(
            percentage = summary.percentage,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UsageLimitBar(
    percentage: Int,
    modifier: Modifier = Modifier
) {
    val clamped = percentage.coerceIn(0, 150)
    val thresholds = listOf(50, 80, 100)

    // ✅ Tomar colores en contexto @Composable (afuera del draw scope)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val barColor =
        if (clamped >= 100) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.height(12.dp)) {
        val radius = size.height / 2
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius)
        )

        val width = size.width * (clamped / 100f)
        drawRoundRect(
            color = barColor,
            size = Size(width.coerceAtMost(size.width), size.height),
            cornerRadius = CornerRadius(radius, radius)
        )

        thresholds.forEach { threshold ->
            val x = size.width * (threshold / 100f)
            drawLine(
                color = tickColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 2f
            )
        }
    }
}
