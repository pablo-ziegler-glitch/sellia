package com.example.selliaapp.ui.screens.config

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.csv.CustomerCsvImporter
import com.example.selliaapp.data.csv.ProductImportTemplate
import com.example.selliaapp.data.csv.UserCsvImporter
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.ui.util.exportContentToDownloads
import com.example.selliaapp.ui.util.exportTemplateToDownloads
import com.example.selliaapp.ui.util.shareExportedFile
import com.example.selliaapp.viewmodel.BulkDataViewModel
import com.example.selliaapp.viewmodel.StockImportViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkDataScreen(
    onBack: () -> Unit,
    onManageProducts: () -> Unit,
    onManageCustomers: () -> Unit,
    onManageUsers: () -> Unit,
    canManageUsers: Boolean
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val stockImportViewModel: StockImportViewModel = hiltViewModel()
    val bulkViewModel: BulkDataViewModel = hiltViewModel()

    fun showMessage(text: String) {
        scope.launch {
            snackbarHostState.showSnackbar(text)
        }
    }

    fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    }

    fun handleExport(
        result: Result<BulkDataViewModel.ExportPayload>,
        successLabel: String
    ) {
        result.fold(
            onSuccess = { payload ->
                val uri = exportContentToDownloads(
                    context = context,
                    fileName = payload.fileName,
                    mimeType = payload.mimeType,
                    content = payload.content
                )
                if (uri != null) {
                    shareExportedFile(
                        context = context,
                        uri = uri,
                        mimeType = payload.mimeType,
                        title = "Compartir $successLabel"
                    )
                    showMessage("$successLabel exportados en Descargas.")
                } else {
                    showMessage("No se pudo exportar $successLabel.")
                }
            },
            onFailure = {
                showMessage("No se pudo exportar $successLabel.")
            }
        )
    }

    val productPicker = rememberImportLauncher(context) { uri ->
        val fileName = queryDisplayName(uri)
        stockImportViewModel.importFromFile(
            context = context,
            uri = uri,
            strategy = ProductRepository.ImportStrategy.Append
        ) { result ->
            showMessage(result.toUserMessage(fileName))
        }
    }

    val customerPicker = rememberImportLauncher(context) { uri ->
        val fileName = queryDisplayName(uri)
        bulkViewModel.importCustomers(context, uri) { result ->
            showMessage(result.toUserMessage(fileName))
        }
    }

    val userPicker = rememberImportLauncher(context) { uri ->
        val fileName = queryDisplayName(uri)
        bulkViewModel.importUsers(context, uri) { result ->
            showMessage(result.toUserMessage(fileName))
        }
    }

    val totalPicker = rememberImportLauncher(context) { uri ->
        bulkViewModel.importAll(context, uri) { result ->
            result.fold(
                onSuccess = { summary ->
                    showMessage(summary.message)
                    if (summary.errors.isNotEmpty()) {
                        showMessage("Importación con errores: ${summary.errors.first()}")
                    }
                },
                onFailure = {
                    showMessage("No se pudo importar la exportación total.")
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargas masivas") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BulkSectionCard(
                title = "Productos",
                description = "Descargá una plantilla Excel/CSV y cargá productos en forma masiva.",
                onManage = onManageProducts,
                onDownloadTemplate = {
                    val uri = exportTemplateToDownloads(
                        context = context,
                        fileName = ProductImportTemplate.templateFileName(),
                        mimeType = ProductImportTemplate.templateMimeType(),
                        content = ProductImportTemplate.templateCsv()
                    )
                    showMessage(
                        if (uri != null) "Plantilla guardada en Descargas."
                        else "No se pudo guardar la plantilla."
                    )
                },
                onExport = {
                    bulkViewModel.exportProducts { result ->
                        handleExport(result, "productos")
                    }
                },
                onImport = {
                    productPicker.launch(
                        arrayOf(
                            "text/*",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.google-apps.spreadsheet"
                        )
                    )
                }
            )

            BulkSectionCard(
                title = "Clientes",
                description = "Importá clientes desde un Excel/CSV para acelerar el alta.",
                onManage = onManageCustomers,
                onDownloadTemplate = {
                    val uri = exportTemplateToDownloads(
                        context = context,
                        fileName = CustomerCsvImporter.templateFileName(),
                        mimeType = CustomerCsvImporter.templateMimeType(),
                        content = CustomerCsvImporter.templateCsv()
                    )
                    showMessage(
                        if (uri != null) "Plantilla guardada en Descargas."
                        else "No se pudo guardar la plantilla."
                    )
                },
                onExport = {
                    bulkViewModel.exportCustomers { result ->
                        handleExport(result, "clientes")
                    }
                },
                onImport = {
                    customerPicker.launch(
                        arrayOf(
                            "text/*",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.google-apps.spreadsheet"
                        )
                    )
                }
            )

            BulkSectionCard(
                title = "Usuarios",
                description = "Cargá usuarios masivamente con roles y emails.",
                onManage = onManageUsers,
                onDownloadTemplate = {
                    val uri = exportTemplateToDownloads(
                        context = context,
                        fileName = UserCsvImporter.templateFileName(),
                        mimeType = UserCsvImporter.templateMimeType(),
                        content = UserCsvImporter.templateCsv()
                    )
                    showMessage(
                        if (uri != null) "Plantilla guardada en Descargas."
                        else "No se pudo guardar la plantilla."
                    )
                },
                onImport = {
                    userPicker.launch(
                        arrayOf(
                            "text/*",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.google-apps.spreadsheet"
                        )
                    )
                },
                enabled = canManageUsers,
                disabledMessage = "Tu rol no tiene permiso para gestionar usuarios."
            )

            BulkSectionCard(
                title = "Ventas",
                description = "Exportá tus ventas con detalle de productos.",
                onExport = {
                    bulkViewModel.exportSales { result ->
                        handleExport(result, "ventas")
                    }
                }
            )

            BulkSectionCard(
                title = "Gastos",
                description = "Exportá los gastos registrados para análisis externo.",
                onExport = {
                    bulkViewModel.exportExpenses { result ->
                        handleExport(result, "gastos")
                    }
                }
            )

            BulkSectionCard(
                title = "Exportación total",
                description = "Generá un CSV único con productos, clientes, ventas y gastos.",
                onExport = {
                    bulkViewModel.exportAll { result ->
                        handleExport(result, "exportación total")
                    }
                },
                onImport = {
                    totalPicker.launch(
                        arrayOf(
                            "text/*",
                            "text/csv"
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun BulkSectionCard(
    title: String,
    description: String,
    onManage: (() -> Unit)? = null,
    onDownloadTemplate: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    enabled: Boolean = true,
    disabledMessage: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onDownloadTemplate?.let { action ->
                    TextButton(onClick = action, enabled = enabled) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Plantilla")
                    }
                }
                onImport?.let { action ->
                    TextButton(onClick = action, enabled = enabled) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Importar")
                    }
                }
                onExport?.let { action ->
                    TextButton(onClick = action, enabled = enabled) {
                        Text("Exportar")
                    }
                }
                if (onManage != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onManage, enabled = enabled) {
                        Text("Gestionar")
                    }
                }
            }
            if (!enabled) {
                Text(
                    disabledMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun rememberImportLauncher(
    context: android.content.Context,
    onPicked: (Uri) -> Unit
) = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) { }
    onPicked(uri)
}
