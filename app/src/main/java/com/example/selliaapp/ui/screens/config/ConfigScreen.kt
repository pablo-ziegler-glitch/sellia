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
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Assessment
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
    onUsageAlerts: () -> Unit,
    onManageUsers: () -> Unit,
    canManageUsers: Boolean,
    onStoreSettings: () -> Unit = {},
    onDevelopmentOptions: () -> Unit,
    showDevelopmentOptions: Boolean,
    adminFeatureFlags: ConfigAdminFeatureFlags,
    isClientFinal: Boolean,
    onStorefront: () -> Unit = {},
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
                    title = "Sincronización y diagnóstico",
                    onClick = onSync
                )
                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "Estado de cuenta",
                    onClick = onUsageAlerts
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Administrativas (App)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                AdminActionItem(
                    title = "Usuarios y roles",
                    mobileEnabled = adminFeatureFlags.usersAndRolesEnabled && canManageUsers,
                    onMobileClick = onManageUsers
                )
                AdminActionItem(
                    title = "Servicios en la nube",
                    mobileEnabled = adminFeatureFlags.cloudServicesEnabled && canManageCloudServices,
                    onMobileClick = onCloudServicesAdmin
                )
                AdminActionItem(
                    title = "Pricing global",
                    mobileEnabled = adminFeatureFlags.globalPricingEnabled,
                    onMobileClick = onPricingConfig
                )
                AdminActionItem(
                    title = "Cargas masivas y ABMs",
                    mobileEnabled = adminFeatureFlags.bulkAbmEnabled,
                    onMobileClick = onBulkData
                )
                AdminActionItem(
                    title = "Configuraciones de tienda",
                    mobileEnabled = adminFeatureFlags.storeSettingsEnabled,
                    onMobileClick = onStoreSettings
                )

                if (adminFeatureFlags.marketingConfigEnabled) {
                    SettingsItem(
                        icon = Icons.Filled.Campaign,
                        title = "Configuraciones de tienda (legacy)",
                        onClick = onMarketingConfig
                    )
                }

                if (!isClientFinal) {
                    SettingsItem(
                        icon = Icons.Filled.Storefront,
                        title = "Vidriera Pública",
                        onClick = onStorefront
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

data class ConfigAdminFeatureFlags(
    val usersAndRolesEnabled: Boolean,
    val cloudServicesEnabled: Boolean,
    val globalPricingEnabled: Boolean,
    val tenantLifecycleEnabled: Boolean,
    val bulkAbmEnabled: Boolean,
    val marketingConfigEnabled: Boolean,
    val productQrsEnabled: Boolean,
    val securitySettingsEnabled: Boolean,
    val publicCatalogConfigEnabled: Boolean,
    val storeSettingsEnabled: Boolean = true
) {
    companion object {
        /**
         * Estado objetivo: operación administrativa completa desde la app.
         */
        val MobileFieldOnly = ConfigAdminFeatureFlags(
            usersAndRolesEnabled = true,
            cloudServicesEnabled = true,
            globalPricingEnabled = true,
            tenantLifecycleEnabled = false,
            bulkAbmEnabled = true,
            marketingConfigEnabled = true,
            productQrsEnabled = true,
            securitySettingsEnabled = true,
            publicCatalogConfigEnabled = true,
            storeSettingsEnabled = true
        )
    }
}

@Composable
private fun AdminActionItem(
    title: String,
    mobileEnabled: Boolean,
    onMobileClick: () -> Unit
) {
    if (!mobileEnabled) return
    SettingsItem(
        icon = Icons.Filled.AdminPanelSettings,
        title = title,
        onClick = onMobileClick
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
