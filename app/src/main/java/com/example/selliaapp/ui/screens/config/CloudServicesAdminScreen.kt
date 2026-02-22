package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val uiState by viewModel.uiState.collectAsState()
    val selectedOwner = uiState.selectedOwner

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
                    text = if (uiState.isAdmin) "Configuración por tienda" else "Configuración de tu tienda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Por defecto cada dispositivo trabaja con Room local. Activá servicios cloud sólo si el dueño lo necesita.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.owners.isEmpty()) {
                item {
                    Text(
                        text = "No hay tiendas disponibles para configurar.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@LazyColumn
            }

            if (uiState.isAdmin) {
                item {
                    OwnerSelector(
                        ownerOptions = uiState.owners.map { it.ownerEmail to it.ownerName },
                        selectedOwnerEmail = selectedOwner?.ownerEmail.orEmpty(),
                        onOwnerSelected = viewModel::selectOwner
                    )
                }
            }

            selectedOwner?.let { owner ->
                item {
                    val config = owner.config
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(owner.ownerName, fontWeight = FontWeight.Bold)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerSelector(
    ownerOptions: List<Pair<String, String>>,
    selectedOwnerEmail: String,
    onOwnerSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        androidx.compose.material3.OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = ownerOptions.firstOrNull { it.first == selectedOwnerEmail }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Tienda") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ownerOptions.forEach { (ownerEmail, ownerName) ->
                DropdownMenuItem(
                    text = { Text(ownerName) },
                    onClick = {
                        onOwnerSelected(ownerEmail)
                        expanded = false
                    }
                )
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
