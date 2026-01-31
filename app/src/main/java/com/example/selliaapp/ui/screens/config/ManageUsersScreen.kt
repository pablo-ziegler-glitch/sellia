package com.example.selliaapp.ui.screens.config

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.menuAnchor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.model.User
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    vm: UserViewModel,
    onBack: () -> Unit,
    canManageUsers: Boolean
) {
    val users by vm.user.collectAsStateWithLifecycle()
    var editorUser by remember { mutableStateOf<User?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    if (showEditor) {
        UserEditorDialog(
            user = editorUser,
            canManageUsers = canManageUsers,
            onDismiss = { showEditor = false },
            onSave = { name, email, role, isActive ->
                if (editorUser == null) {
                    vm.addUser(name, email, role.raw, isActive)
                } else {
                    vm.updateUser(
                        editorUser!!.copy(
                            name = name,
                            email = email,
                            role = role.raw,
                            isActive = isActive
                        )
                    )
                }
                showEditor = false
                editorUser = null
            }
        )
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Usuarios y roles",
                onBack = onBack,
                actions = {
                    if (canManageUsers) {
                        IconButton(
                            onClick = {
                                editorUser = null
                                showEditor = true
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Agregar usuario")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Administrá accesos, roles y estados de tu equipo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!canManageUsers) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column {
                            Text(
                                text = "Acceso restringido",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tu rol no tiene permiso para gestionar usuarios.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                return@Column
            }

            if (users.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No hay usuarios cargados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Agregá usuarios para asignar roles y permisos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = {
                            editorUser = null
                            showEditor = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Agregar usuario")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(users, key = { it.id }) { user ->
                        UserItem(
                            user = user,
                            onEdit = {
                                editorUser = user
                                showEditor = true
                            },
                            onToggleActive = { enabled ->
                                vm.updateUser(user.copy(isActive = enabled))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserItem(
    user: User,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit
) {
    val role = AppRole.fromRaw(user.role)
    val containerColor by animateColorAsState(
        if (user.isActive) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        },
        label = "userCardColor"
    )
    val statusColor by animateColorAsState(
        if (user.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "userStatusColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar usuario")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(role.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                AnimatedContent(targetState = user.isActive, label = "statusLabel") { isActive ->
                    Text(
                        text = if (isActive) "Habilitado" else "Deshabilitado",
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = user.isActive, onCheckedChange = onToggleActive)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserEditorDialog(
    user: User?,
    canManageUsers: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, role: AppRole, isActive: Boolean) -> Unit
) {
    var name by remember(user) { mutableStateOf(user?.name.orEmpty()) }
    var email by remember(user) { mutableStateOf(user?.email.orEmpty()) }
    var selectedRole by remember(user) { mutableStateOf(AppRole.fromRaw(user?.role)) }
    var isActive by remember(user) { mutableStateOf(user?.isActive ?: true) }
    var roleExpanded by remember { mutableStateOf(false) }
    val roleOptions = remember { AppRole.entries }
    val canSave = name.isNotBlank() && email.isNotBlank() && canManageUsers

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (user == null) "Nuevo usuario" else "Editar usuario")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre completo") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canManageUsers
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canManageUsers
                )
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = !roleExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRole.label,
                        onValueChange = {},
                        label = { Text("Rol") },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = canManageUsers
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roleOptions.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.label) },
                                onClick = {
                                    selectedRole = role
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Usuario habilitado",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        enabled = canManageUsers
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), email.trim(), selectedRole, isActive) },
                enabled = canSave
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
