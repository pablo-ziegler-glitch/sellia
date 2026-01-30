package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.selliaapp.ui.components.BackTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onPricingConfig: () -> Unit,
    onMarketingConfig: () -> Unit,
    onSync: () -> Unit,
    onBulkData: () -> Unit,
    onCloudServicesAdmin: () -> Unit,
    canManageCloudServices: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            BackTopAppBar(title = "Configuración", onBack = onBack)
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Perfil de usuario
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar por defecto
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Nombre de usuario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("usuario@example.com", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Menú
                SettingsItem(
                    icon = Icons.Filled.Campaign,
                    title = "Campañas de marketing",
                    onClick = onMarketingConfig
                )
                SettingsItem(
                    icon = Icons.Filled.UploadFile,
                    title = "Cargas masivas y ABMs",
                    onClick = onBulkData
                )
                SettingsItem(
                    icon = Icons.Filled.CloudSync,
                    title = "Sincronización",
                    onClick = onSync
                )
                SettingsItem(
                    icon = Icons.Filled.ChevronRight,
                    title = "Pricing y costos",
                    onClick = onPricingConfig
                )
                if (canManageCloudServices) {
                    SettingsItem(
                        icon = Icons.Filled.CloudSync,
                        title = "Servicios en la nube (Admin)",
                        onClick = onCloudServicesAdmin
                    )
                }
            }
        }
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp)
    )
    HorizontalDivider()
}
