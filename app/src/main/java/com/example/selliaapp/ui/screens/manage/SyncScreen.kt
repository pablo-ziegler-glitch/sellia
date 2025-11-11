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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.selliaapp.sync.SyncScheduler
import com.example.selliaapp.sync.SyncWorker
import com.example.selliaapp.ui.components.BackTopAppBar

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
    val workManager = remember(context) { WorkManager.getInstance(context) }

     // Observamos el estado del trabajo único por nombre
    val workInfos by workManager
        .getWorkInfosForUniqueWorkLiveData(SyncWorker.UNIQUE_NAME)
        .observeAsState(initial = emptyList())


    // syncing = hay un trabajo encolado o corriendo
    val syncing = workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    Scaffold(topBar = { BackTopAppBar(title = "Sincronización", onBack = onBack) }) { padding ->
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

            // Botón principal: Encolar sync
            Button(
                enabled = !syncing,               // deshabilitamos mientras hay un trabajo en curso
                onClick = { SyncScheduler.enqueueNow(context) }
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
                val last = workInfos.firstOrNull()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado: ${last?.state?.name ?: "N/A"}")
                }
                last?.outputData?.let { data ->
                    // Si tu Worker setea datos de salida, podés mostrarlos acá.
                    // Text("Resultado: ${data.getString("key") ?: "-"}")
                }
            }
        }
    }



