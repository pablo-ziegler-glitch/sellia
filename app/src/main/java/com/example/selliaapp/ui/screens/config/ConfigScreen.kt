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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
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
    onAddUser: () -> Unit,
    onManageProducts: () -> Unit,
    onManageCustomers: () -> Unit,
    onSync: () -> Unit,
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
                    icon = Icons.Filled.AccountCircle,
                    title = "Usuarios",
                    onClick = onAddUser
                )
                SettingsItem(
                    icon = Icons.Filled.Inventory2, // inventario/productos
                    title = "Productos",
                    onClick = onManageProducts
                )
                SettingsItem(
                    icon = Icons.Filled.Group, // clientes
                    title = "Clientes",
                    onClick = onManageCustomers
                )
                SettingsItem(
                    icon = Icons.Filled.CloudSync,
                    title = "Sincronización",
                    onClick = onSync
                )
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
