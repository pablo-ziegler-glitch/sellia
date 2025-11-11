package com.example.selliaapp.ui.screens.stock


import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.viewmodel.StockImportViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockImportScreen(
    viewModel: StockImportViewModel,
    onBack: () -> Unit

) {
    val context = LocalContext.current
    var isImporting by remember { mutableStateOf(false) }
    var lastFileName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val openFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* opcional */ }

        lastFileName = context.contentResolver
            .query(uri, null, null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
            }

        isImporting = true
        message = null
        viewModel.importFromFile(context, uri, ProductRepository.ImportStrategy.Append) { result ->
            isImporting = false
            message = result.toUserMessage(lastFileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar productos desde archivo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cómo funciona", style = MaterialTheme.typography.titleMedium)
            Text("Seleccioná un archivo .csv o una planilla de Excel/Google Sheets. La app creará/actualizará productos.")
            Text("Formato recomendado:", style = MaterialTheme.typography.titleSmall)
            Text("name, barcode, price, quantity")
            Text(
                "Alias aceptados:\n" +
                        "• name: nombre, product, producto\n" +
                        "• barcode: codigo, código, ean, sku\n" +
                        "• price: precio, amount\n" +
                        "• quantity: qty, stock, cantidad",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        openFile.launch(
                            arrayOf(
                                "text/*",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.google-apps.spreadsheet"
                            )
                        )
                    },
                    enabled = !isImporting
                ) { Text(if (isImporting) "Importando..." else "Elegir archivo") }

                if (lastFileName != null) {
                    Text("Archivo: $lastFileName", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (message != null) {
                Surface(tonalElevation = 1.dp) {
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = message!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
    )
}

@Composable
private fun ImportResultView(result: ImportResult, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Importación finalizada:", fontWeight = FontWeight.Bold)
        Text("Insertados: ${result.inserted}")
        Text("Actualizados: ${result.updated}")
        if (result.hasErrors) {
            Text("Errores:", fontWeight = FontWeight.Bold)
            result.errors.take(50).forEachIndexed { i, msg ->
                Text("• [${i + 1}] $msg")
            }
            if (result.errors.size > 50) {
                Text("…${result.errors.size - 50} errores más")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDismiss) { Text("Aceptar") }
    }
}

@Composable
private fun ErrorView(message: String, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Falló la importación", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Text(message)
        OutlinedButton(onClick = onDismiss) { Text("Cerrar") }
    }
}
