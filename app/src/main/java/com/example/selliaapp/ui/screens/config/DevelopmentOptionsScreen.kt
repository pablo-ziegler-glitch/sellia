package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.domain.config.DevelopmentFeatureKey
import com.example.selliaapp.viewmodel.config.DevelopmentOptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopmentOptionsScreen(
    onBack: () -> Unit,
    viewModel: DevelopmentOptionsViewModel = hiltViewModel()
) {
    val owners by viewModel.owners.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opciones Desarrollo") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Configuración por dueño de tienda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Definí qué funcionalidades quedan habilitadas por tienda.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            items(owners, key = { it.ownerEmail }) { owner ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(owner.ownerName, fontWeight = FontWeight.Bold)
                                Text(owner.ownerEmail, style = MaterialTheme.typography.bodySmall)
                            }
                            OwnerStatusChip(isActive = owner.isActive)
                        }
                        Spacer(Modifier.height(12.dp))
                        DevelopmentFeatureKey.entries.forEach { feature ->
                            FeatureSwitch(
                                title = feature.label,
                                checked = owner.config.isEnabled(feature),
                                onCheckedChange = { viewModel.setFeature(owner.ownerEmail, feature, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerStatusChip(isActive: Boolean) {
    val label = if (isActive) "Activa" else "Inactiva"
    val background = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Surface(
        color = background,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FeatureSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
