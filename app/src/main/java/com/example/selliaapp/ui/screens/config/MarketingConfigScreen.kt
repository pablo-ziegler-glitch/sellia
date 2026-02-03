package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.MarketingSettings
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.MarketingConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketingConfigScreen(
    vm: MarketingConfigViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState(initial = MarketingSettings())
    var publicStoreUrl by remember { mutableStateOf(settings.publicStoreUrl) }
    var storeName by remember { mutableStateOf(settings.storeName) }
    var storeLogoUrl by remember { mutableStateOf(settings.storeLogoUrl) }
    var storePhone by remember { mutableStateOf(settings.storePhone) }
    var storeWhatsapp by remember { mutableStateOf(settings.storeWhatsapp) }
    var storeEmail by remember { mutableStateOf(settings.storeEmail) }

    LaunchedEffect(settings) {
        publicStoreUrl = settings.publicStoreUrl
        storeName = settings.storeName
        storeLogoUrl = settings.storeLogoUrl
        storePhone = settings.storePhone
        storeWhatsapp = settings.storeWhatsapp
        storeEmail = settings.storeEmail
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Campañas de marketing", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sitio público")
            OutlinedTextField(
                value = publicStoreUrl,
                onValueChange = { publicStoreUrl = it },
                label = { Text("URL pública (para QR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Contacto público de la tienda")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Nombre de la tienda") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeLogoUrl,
                    onValueChange = { storeLogoUrl = it },
                    label = { Text("Logo (URL pública)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storePhone,
                    onValueChange = { storePhone = it },
                    label = { Text("Teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeWhatsapp,
                    onValueChange = { storeWhatsapp = it },
                    label = { Text("WhatsApp") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeEmail,
                    onValueChange = { storeEmail = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    vm.updateSettings(
                        MarketingSettings(
                            publicStoreUrl = publicStoreUrl.trim(),
                            storeName = storeName.ifBlank { settings.storeName },
                            storeLogoUrl = storeLogoUrl.trim(),
                            storePhone = storePhone.trim(),
                            storeWhatsapp = storeWhatsapp.trim(),
                            storeEmail = storeEmail.trim()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar configuración")
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
                Text("Volver")
            }
        }
    }
}
