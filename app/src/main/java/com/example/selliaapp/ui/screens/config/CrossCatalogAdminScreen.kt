package com.example.selliaapp.ui.screens.config

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.ui.util.exportTemplateToDownloads
import com.example.selliaapp.ui.util.shareExportedFile
import com.example.selliaapp.viewmodel.config.CrossCatalogAdminViewModel
import kotlinx.coroutines.launch

private const val CROSS_TEMPLATE_CONTENT = "barcode,name,brand\n"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossCatalogAdminScreen(
    onBack: () -> Unit,
    viewModel: CrossCatalogAdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val fileName = queryDisplayName(uri) ?: "archivo"
        viewModel.importFromFile(context, uri) { result ->
            val errorsCount = result.errors.size
            val okCount = result.inserted
            showMessage(
                if (errorsCount == 0) {
                    "Catálogo CROSS actualizado: $okCount códigos procesados."
                } else {
                    "CROSS: $okCount códigos procesados, $errorsCount errores en $fileName."
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo CROSS (Desarrollo)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Uso restringido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Esta pantalla es solo para cargas ocasionales del catálogo maestro CROSS (barcode → producto). No reemplaza la carga operativa diaria de stock.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(
                        enabled = !uiState.isImporting,
                        onClick = {
                            val uri = exportTemplateToDownloads(
                                context = context,
                                fileName = "plantilla_cross_catalog.csv",
                                mimeType = "text/csv",
                                content = CROSS_TEMPLATE_CONTENT
                            )
                            if (uri != null) {
                                shareExportedFile(
                                    context = context,
                                    uri = uri,
                                    mimeType = "text/csv",
                                    title = "Compartir plantilla catálogo CROSS"
                                )
                                showMessage("Plantilla CROSS guardada en Descargas.")
                            } else {
                                showMessage("No se pudo generar la plantilla CROSS.")
                            }
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Text("Descargar plantilla CROSS")
                    }
                    FilledTonalButton(
                        enabled = !uiState.isImporting,
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "text/*",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.google-apps.spreadsheet"
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text(if (uiState.isImporting) "Importando..." else "Importar catálogo CROSS")
                    }
                    uiState.lastResult?.let { result ->
                        Text(
                            text = "Última ejecución: ${result.inserted} códigos procesados · ${result.errors.size} errores",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
