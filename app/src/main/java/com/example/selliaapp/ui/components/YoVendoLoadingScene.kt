package com.example.selliaapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.selliaapp.auth.AuthLoadingUiState

@Composable
fun YoVendoLoadingScene(
    loadingUiState: AuthLoadingUiState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "yovendo-loading")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val progress = loadingUiState.progress.coerceIn(0f, 1f)
    val sellerColor = MaterialTheme.colorScheme.primary
    val buyerColor = MaterialTheme.colorScheme.secondary
    val packageColor = Color(0xFFF59E0B)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val trackStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val laneColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YoVendo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Preparando tu operación",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val centerY = size.height * 0.55f
            val sellerX = size.width * 0.2f
            val buyerX = size.width * 0.8f
            val personRadius = size.minDimension * 0.08f

            drawCircle(
                color = sellerColor.copy(alpha = pulse),
                radius = personRadius,
                center = Offset(sellerX, centerY)
            )
            drawCircle(
                color = buyerColor.copy(alpha = pulse),
                radius = personRadius,
                center = Offset(buyerX, centerY)
            )

            val laneTop = centerY - (personRadius * 0.4f)
            val laneHeight = personRadius * 0.8f
            drawRoundRect(
                color = laneColor,
                topLeft = Offset(sellerX + personRadius, laneTop),
                size = androidx.compose.ui.geometry.Size(
                    width = (buyerX - sellerX) - (personRadius * 2),
                    height = laneHeight
                ),
                cornerRadius = CornerRadius(laneHeight / 2)
            )

            val packageX = sellerX + personRadius + (((buyerX - sellerX) - (personRadius * 2)) * progress)
            val packageSize = personRadius * 0.9f
            drawRoundRect(
                color = packageColor,
                topLeft = Offset(packageX - packageSize / 2, centerY - packageSize / 2),
                size = androidx.compose.ui.geometry.Size(packageSize, packageSize),
                cornerRadius = CornerRadius(8f)
            )

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, size.height - 16.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = sellerColor,
                topLeft = Offset(0f, size.height - 16.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width * progress, 8.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx())
            )

            drawRoundRect(
                color = trackStrokeColor,
                topLeft = Offset(0f, size.height - 16.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Text(
            text = loadingUiState.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
