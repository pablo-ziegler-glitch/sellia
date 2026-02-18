package com.example.selliaapp.ui.screens.stock

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.PhotoCandidateRow
import com.example.selliaapp.viewmodel.PhotoStockIntakeViewModel
import kotlin.math.roundToInt

@Composable
fun PhotoStockIntakeScreen(
    onBack: () -> Unit,
    viewModel: PhotoStockIntakeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackState = remember { SnackbarHostState() }

    LaunchedEffect(state.infoMessage, state.errorMessage) {
        state.errorMessage?.let { snackState.showSnackbar(it); viewModel.clearMessage() }
        state.infoMessage?.let { snackState.showSnackbar(it); viewModel.clearMessage() }
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.analyzeImagePaths(uris.map { it.toString() })
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Carga por foto (IA)",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Subí fotos de productos, validá sugerencias de nombre/marca y guardá el stock.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(onClick = { pickImagesLauncher.launch("image/*") }) {
                Text("Seleccionar fotos")
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.rows.isEmpty() && !state.loading) {
                Text("Aún no hay detecciones. Elegí fotos para empezar.")
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(state.rows, key = { it.id }) { row ->
                    CandidateRowCard(
                        row = row,
                        onChange = viewModel::updateRow
                    )
                }
            }

            Button(
                onClick = viewModel::commitSelectedToStock,
                enabled = state.rows.isNotEmpty() && !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Agregar seleccionados al stock")
                }
            }
        }
    }
}

@Composable
private fun CandidateRowCard(
    row: PhotoCandidateRow,
    onChange: (PhotoCandidateRow) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.selected, onCheckedChange = { onChange(row.copy(selected = it)) })
                Column {
                    Text("Confianza IA: ${(row.confidence * 100f).roundToInt()}%")
                    Text(
                        text = row.imagePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            OutlinedTextField(
                value = row.name,
                onValueChange = { onChange(row.copy(name = it)) },
                label = { Text("Producto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = row.brand,
                onValueChange = { onChange(row.copy(brand = it)) },
                label = { Text("Marca") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.category,
                    onValueChange = { onChange(row.copy(category = it)) },
                    label = { Text("Categoría") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { onChange(row.copy(quantity = it.filter(Char::isDigit))) },
                    label = { Text("Cant.") },
                    modifier = Modifier.weight(0.4f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
