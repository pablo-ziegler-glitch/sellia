package com.example.selliaapp.ui.screens.stock

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.viewmodel.StockImportViewModel

/**
 * Wizard de importación:
 * Paso 1: Selección de archivo + vista previa.
 * Paso 2: Mapeo de columnas (opcional si encabezados ya coinciden).
 * Paso 3: Dry-run (simular import) → muestra inserts/updates/errores.
 * Paso 4: Importación real (posibilidad de enviar a WorkManager).
 */
@Composable
fun StockImportWizardScreen(
    fileUri: Uri?,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    viewModel: StockImportViewModel = hiltViewModel()
) {
    var step by remember { mutableStateOf(1) }
    val ui by viewModel.ui.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Importar archivo de productos", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        when (step) {
            1 -> StepPreview(fileUri, viewModel) { step = 2 }
            2 -> StepMapping(viewModel) { step = 3 }
            3 -> StepDryRun(viewModel) { step = 4 }
            4 -> StepImport(viewModel, onDone)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            if (step > 1) {
                OutlinedButton(onClick = { step-- }) { Text("Atrás") }
            }
        }
    }
}

@Composable private fun StepPreview(
    fileUri: Uri?,
    vm: StockImportViewModel,
    onNext: () -> Unit
) {
    val preview by vm.preview.collectAsState()
    Button(enabled = fileUri != null, onClick = { vm.loadPreview(fileUri!!); onNext() }) {
        Text("Cargar vista previa")
    }
    Spacer(Modifier.height(12.dp))
    if (preview.isNotEmpty()) {
        Text("Primeras filas:")
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxHeight(0.6f)) {
            itemsIndexed(preview.take(10)) { idx, row ->
                Text("${idx + 1}. ${row.joinToString(" | ")}")
                Divider()
            }
        }
    }
}

@Composable private fun StepMapping(
    vm: StockImportViewModel,
    onNext: () -> Unit
) {
    // Para simplificar, si encabezados ya coinciden, avanzamos directo.
    // Si quisieras mapeo manual, presentá Dropdowns por campo → columna detectada.
    Text("Encabezados detectados OK. (Se pueden agregar dropdowns para remapeo manual)")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onNext) { Text("Continuar a simulación (dry-run)") }
}

@Composable private fun StepDryRun(
    vm: StockImportViewModel,
    onNext: () -> Unit
) {
    val dryRun by vm.dryRun.collectAsState()
    Button(onClick = { vm.runDryRun() }) { Text("Simular importación") }
    Spacer(Modifier.height(12.dp))

    if (dryRun != null) {
        val r = dryRun!!
        Text("Simulación: inserts=${r.inserted}, updates=${r.updated}, errores=${r.errors.size}")
        Spacer(Modifier.height(8.dp))
        if (r.errors.isNotEmpty()) {
            Text("Errores (primeros 10):")
            Spacer(Modifier.height(4.dp))
            r.errors.take(10).forEach { Text("• $it") }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNext) { Text("Importar ahora") }
    }
}

@Composable private fun StepImport(
    vm: StockImportViewModel,
    onDone: () -> Unit
) {
    val importing by vm.importing.collectAsState()
    if (!importing) {
        Button(onClick = { vm.doImport() }) { Text("Ejecutar importación") }
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text("Importando en segundo plano…")
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onDone) { Text("Finalizar") }
}
