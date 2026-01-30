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
import com.example.selliaapp.viewmodel.config.CloudServicesAdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudServicesAdminScreen(
    onBack: () -> Unit,
    viewModel: CloudServicesAdminViewModel = hiltViewModel()
) {
    val owners by viewModel.owners.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servicios en la nube") },
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
                    text = "Por defecto cada dispositivo trabaja con Room local. Activá servicios cloud sólo si el dueño lo necesita.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            items(owners, key = { it.ownerEmail }) { owner ->
                val config = owner.config
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(owner.ownerName, fontWeight = FontWeight.Bold)
                        Text(owner.ownerEmail, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        SettingSwitch(
                            title = "Servicios en la nube",
                            description = if (config.cloudEnabled) {
                                "Cloud activo para esta tienda."
                            } else {
                                "Solo local (Room)."
                            },
                            checked = config.cloudEnabled,
                            onCheckedChange = { viewModel.setCloudEnabled(owner.ownerEmail, it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingSwitch(
                            title = "Backup Firestore",
                            description = "Copia de seguridad en Firebase (Firestore).",
                            checked = config.firestoreBackupEnabled,
                            enabled = config.cloudEnabled,
                            onCheckedChange = { viewModel.setFirestoreBackup(owner.ownerEmail, it) }
                        )
                        SettingSwitch(
                            title = "Firebase Auth",
                            description = "Sincronización de usuarios y sesiones.",
                            checked = config.authSyncEnabled,
                            enabled = config.cloudEnabled,
                            onCheckedChange = { viewModel.setAuthSync(owner.ownerEmail, it) }
                        )
                        SettingSwitch(
                            title = "Firebase Storage",
                            description = "Backup de imágenes/archivos.",
                            checked = config.storageBackupEnabled,
                            enabled = config.cloudEnabled,
                            onCheckedChange = { viewModel.setStorageBackup(owner.ownerEmail, it) }
                        )
                        SettingSwitch(
                            title = "Cloud Functions",
                            description = "Automatizaciones serverless y webhooks.",
                            checked = config.functionsEnabled,
                            enabled = config.cloudEnabled,
                            onCheckedChange = { viewModel.setFunctionsEnabled(owner.ownerEmail, it) }
                        )
                        SettingSwitch(
                            title = "Firebase Hosting",
                            description = "Publicación de la web pública/landing.",
                            checked = config.hostingEnabled,
                            enabled = config.cloudEnabled,
                            onCheckedChange = { viewModel.setHostingEnabled(owner.ownerEmail, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
