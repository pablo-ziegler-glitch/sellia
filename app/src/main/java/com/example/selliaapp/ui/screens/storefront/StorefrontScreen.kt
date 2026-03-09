package com.example.selliaapp.ui.screens.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.StorefrontViewModel

@Composable
fun StorefrontScreen(
    onBack: () -> Unit,
    vm: StorefrontViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val config = state.config

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopAppBar(title = "Vidriera Pública", onBack = onBack)

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Identity Section ---
            SectionTitle("Identidad de la tienda")

            OutlinedTextField(
                value = config.storeName,
                onValueChange = { vm.updateConfig(config.copy(storeName = it)) },
                label = { Text("Nombre de la tienda") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.tagline,
                onValueChange = { vm.updateConfig(config.copy(tagline = it)) },
                label = { Text("Tagline / slogan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.logoUrl,
                onValueChange = { vm.updateConfig(config.copy(logoUrl = it)) },
                label = { Text("URL del logo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // --- Banner Section ---
            SectionTitle("Banner de tienda")
            Text(
                text = "Se muestra en la vista de tu tienda para clientes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = config.bannerImageUrl,
                onValueChange = { vm.updateConfig(config.copy(bannerImageUrl = it)) },
                label = { Text("URL imagen de fondo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.bannerTitle,
                onValueChange = { vm.updateConfig(config.copy(bannerTitle = it)) },
                label = { Text("Título del banner") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.bannerSubtitle,
                onValueChange = { vm.updateConfig(config.copy(bannerSubtitle = it)) },
                label = { Text("Subtítulo del banner") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.bannerCtaText,
                onValueChange = { vm.updateConfig(config.copy(bannerCtaText = it)) },
                label = { Text("Texto del botón CTA") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // --- Contact Section ---
            SectionTitle("Contacto")

            OutlinedTextField(
                value = config.contactWhatsapp,
                onValueChange = { vm.updateConfig(config.copy(contactWhatsapp = it)) },
                label = { Text("WhatsApp") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.contactInstagram,
                onValueChange = { vm.updateConfig(config.copy(contactInstagram = it)) },
                label = { Text("Instagram") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.contactAddress,
                onValueChange = { vm.updateConfig(config.copy(contactAddress = it)) },
                label = { Text("Dirección") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // --- Banner Messages Section ---
            SectionTitle("Mensajes para banner global")
            Text(
                text = "Máx. 3 mensajes activos. Aparecen en la vista de todos los clientes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            config.bannerMessages.forEach { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.active)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = msg.active,
                            onCheckedChange = { vm.toggleBannerMessage(msg.id) }
                        )
                        IconButton(onClick = { vm.removeBannerMessage(msg.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Add message
            var newMessage by rememberSaveable { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newMessage,
                    onValueChange = { if (it.length <= 120) newMessage = it },
                    label = { Text("Nuevo mensaje") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        if (newMessage.isNotBlank()) {
                            vm.addBannerMessage(newMessage.trim())
                            newMessage = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }

            // Status messages
            state.errorMessage?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.successMessage?.let { success ->
                Text(text = success, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = vm::saveConfig,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar cambios")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}
