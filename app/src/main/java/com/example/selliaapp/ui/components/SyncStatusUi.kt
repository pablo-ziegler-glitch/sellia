package com.example.selliaapp.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.selliaapp.data.dao.SyncOutboxDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private enum class SyncUiStatus {
    SYNCED,
    SYNCING,
    PENDING,
    OFFLINE
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface SyncStatusUiEntryPoint {
    fun syncOutboxDao(): SyncOutboxDao
}

@Composable
private fun rememberSyncOutboxDao(context: Context): SyncOutboxDao {
    val appContext = context.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            SyncStatusUiEntryPoint::class.java
        ).syncOutboxDao()
    }
}

@Composable
private fun rememberIsOnline(context: Context): Boolean {
    val appContext = context.applicationContext
    return produceState(initialValue = true, key1 = appContext) {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.value
}

@Composable
private fun rememberSyncUiStatus(): Pair<SyncUiStatus, Int> {
    val context = LocalContext.current
    val dao = rememberSyncOutboxDao(context)
    val pendingCount by dao.observePendingCount().collectAsStateWithLifecycle(initialValue = 0)

    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workInfos by workManager
        .getWorkInfosForUniqueWorkLiveData(com.example.selliaapp.sync.SyncWorker.UNIQUE_NAME)
        .observeAsState(initial = emptyList())

    val syncing = workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    val isOnline = rememberIsOnline(context)

    val status = when {
        !isOnline -> SyncUiStatus.OFFLINE
        syncing -> SyncUiStatus.SYNCING
        pendingCount > 0 -> SyncUiStatus.PENDING
        else -> SyncUiStatus.SYNCED
    }
    return status to pendingCount
}

@Composable
fun SyncStatusTopBarIcon() {
    val (status, pendingCount) = rememberSyncUiStatus()
    val (icon, label, color) = when (status) {
        SyncUiStatus.SYNCED -> Triple(Icons.Filled.CloudDone, "Sincronizado", MaterialTheme.colorScheme.primary)
        SyncUiStatus.SYNCING -> Triple(Icons.Filled.CloudSync, "Sincronizando", MaterialTheme.colorScheme.secondary)
        SyncUiStatus.PENDING -> Triple(
            Icons.Filled.CloudUpload,
            if (pendingCount == 1) "1 cambio pendiente" else "$pendingCount cambios pendientes",
            MaterialTheme.colorScheme.tertiary
        )
        SyncUiStatus.OFFLINE -> Triple(Icons.Filled.CloudOff, "Sin conexión", MaterialTheme.colorScheme.error)
    }

    SyncStatusIcon(icon = icon, label = label, tint = color)
}

@Composable
private fun SyncStatusIcon(icon: ImageVector, label: String, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.semantics { contentDescription = label }
    )
}

@Composable
fun SyncPendingBanner(modifier: Modifier = Modifier) {
    val (status, pendingCount) = rememberSyncUiStatus()
    if (pendingCount <= 0 || status == SyncUiStatus.SYNCED) return

    val text = when (status) {
        SyncUiStatus.OFFLINE -> "Sin conexión: $pendingCount cambios se enviarán al reconectar."
        SyncUiStatus.SYNCING -> "Sincronizando $pendingCount cambios pendientes..."
        SyncUiStatus.PENDING -> "Tenés $pendingCount cambios pendientes de sincronizar."
        SyncUiStatus.SYNCED -> ""
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncStatusTopBarIcon()
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
