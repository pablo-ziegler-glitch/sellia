package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.selliaapp.ui.components.BackTopAppBar

/**
 * Hub de configuraciones de tienda — espejo mobile del backoffice web.
 *
 * Agrupa las opciones que normalmente se administran desde el backoffice web
 * (#/settings/store, #/settings/marketing, #/settings/public_catalog) para que
 * puedan editarse directamente desde la aplicación sin necesidad de acceder al panel web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(
    onBack: () -> Unit,
    onMarketingConfig: () -> Unit,
    onPublicCatalogConfig: () -> Unit,
    onStorefront: () -> Unit,
    onLocationConfig: () -> Unit
) {
    Scaffold(
        topBar = { BackTopAppBar(title = "Configuraciones de tienda", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Configurá tu tienda desde la app",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Estas opciones reflejan las mismas configuraciones disponibles en el Backoffice Web.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column {
                    StoreSettingsItem(
                        icon = Icons.Filled.Campaign,
                        title = "Datos de la tienda",
                        description = "Nombre, logo, URL pública, contacto y colores.",
                        onClick = onMarketingConfig
                    )
                    HorizontalDivider()
                    StoreSettingsItem(
                        icon = Icons.Filled.Storefront,
                        title = "Catálogo público",
                        description = "Dominio, sincronización y visibilidad del catálogo online.",
                        onClick = onPublicCatalogConfig
                    )
                    HorizontalDivider()
                    StoreSettingsItem(
                        icon = Icons.Filled.Storefront,
                        title = "Vidriera pública",
                        description = "Vista previa de la vidriera pública de productos.",
                        onClick = onStorefront
                    )
                    HorizontalDivider()
                    StoreSettingsItem(
                        icon = Icons.Filled.LocationOn,
                        title = "Ubicación y tipo de tienda",
                        description = "Configurá si tu tienda es física, online o ambas.",
                        onClick = onLocationConfig
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StoreSettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
    )
}
