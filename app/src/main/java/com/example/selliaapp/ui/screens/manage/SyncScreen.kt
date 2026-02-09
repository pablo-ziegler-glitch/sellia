package com.example.selliaapp.ui.screens.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.viewmodel.manage.SyncViewModel
import com.example.selliaapp.sync.SyncScheduler
import com.example.selliaapp.sync.SyncWorker
import com.example.selliaapp.ui.components.BackTopAppBar
import kotlinx.coroutines.launch

/**
 * Pantalla simple para ejecutar la sincronización manual.
 * Encola el SyncWorker y observa su estado (ENQUEUED/RUNNING/SUCCEEDED/FAILED).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SyncViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastState by remember { mutableStateOf<WorkInfo.State?>(null) }
    var includeBackup by remember { mutableStateOf(false) }

     // Observamos el estado del trabajo único por nombre
    val workInfos by workManager
        .getWorkInfosForUniqueWorkLiveData(SyncWorker.UNIQUE_NAME)
        .observeAsState(initial = emptyList())


    // syncing = hay un trabajo encolado o corriendo
    val syncing = workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    val last = workInfos.firstOrNull()
    val lastMessage = last?.outputData?.getString(SyncWorker.OUTPUT_MESSAGE)

    if (last?.state != null && lastState != last.state) {
        lastState = last.state
        scope.launch {
            val text = when (last.state) {
                WorkInfo.State.RUNNING -> "Sincronización en progreso..."
                WorkInfo.State.SUCCEEDED -> lastMessage ?: "Sincronización completada."
                WorkInfo.State.FAILED -> lastMessage ?: "Sincronización fallida."
                WorkInfo.State.CANCELLED -> "Sincronización cancelada."
                WorkInfo.State.ENQUEUED -> "Sincronización encolada."
                WorkInfo.State.BLOCKED -> "Sincronización bloqueada."
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Sincronización", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "Sincronizá ahora los datos (subida/descarga remota si corresponde).",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                "Opción 1: Sincronización estándar",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Sube cambios pendientes y baja productos/facturas remotas (requiere Datos en la nube activo).",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Respaldo operativo completo",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Guarda todas las tablas locales en Firestore para recuperación y auditoría (requiere Datos en la nube activo).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = includeBackup,
                    onCheckedChange = { includeBackup = it },
                    enabled = uiState.cloudEnabled && !syncing
                )
            }

            if (!uiState.cloudEnabled) {
                Text(
                    "Sincronización deshabilitada (requiere Datos en la nube activo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Botón principal: Encolar sync
            Button(
                enabled = !syncing && uiState.cloudEnabled,
                onClick = {
                    SyncScheduler.enqueueNow(context, includeBackup)
                    scope.launch { snackbarHostState.showSnackbar("Sincronización encolada.") }
                }
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (syncing) "Sincronizando..." else "Sincronizar ahora")
            }

            // Botón opcional: cancelar si está corriendo
            if (syncing) {
                OutlinedButton(
                    onClick = { workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME) }
                ) {
                    Text("Cancelar sync")
                }
            }

            // Estado visible (útil para debug)
            if (workInfos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado: ${last?.state?.name ?: "N/A"}")
                }
                if (!lastMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Detalle: $lastMessage", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
