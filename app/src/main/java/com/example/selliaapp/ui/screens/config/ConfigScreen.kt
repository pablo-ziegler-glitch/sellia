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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.AccountAvatar
import com.example.selliaapp.ui.components.AccountSummary
import com.example.selliaapp.ui.components.BackTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onPricingConfig: () -> Unit,
    onMarketingConfig: () -> Unit,
    onSync: () -> Unit,
    onBulkData: () -> Unit,
    onCloudServicesAdmin: () -> Unit,
    canManageCloudServices: Boolean,
    onDevelopmentOptions: () -> Unit,
    showDevelopmentOptions: Boolean,
    onBack: () -> Unit
) {
    var showProfileDetails by remember { mutableStateOf(false) }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showProfileDetails = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccountAvatar(
                            avatarUrl = accountSummary.avatarUrl,
                            displayName = accountSummary.displayName,
                            size = 48.dp
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                accountSummary.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                accountSummary.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                accountSummary.roleLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                SettingsItem(
                    icon = Icons.Filled.Lock,
                    title = "Seguridad y accesos",
                    onClick = onSecuritySettings
                )
                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "Alertas de uso",
                    onClick = onUsageAlerts
                )
                if (canManageUsers) {
                    SettingsItem(
                        icon = Icons.Filled.Badge,
                        title = "Usuarios y roles",
                        onClick = onManageUsers
                    )
                }
                if (canManageCloudServices) {
                    SettingsItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = "Servicios en la nube (Admin)",
                        onClick = onCloudServicesAdmin
                    )
                }
                if (showDevelopmentOptions) {
                    SettingsItem(
                        icon = Icons.Filled.Assessment,
                        title = "Opciones Desarrollo",
                        onClick = onDevelopmentOptions
                    )
                }
            }
        }
    )

    if (showProfileDetails) {
        ModalBottomSheet(onDismissRequest = { showProfileDetails = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountAvatar(
                        avatarUrl = accountSummary.avatarUrl,
                        displayName = accountSummary.displayName,
                        size = 56.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        userProfile.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = userProfile.roleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                UserDetailRow(label = "UID", value = userProfile.uid ?: "No disponible")
                UserDetailRow(label = "Tenant ID", value = userProfile.tenantId ?: "No disponible")
            }
        }
    }
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

data class UserProfileDetails(
    val displayName: String,
    val email: String?,
    val roleLabel: String,
    val uid: String?,
    val tenantId: String?
)

@Composable
private fun UserDetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
