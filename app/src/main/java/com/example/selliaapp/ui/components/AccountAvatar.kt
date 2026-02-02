package com.example.selliaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.selliaapp.auth.AuthState
import com.example.selliaapp.domain.security.UserAccessState

data class AccountSummary(
    val displayName: String,
    val email: String?,
    val roleLabel: String,
    val avatarUrl: String?
)

fun buildAccountSummary(authState: AuthState, accessState: UserAccessState): AccountSummary {
    val session = (authState as? AuthState.Authenticated)?.session
    val email = session?.email ?: accessState.email
    val displayName = session?.displayName?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
        ?: "Usuario"
    val roleLabel = accessState.role.label
    return AccountSummary(
        displayName = displayName,
        email = email,
        roleLabel = roleLabel,
        avatarUrl = session?.photoUrl
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAvatarMenu(
    accountSummary: AccountSummary,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }, modifier = modifier) {
        AccountAvatar(
            avatarUrl = accountSummary.avatarUrl,
            displayName = accountSummary.displayName
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AccountAvatar(
                        avatarUrl = accountSummary.avatarUrl,
                        displayName = accountSummary.displayName,
                        size = 64.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = accountSummary.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        accountSummary.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(accountSummary.roleLabel) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Datos de la cuenta",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Rol asignado: ${accountSummary.roleLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AccountAvatar(
    avatarUrl: String?,
    displayName: String,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val initial = displayName.trim().take(1).uppercase()
    val background = MaterialTheme.colorScheme.primaryContainer
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(width = 1.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar de ${displayName}",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
