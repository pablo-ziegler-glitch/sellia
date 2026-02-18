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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.menuAnchor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.model.User
import com.example.selliaapp.data.model.onboarding.AccountRequest
import com.example.selliaapp.data.model.onboarding.AccountRequestStatus
import com.example.selliaapp.data.model.onboarding.AccountRequestType
import com.example.selliaapp.data.model.onboarding.BusinessModule
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.UserViewModel
import com.example.selliaapp.viewmodel.TenantOwnershipViewModel
import com.example.selliaapp.viewmodel.admin.AccountRequestsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    vm: UserViewModel,
    requestsViewModel: AccountRequestsViewModel,
    ownershipViewModel: TenantOwnershipViewModel,
    onBack: () -> Unit,
    canManageUsers: Boolean
) {
    val users by vm.user.collectAsStateWithLifecycle()
    val requestsState by requestsViewModel.state.collectAsStateWithLifecycle()
    var editorUser by remember { mutableStateOf<User?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val ownershipState by ownershipViewModel.state.collectAsStateWithLifecycle()
    var ownershipDialog by remember { mutableStateOf<OwnershipActionDialogState?>(null) }

    ownershipDialog?.let { dialogState ->
        OwnershipActionDialog(
            state = dialogState,
            isLoading = ownershipState.isLoading,
            onDismiss = { ownershipDialog = null },
            onConfirm = { targetEmail, keepPreviousOwnerAccess ->
                when (dialogState.action) {
                    OwnershipAction.DELEGATE_STORE -> ownershipViewModel.delegateStore(targetEmail)
                    OwnershipAction.ASSOCIATE_OWNER -> ownershipViewModel.associateOwner(targetEmail)
                    OwnershipAction.TRANSFER_PRIMARY_OWNER -> ownershipViewModel.transferPrimaryOwner(
                        targetEmail,
                        keepPreviousOwnerAccess
                    )
                }
                ownershipDialog = null
            }
        )
    }

    if (showEditor) {
        UserEditorDialog(
            user = editorUser,
            canManageUsers = canManageUsers,
            onDismiss = { showEditor = false },
            onSave = { name, email, role, isActive ->
                val normalizedRole = normalizeAssignableRole(role)
                if (editorUser == null) {
                    vm.addUser(name, email, normalizedRole.raw, isActive)
                } else {
                    vm.updateUser(
                        editorUser!!.copy(
                            name = name,
                            email = email,
                            role = normalizedRole.raw,
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

            if (canManageUsers) {
                OwnershipManagementPanel(
                    isLoading = ownershipState.isLoading,
                    message = ownershipState.message,
                    error = ownershipState.error,
                    onClearMessage = ownershipViewModel::clearMessage,
                    onDelegateStore = { ownershipDialog = OwnershipActionDialogState(OwnershipAction.DELEGATE_STORE) },
                    onAssociateOwner = { ownershipDialog = OwnershipActionDialogState(OwnershipAction.ASSOCIATE_OWNER) },
                    onTransferOwner = { ownershipDialog = OwnershipActionDialogState(OwnershipAction.TRANSFER_PRIMARY_OWNER) }
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Usuarios") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Solicitudes") }
                )
            }

            when (selectedTab) {
                0 -> UsersTab(
                    users = users,
                    onAddUser = {
                        editorUser = null
                        showEditor = true
                    },
                    onEditUser = { user ->
                        editorUser = user
                        showEditor = true
                    },
                    onToggleActive = { user, enabled ->
                        vm.updateUser(user.copy(isActive = enabled))
                    }
                )
                1 -> AccountRequestsTab(
                    state = requestsState,
                    onRefresh = requestsViewModel::refresh,
                    onUpdate = requestsViewModel::updateRequest
                )
            }
        }
    }
}


private enum class OwnershipAction(val title: String, val description: String) {
    DELEGATE_STORE(
        title = "Delegar tienda",
        description = "Asigna acceso de encargado/a sin transferir la titularidad principal."
    ),
    ASSOCIATE_OWNER(
        title = "Asociar co-dueño/a",
        description = "Agrega un dueño adicional para operar la misma tienda sin pérdida de información."
    ),
    TRANSFER_PRIMARY_OWNER(
        title = "Cambiar dueño principal",
        description = "Transfiere la titularidad principal. Opcionalmente podés conservar acceso del dueño actual."
    )
}

private data class OwnershipActionDialogState(
    val action: OwnershipAction
)

@Composable
private fun OwnershipManagementPanel(
    isLoading: Boolean,
    message: String?,
    error: String?,
    onClearMessage: () -> Unit,
    onDelegateStore: () -> Unit,
    onAssociateOwner: () -> Unit,
    onTransferOwner: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Titularidad y delegación de tienda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Estas acciones mantienen intacto el historial (ventas, stock, caja y reportes) porque solo cambian permisos y dueños del tenant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDelegateStore, enabled = !isLoading) {
                    Text("Delegar tienda")
                }
                Button(onClick = onAssociateOwner, enabled = !isLoading) {
                    Text("Agregar co-dueño")
                }
                Button(onClick = onTransferOwner, enabled = !isLoading) {
                    Text("Cambiar dueño")
                }
            }
            if (!message.isNullOrBlank()) {
                AssistChip(onClick = onClearMessage, label = { Text(message) })
            }
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun OwnershipActionDialog(
    state: OwnershipActionDialogState,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (targetEmail: String, keepPreviousOwnerAccess: Boolean) -> Unit
) {
    var targetEmail by remember { mutableStateOf("") }
    var keepPreviousOwnerAccess by remember { mutableStateOf(true) }
    val canConfirm = targetEmail.isNotBlank() && !isLoading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.action.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.action.description)
                OutlinedTextField(
                    value = targetEmail,
                    onValueChange = { targetEmail = it.trim() },
                    label = { Text("Email del usuario") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.action == OwnershipAction.TRANSFER_PRIMARY_OWNER) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = keepPreviousOwnerAccess,
                            onCheckedChange = { keepPreviousOwnerAccess = it }
                        )
                        Text("Mantener acceso del dueño anterior como co-dueño")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(targetEmail, keepPreviousOwnerAccess) },
                enabled = canConfirm
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}


private fun normalizeAssignableRole(role: AppRole): AppRole = when (role) {
    AppRole.MANAGER,
    AppRole.CASHIER -> role

    else -> AppRole.CASHIER
}

@Composable
private fun UsersTab(
    users: List<User>,
    onAddUser: () -> Unit,
    onEditUser: (User) -> Unit,
    onToggleActive: (User, Boolean) -> Unit
) {
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
                Button(onClick = onAddUser) {
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
                    onEdit = { onEditUser(user) },
                    onToggleActive = { enabled -> onToggleActive(user, enabled) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountRequestsTab(
    state: com.example.selliaapp.viewmodel.admin.AccountRequestsUiState,
    onRefresh: () -> Unit,
    onUpdate: (String, AccountRequestStatus, Map<String, Boolean>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Solicitudes de cuentas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onRefresh, enabled = !state.isLoading) {
                Text("Actualizar")
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.requests.isEmpty() && !state.isLoading) {
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
                        text = "No hay solicitudes pendientes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Las nuevas solicitudes aparecerán en esta sección.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.requests, key = { it.id }) { request ->
                    AccountRequestItem(
                        request = request,
                        isLoading = state.isLoading,
                        onUpdate = onUpdate
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountRequestItem(
    request: AccountRequest,
    isLoading: Boolean,
    onUpdate: (String, AccountRequestStatus, Map<String, Boolean>) -> Unit
) {
    var statusExpanded by remember { mutableStateOf(false) }
    var selectedStatus by remember(request.id) { mutableStateOf(request.status) }
    var enabledModules by remember(request.id) { mutableStateOf(request.enabledModules) }
    val moduleOptions = remember { BusinessModule.entries }
    val isPendingSelection = selectedStatus == AccountRequestStatus.PENDING
    val hasStatusChanged = selectedStatus != request.status
    val hasModulesChanged = enabledModules != request.enabledModules
    val hasChanges = hasStatusChanged || hasModulesChanged
    val canPersistChanges = !isLoading && hasChanges && !isPendingSelection
    val loginWillBeEnabled = selectedStatus == AccountRequestStatus.ACTIVE

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.storeName ?: request.contactName ?: request.email,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Tipo: ${request.accountType.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!request.tenantName.isNullOrBlank()) {
                Text(
                    text = "Tienda seleccionada: ${request.tenantName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!request.storeAddress.isNullOrBlank()) {
                Text(
                    text = "Dirección: ${request.storeAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!request.storePhone.isNullOrBlank()) {
                Text(
                    text = "Teléfono: ${request.storePhone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = !statusExpanded }
            ) {
                OutlinedTextField(
                    value = selectedStatus.label,
                    onValueChange = {},
                    label = { Text("Estado") },
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = !isLoading
                )
                ExposedDropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false }
                ) {
                    AccountRequestStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.label) },
                            onClick = {
                                selectedStatus = status
                                statusExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = if (loginWillBeEnabled) {
                    "Acceso al login: habilitado al guardar cambios"
                } else {
                    "Acceso al login: bloqueado al guardar cambios"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (loginWillBeEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Medium
            )

            if (isPendingSelection) {
                Text(
                    text = "Para evitar estados ambiguos, elegí Habilitado, Rechazado o Deshabilitado antes de guardar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (request.accountType == AccountRequestType.STORE_OWNER) {
                Text(
                    text = "Módulos habilitados",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                moduleOptions.forEach { module ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = module.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = enabledModules[module.key] == true,
                            onCheckedChange = { checked ->
                                enabledModules = enabledModules.toMutableMap()
                                    .apply { put(module.key, checked) }
                            },
                            enabled = !isLoading
                        )
                    }
                }
            }

            Button(
                onClick = { onUpdate(request.id, selectedStatus, enabledModules) },
                enabled = canPersistChanges
            ) {
                Text("Guardar cambios")
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
    val roleOptions = remember(user) {
        (listOf(AppRole.MANAGER, AppRole.CASHIER) + AppRole.fromRaw(user?.role)).distinct()
    }
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
