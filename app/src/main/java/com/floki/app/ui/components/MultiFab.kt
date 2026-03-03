package com.floki.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * FAB tipo "speed-dial": un botón principal con "+", que al expandirse
 * muestra 1..N acciones verticalmente (cada acción es un minifab rotulado).
 *
 * Este componente es agnóstico; la pantalla define qué acciones pasarle.
 */
@Composable
fun MultiFab(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<MiniFabAction>
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Acciones visibles solo si expanded = true
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                actions.forEach { action ->
                    MiniFab(action = action)
                }
            }
        }

        // FAB principal con "+"
        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (expanded) "Cerrar acciones" else "Abrir acciones"
            )
        }
    }
}

/**
 * Datos de una acción del speed-dial.
 */
data class MiniFabAction(
    val label: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit
)

/**
 * Render de mini-fab "etiquetado": un chip elevado con icono + texto.
 * Usamos un Surface clickeable para ganar jerarquía visual simple.
 */
@Composable
private fun MiniFab(action: MiniFabAction) {
    Surface(
        onClick = action.onClick,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .semantics { contentDescription = action.label }
            .shadow(4.dp, MaterialTheme.shapes.large)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .size(height = 40.dp, width = 0.dp) // width flexible por contenido
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            action.icon()
            Text(text = action.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
