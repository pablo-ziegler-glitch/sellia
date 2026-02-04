package com.example.selliaapp.ui.screens.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.model.usage.UsageSeriesPoint
import com.example.selliaapp.data.model.usage.UsageServiceSummary
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.admin.UsageDashboardViewModel
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboardScreen(
    vm: UsageDashboardViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BackTopAppBar(title = "Consumo de servicios", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            HeaderCard(
                total = state.total,
                rangeLabel = state.rangeLabel
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Serie temporal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            UsageLineChart(
                points = state.series,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Consumo por servicio/app",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            val errorMessage = state.errorMessage
            if (state.isLoading) {
                LoadingState()
            } else if (errorMessage != null) {
                ErrorState(message = errorMessage)
            } else if (state.services.isEmpty()) {
                EmptyState(message = "Todavía no hay datos de consumo para este período.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.services.forEach { summary ->
                        UsageServiceCard(summary = summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(total: Double, rangeLabel: String) {
    val currencyFormatter = rememberCurrencyFormatter()
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Consumo total",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Período: $rangeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsageLineChart(
    points: List<UsageSeriesPoint>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val chartHeight = 180.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }

            points.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin datos para graficar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    drawRoundRect(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f)
                    )

                    val maxValue = points.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
                    val minValue = points.minOf { it.value }
                    val verticalRange = (maxValue - minValue).takeIf { it > 0 } ?: 1.0

                    val widthStep = if (points.size > 1) size.width / (points.size - 1) else 0f
                    val linePath = Path()
                    val lineColor = MaterialTheme.colorScheme.primary

                    points.forEachIndexed { index, point ->
                        val x = widthStep * index
                        val normalized = (point.value - minValue) / verticalRange
                        val y = size.height - (normalized * size.height).toFloat()

                        if (index == 0) {
                            linePath.moveTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    points.forEachIndexed { index, point ->
                        val x = widthStep * index
                        val normalized = (point.value - minValue) / verticalRange
                        val y = size.height - (normalized * size.height).toFloat()
                        drawCircle(
                            color = lineColor,
                            radius = 6f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageServiceCard(summary: UsageServiceSummary) {
    val currencyFormatter = rememberCurrencyFormatter()
    val trendColor = if (summary.trendPercent >= 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = summary.serviceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary.appName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(summary.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Variación",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPercent(summary.trendPercent),
                        style = MaterialTheme.typography.titleSmall,
                        color = trendColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            UsageShareBar(sharePercent = summary.sharePercent)
        }
    }
}

@Composable
private fun UsageShareBar(sharePercent: Double) {
    val clamped = sharePercent.coerceIn(0.0, 100.0)
    val percentLabel = formatPercent(sharePercent)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Participación",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = percentLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val barWidth = (size.width * (clamped / 100.0)).coerceAtLeast(0.0).toFloat()
            val radius = size.height / 2
            drawRoundRect(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
            drawRoundRect(
                color = MaterialTheme.colorScheme.primary,
                size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorState(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun rememberCurrencyFormatter(): NumberFormat {
    val locale = Locale.getDefault()
    return remember(locale) { NumberFormat.getCurrencyInstance(locale) }
}

private fun formatPercent(value: Double): String {
    val formatted = String.format(Locale.getDefault(), "%.1f%%", value)
    return if (value > 0) "+$formatted" else formatted
}
