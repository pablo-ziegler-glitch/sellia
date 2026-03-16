package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.BuildConfig
import com.example.selliaapp.data.remote.StoreType
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.StorefrontViewModel
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val latamCountries = listOf(
    "AR" to "Argentina",
    "MX" to "México",
    "CL" to "Chile",
    "CO" to "Colombia",
    "UY" to "Uruguay",
    "PE" to "Perú",
    "VE" to "Venezuela",
    "BO" to "Bolivia",
    "PY" to "Paraguay",
    "EC" to "Ecuador"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsScreen(
    onBack: () -> Unit,
    vm: StorefrontViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val config = state.config
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var suggestedOnce by remember { mutableStateOf(false) }
    var addressQuery by remember(config.locationAddress) { mutableStateOf(config.locationAddress.orEmpty()) }
    var placeSuggestions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var loadingSuggestions by remember { mutableStateOf(false) }
    var expandedCountry by remember { mutableStateOf(false) }

    val placesClient = remember {
        if (!Places.isInitialized() && BuildConfig.MAPS_API_KEY.isNotBlank()) {
            Places.initialize(context.applicationContext, BuildConfig.MAPS_API_KEY)
        }
        if (Places.isInitialized()) Places.createClient(context) else null
    }

    LaunchedEffect(config.locationLat, config.contactWhatsapp, config.contactInstagram) {
        if (suggestedOnce) return@LaunchedEffect
        val hasPhysical = config.locationLat != null
        val hasOnlineSignals = config.contactWhatsapp.isNotBlank() || config.contactInstagram.isNotBlank()
        val suggestedType = when {
            hasPhysical && hasOnlineSignals -> StoreType.BOTH
            hasPhysical -> StoreType.PHYSICAL
            hasOnlineSignals -> StoreType.ONLINE
            else -> StoreType.PHYSICAL
        }
        vm.updateStoreType(suggestedType)
        suggestedOnce = true
    }

    LaunchedEffect(addressQuery, config.storeType, placesClient) {
        if (config.storeType !in setOf(StoreType.PHYSICAL, StoreType.BOTH) || placesClient == null) {
            placeSuggestions = emptyList()
            return@LaunchedEffect
        }
        if (addressQuery.length < 2) {
            placeSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        loadingSuggestions = true
        runCatching { placesClient.findPredictions(addressQuery).take(5) }
            .onSuccess { placeSuggestions = it }
            .onFailure { placeSuggestions = emptyList() }
        loadingSuggestions = false
    }

    LaunchedEffect(state.successMessage, state.errorMessage) {
        state.successMessage?.let { snackbarHostState.showSnackbar(it) }
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
        if (state.successMessage != null || state.errorMessage != null) vm.clearMessages()
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Ubicación y tipo de tienda", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("¿Cómo operás tu tienda?", style = MaterialTheme.typography.titleMedium)
            StoreType.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.updateStoreType(option) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = config.storeType == option, onClick = { vm.updateStoreType(option) })
                    Text(
                        when (option) {
                            StoreType.PHYSICAL -> "Física"
                            StoreType.ONLINE -> "Online"
                            StoreType.BOTH -> "Física + Online"
                        }
                    )
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hacemos delivery / envíos a domicilio")
                Switch(checked = config.hasDelivery, onCheckedChange = vm::updateDelivery)
            }

            if (config.storeType == StoreType.PHYSICAL || config.storeType == StoreType.BOTH) {
                HorizontalDivider()
                Text("Dirección del local físico", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tus clientes podrán encontrarte en el mapa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = { addressQuery = it },
                    label = { Text("Buscar dirección...") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (loadingSuggestions) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                }
                placeSuggestions.forEach { prediction ->
                    Text(
                        text = prediction.getFullText(null).toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (placesClient == null) return@clickable
                                runCatching { placesClient.fetchAddress(prediction.placeId) }
                                    .onSuccess { place ->
                                        val selectedAddress = place.address.orEmpty()
                                        vm.updateConfig(
                                            config.copy(
                                                locationLat = place.latLng?.latitude,
                                                locationLng = place.latLng?.longitude,
                                                locationAddress = selectedAddress
                                            )
                                        )
                                        addressQuery = selectedAddress
                                        placeSuggestions = emptyList()
                                    }
                            }
                            .padding(vertical = 8.dp)
                    )
                }

                if (!config.locationAddress.isNullOrBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text("📍 ${config.locationAddress}") },
                        trailingIcon = {
                            IconButton(onClick = {
                                addressQuery = ""
                                placeSuggestions = emptyList()
                                vm.clearPhysicalLocation()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar dirección")
                            }
                        }
                    )
                }
            }

            if (config.storeType == StoreType.ONLINE || config.storeType == StoreType.BOTH) {
                HorizontalDivider()
                Text("¿Desde dónde operás?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ayuda a los clientes a saber dónde estás radicado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = config.locationCountry.orEmpty(),
                    onValueChange = { vm.updateConfig(config.copy(locationCountry = it.uppercase())) },
                    label = { Text("País") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { expandedCountry = true }) { Text("Elegir país LATAM") }
                DropdownMenu(expanded = expandedCountry, onDismissRequest = { expandedCountry = false }) {
                    latamCountries.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                vm.updateConfig(config.copy(locationCountry = code))
                                expandedCountry = false
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = config.locationCity.orEmpty(),
                    onValueChange = { vm.updateConfig(config.copy(locationCity = it)) },
                    label = { Text("Ciudad") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = vm::saveConfig,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "Guardando..." else "Guardar configuración")
            }
        }
    }
}

private suspend fun PlacesClient.findPredictions(query: String): List<AutocompletePrediction> =
    suspendCancellableCoroutine { cont ->
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()
        findAutocompletePredictions(request)
            .addOnSuccessListener { cont.resume(it.autocompletePredictions) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

private suspend fun PlacesClient.fetchAddress(placeId: String): Place = suspendCancellableCoroutine { cont ->
    val request = FetchPlaceRequest.newInstance(
        placeId,
        listOf(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG)
    )
    fetchPlace(request)
        .addOnSuccessListener { cont.resume(it.place) }
        .addOnFailureListener { cont.resumeWithException(it) }
}
