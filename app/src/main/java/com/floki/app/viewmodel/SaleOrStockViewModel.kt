package com.floki.app.viewmodel



import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floki.app.repository.OffRepository
import com.floki.app.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaleOrStockViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    private val offRepo: OffRepository
) : ViewModel() {

    /**
     * Lógica al escanear: si no existe localmente -> consultamos OFF
     * y navegamos al Alta con datos pre-cargados.
     */
    fun onBarcodeScanned(
        barcode: String,
        openAddProduct: (prefill: PrefillData) -> Unit,
        showToast: (String) -> Unit
    ) {
        viewModelScope.launch {
            val local = productRepo.getByBarcodeOrNull(barcode)
            if (local != null) {
                // ... tu flujo normal (agregar a venta o mostrar en stock)
                showToast("Producto encontrado en base local.")
                return@launch
            }

            // No existe: consultamos OFF
            val off = try { offRepo.getProductSuggestion(barcode) } catch (e: Exception) {
                null
            }

            val prefill = if (off != null) {
                PrefillData(
                    barcode = barcode,
                    name = off.productName ?: "",
                    brand = off.brands?.split(",")?.firstOrNull()?.trim() ?: "",
                    imageUrl = off.imageUrl
                )
            } else {
                PrefillData(barcode = barcode) // solo el código
            }

            // Navegar a pantalla de alta manual con "Guardar" visible y datos precargados
            openAddProduct(prefill)
        }
    }
}

/** DTO liviano para pasar datos a la pantalla de Alta de Producto */
data class PrefillData(
    val barcode: String,
    val name: String = "",
    val brand: String = "",
    val imageUrl: String? = null
)
