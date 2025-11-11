package com.example.selliaapp.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.csv.ProductCsvImporter
import com.example.selliaapp.data.csv.TabularFileReader
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.di.IoDispatcher
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.ProductRepository // Solo para el tipo ImportStrategy en la firma pública
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [NUEVO] VM para el wizard de importación migrado a IProductRepository.
 * Mantiene firmas públicas y comportamiento. Toda la lógica real sigue en tu ProductRepository
 * a través del adaptador (IProductRepositoryAdapter).
 */
@HiltViewModel
class StockImportViewModel @Inject constructor(
    private val repo: IProductRepository,
    @IoDispatcher private val io: CoroutineDispatcher,   // qualifier de IO
    @ApplicationContext private val appContext: Context  // contexto app
) : ViewModel() {

    data class DryRunResult(
        val inserted: Int,
        val updated: Int,
        val errors: List<String>
    )

    private val _ui = MutableStateFlow("idle")
    val ui: StateFlow<String> = _ui

    private val _preview = MutableStateFlow<List<List<String>>>(emptyList())
    val preview: StateFlow<List<List<String>>> = _preview

    private val _dryRun = MutableStateFlow<DryRunResult?>(null)
    val dryRun: StateFlow<DryRunResult?> = _dryRun

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private var currentUri: Uri? = null
    private var cachedRows: List<ProductCsvImporter.Row> = emptyList() // reservado si luego querés flujo avanzado

    /** Carga preview (primeras 20 filas) del archivo tabular seleccionado. */
    fun loadPreview(uri: Uri) {
        currentUri = uri
        viewModelScope.launch {
            runCatching {
                TabularFileReader.readAll(appContext.contentResolver, uri).take(20)
            }.onSuccess { table ->
                _preview.value = table
            }.onFailure {
                _preview.value = emptyList()
            }
        }
    }

    /** Simulación de importación sin escribir en DB. */
    fun runDryRun() {
        viewModelScope.launch {
            val uri = currentUri ?: return@launch
            val (inserted, updated, errors) = repo.simulateImport(appContext, uri)
            _dryRun.value = DryRunResult(inserted, updated, errors)
        }
    }

    /** Encola importación real en background (WorkManager). */
    fun doImport() {
        viewModelScope.launch {
            _importing.value = true
            val uri = currentUri ?: return@launch
            repo.importProductsInBackground(appContext, uri)
            _importing.value = false
        }
    }

    /**
     * Importa desde un archivo tabular (CSV/Excel/Sheets) referenciado por [uri] con la estrategia indicada.
     * Mantiene la firma pública con ProductRepository.ImportStrategy para no romper la UI.
     */
    fun importFromFile(
        context: Context,
        uri: Uri,
        strategy: ProductRepository.ImportStrategy = ProductRepository.ImportStrategy.Append,
        onCompleted: (ImportResult) -> Unit
    ) {
        val resolver: ContentResolver = context.contentResolver
        viewModelScope.launch(io) {
            val result = repo.importFromFile(resolver, uri, strategy)
            withContext(Dispatchers.Main) {
                onCompleted(result)
            }
        }
    }
}