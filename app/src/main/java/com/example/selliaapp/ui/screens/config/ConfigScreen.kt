package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
    accountSummary: AccountSummary,
    userProfile: UserProfileDetails,
    onPricingConfig: () -> Unit,
    onMarketingConfig: () -> Unit,
    onSync: () -> Unit,
    onProductQrs: () -> Unit,
    onBulkData: () -> Unit,
    onCloudServicesAdmin: () -> Unit,
    canManageCloudServices: Boolean,
    onSecuritySettings: () -> Unit,
    onAppVersion: () -> Unit,
    onUsageAlerts: () -> Unit,
    onManageUsers: () -> Unit,
    canManageUsers: Boolean,
    onTenantDeactivation: () -> Unit,
    onTenantReactivation: () -> Unit,
    onTenantDelete: (String, String) -> Unit,
    tenantActionFeedback: String?,
    tenantActionError: String?,
    onDevelopmentOptions: () -> Unit,
    showDevelopmentOptions: Boolean,
    onSupport: () -> Unit,
    onOpenBackofficeWeb: (BackofficeModule) -> Unit,
    adminFeatureFlags: ConfigAdminFeatureFlags,
    isClientFinal: Boolean,
    onBack: () -> Unit
) {
    var showProfileDetails by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var confirmTenantId by remember { mutableStateOf("") }
    var confirmPhrase by remember { mutableStateOf("") }
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

                if (isClientFinal) {
                    Text(
                        text = "Solo tenés acceso a tus datos de perfil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    return@Column
                }

                Text(
                    text = "Operativas (campo)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                SettingsItem(
                    icon = Icons.Filled.CloudSync,
                    title = "Sincronización",
                    onClick = onSync
                )
                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "Estado de cuenta",
                    onClick = onUsageAlerts
                )
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "Soporte",
                    onClick = onSupport
                )
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "Versión de la app",
                    onClick = onAppVersion
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Administrativas (Backoffice Web)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                AdminActionItem(
                    title = "Usuarios y roles",
                    module = BackofficeModule.USERS_AND_ROLES,
                    mobileEnabled = adminFeatureFlags.usersAndRolesEnabled && canManageUsers,
                    onMobileClick = onManageUsers,
                    onWebClick = onOpenBackofficeWeb
                )
                AdminActionItem(
                    title = "Servicios en la nube",
                    module = BackofficeModule.CLOUD_SERVICES,
                    mobileEnabled = adminFeatureFlags.cloudServicesEnabled && canManageCloudServices,
                    onMobileClick = onCloudServicesAdmin,
                    onWebClick = onOpenBackofficeWeb
                )
                AdminActionItem(
                    title = "Pricing global",
                    module = BackofficeModule.GLOBAL_PRICING,
                    mobileEnabled = adminFeatureFlags.globalPricingEnabled,
                    onMobileClick = onPricingConfig,
                    onWebClick = onOpenBackofficeWeb
                )
                AdminActionItem(
                    title = "Tenant lifecycle",
                    module = BackofficeModule.TENANT_LIFECYCLE,
                    mobileEnabled = adminFeatureFlags.tenantLifecycleEnabled,
                    onMobileClick = onTenantDeactivation,
                    onWebClick = onOpenBackofficeWeb
                )
                AdminActionItem(
                    title = "Cargas masivas y ABMs",
                    module = BackofficeModule.BULK_ABM,
                    mobileEnabled = adminFeatureFlags.bulkAbmEnabled,
                    onMobileClick = onBulkData,
                    onWebClick = onOpenBackofficeWeb
                )

                if (adminFeatureFlags.marketingConfigEnabled) {
                    SettingsItem(
                        icon = Icons.Filled.Campaign,
                        title = "Configuraciones de tienda",
                        onClick = onMarketingConfig
                    )
                }

                if (adminFeatureFlags.productQrsEnabled) {
                    SettingsItem(
                        icon = Icons.Filled.QrCode2,
                        title = "QRs Productos",
                        onClick = onProductQrs
                    )
                }
                if (adminFeatureFlags.securitySettingsEnabled) {
                    SettingsItem(
                        icon = Icons.Filled.Lock,
                        title = "Seguridad y accesos",
                        onClick = onSecuritySettings
                    )
                }

                if (adminFeatureFlags.tenantLifecycleEnabled) {
                    SettingsItem(
                        icon = Icons.Filled.Lock,
                        title = "Solicitar reactivación tienda",
                        onClick = onTenantReactivation
                    )
                    SettingsItem(
                        icon = Icons.Filled.Lock,
                        title = "Eliminar tienda (doble check)",
                        onClick = { showDeleteDialog = true }
                    )
                }
                tenantActionFeedback?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                tenantActionError?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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


    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar tienda") },
            text = {
                Column {
                    Text("Esta acción es irreversible. Confirmá tenant ID y escribí ELIMINAR.")
                    TextField(value = confirmTenantId, onValueChange = { confirmTenantId = it }, label = { Text("Tenant ID") })
                    TextField(value = confirmPhrase, onValueChange = { confirmPhrase = it }, label = { Text("Escribí ELIMINAR") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onTenantDelete(confirmTenantId, confirmPhrase)
                    showDeleteDialog = false
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}


enum class BackofficeModule(val slug: String) {
    USERS_AND_ROLES("users_roles"),
    CLOUD_SERVICES("cloud_services"),
    GLOBAL_PRICING("global_pricing"),
    TENANT_LIFECYCLE("tenant_lifecycle"),
    BULK_ABM("bulk_abm")
}

data class ConfigAdminFeatureFlags(
    val usersAndRolesEnabled: Boolean,
    val cloudServicesEnabled: Boolean,
    val globalPricingEnabled: Boolean,
    val tenantLifecycleEnabled: Boolean,
    val bulkAbmEnabled: Boolean,
    val marketingConfigEnabled: Boolean,
    val productQrsEnabled: Boolean,
    val securitySettingsEnabled: Boolean
) {
    companion object {
        /**
         * Estado objetivo: mobile sólo para operación de campo.
         * Dejar flags en false permite apagar módulos admin sin romper navegación
         * porque cada acción conserva fallback "Abrir en Backoffice Web".
         */
        val MobileFieldOnly = ConfigAdminFeatureFlags(
            usersAndRolesEnabled = false,
            cloudServicesEnabled = false,
            globalPricingEnabled = false,
            tenantLifecycleEnabled = false,
            bulkAbmEnabled = false,
            marketingConfigEnabled = false,
            productQrsEnabled = false,
            securitySettingsEnabled = false
        )
    }
}

@Composable
private fun AdminActionItem(
    title: String,
    module: BackofficeModule,
    mobileEnabled: Boolean,
    onMobileClick: () -> Unit,
    onWebClick: (BackofficeModule) -> Unit
) {
    if (mobileEnabled) {
        SettingsItem(
            icon = Icons.Filled.AdminPanelSettings,
            title = "$title (Mobile heredado)",
            onClick = onMobileClick
        )
    } else {
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            title = "$title · Abrir en Backoffice Web",
            onClick = { onWebClick(module) }
        )
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
