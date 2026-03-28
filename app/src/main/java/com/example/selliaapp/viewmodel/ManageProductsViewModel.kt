package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.VariantDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.VariantEntity
import com.example.selliaapp.domain.product.ProductFilterParams
import com.example.selliaapp.domain.product.ProductSortOption
import com.example.selliaapp.domain.product.filterAndSortProducts
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceItem
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.repository.PricingConfigRepository
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.LocalDate

data class ManageProductsUiState(
    val query: String = "",
    val parentCategory: String = "",
    val category: String = "",
    val color: String = "",
    val size: String = "",
    val minPrice: String = "",
    val maxPrice: String = "",
    val sort: ProductSortOption = ProductSortOption.UPDATED_DESC,
    val onlyLowStock: Boolean = false,
    val onlyNoImage: Boolean = false,
    val onlyNoBarcode: Boolean = false
)

@HiltViewModel
class ManageProductsViewModel @Inject constructor(
    private val repo: IProductRepository,
    private val variantDao: VariantDao,
    private val pricingConfigRepository: PricingConfigRepository,
    private val providerRepository: ProviderRepository,
    private val providerInvoiceRepository: ProviderInvoiceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ManageProductsUiState())
    val state: StateFlow<ManageProductsUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pricingSettings = MutableStateFlow<PricingSettingsEntity?>(null)
    val pricingSettings = _pricingSettings.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pricingConfigRepository.getSettings() }
                .onSuccess { _pricingSettings.value = it }
        }
    }

    val productsAll: Flow<List<ProductEntity>> = repo.observeAll()

    val filteredProducts: Flow<List<ProductEntity>> = combine(productsAll, state) { products, uiState ->
        filterAndSortProducts(products, uiState.toFilterParams())
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun setParentCategory(value: String) = _state.update { it.copy(parentCategory = value) }
    fun setCategory(value: String) = _state.update { it.copy(category = value) }
    fun setColor(value: String) = _state.update { it.copy(color = value) }
    fun setSize(value: String) = _state.update { it.copy(size = value) }
    fun setMinPrice(value: String) = _state.update { it.copy(minPrice = value) }
    fun setMaxPrice(value: String) = _state.update { it.copy(maxPrice = value) }
    fun setSort(sort: ProductSortOption) = _state.update { it.copy(sort = sort) }

    fun toggleLowStock() = _state.update { it.copy(onlyLowStock = !it.onlyLowStock) }
    fun toggleNoImage() = _state.update { it.copy(onlyNoImage = !it.onlyNoImage) }
    fun toggleNoBarcode() = _state.update { it.copy(onlyNoBarcode = !it.onlyNoBarcode) }

    fun clearFilters() {
        _state.value = ManageProductsUiState()
    }

    fun deleteById(id: Int) {
        viewModelScope.launch {
            runCatching { repo.deleteById(id) }
                .onFailure { error ->
                    _message.value = error.message ?: "No se pudo eliminar el producto."
                }
        }
    }

    fun upsert(product: ProductEntity, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                // Si tiene nombre de proveedor, buscar o crear el proveedor y asignar el ID
                val resolved = if (!product.providerName.isNullOrBlank()) {
                    val pid = providerRepository.findOrCreateByName(product.providerName)
                    product.copy(providerId = pid)
                } else {
                    product.copy(providerId = null)
                }
                if (resolved.id == 0) {
                    repo.addProduct(resolved)
                } else {
                    repo.updateProduct(resolved)
                }
            }.onSuccess { id ->
                onDone(id)
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudo guardar el producto."
            }
        }
    }

    /** Crea un pedido de compra (PO) rápido al proveedor del producto. */
    fun createQuickPurchaseOrder(product: ProductEntity, quantity: Double, unitPrice: Double) {
        val providerId = product.providerId ?: return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val number = "PO-${now % 1_000_000}"
                val total = quantity * unitPrice
                val invoice = ProviderInvoice(
                    providerId = providerId,
                    number = number,
                    issueDateMillis = now,
                    total = total
                )
                val item = ProviderInvoiceItem(
                    invoiceId = 0,
                    name = product.name,
                    code = product.barcode ?: product.code,
                    quantity = quantity,
                    priceUnit = unitPrice,
                    vatPercent = 0.0,
                    vatAmount = 0.0,
                    total = total
                )
                providerInvoiceRepository.create(invoice, listOf(item))
            }.onSuccess {
                val provName = product.providerName ?: "Proveedor #${product.providerId}"
                _message.value = "Pedido creado a $provName. Podés verlo en Pedidos pendientes."
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudo crear el pedido."
            }
        }
    }


    suspend fun getSizeStockMap(productId: Int): Map<String, Int> {
        return variantDao.getSizeStocksByProductOnce(productId)
            .mapNotNull { v ->
                val size = v.option1?.trim().orEmpty()
                if (size.isBlank()) null else size to v.quantity.coerceAtLeast(0)
            }
            .toMap()
    }

    suspend fun getVariantMatrix(productId: Int): List<VariantEntity> {
        return variantDao.getByProductOnce(productId)
    }

    fun saveSizeStocks(product: ProductEntity, quantitiesBySize: Map<String, Int>) {
        val normalized = quantitiesBySize
            .mapKeys { it.key.trim() }
            .filterKeys { it.isNotBlank() }
            .mapValues { it.value.coerceAtLeast(0) }
            .filterValues { it > 0 }

        viewModelScope.launch {
            if (normalized.values.sum() > product.quantity) {
                _message.value = "La suma de talles no puede superar el stock total del producto."
                return@launch
            }
            if (normalized.isNotEmpty() && normalized.keys.any { size -> !product.sizes.contains(size) }) {
                _message.value = "Hay talles cargados que no existen en la lista de talles del producto."
                return@launch
            }

            runCatching {
                variantDao.deleteSizeStocksByProduct(product.id)
                if (normalized.isNotEmpty()) {
                    val rows = normalized.map { (size, qty) ->
                        VariantEntity(
                            productId = product.id,
                            sku = null,
                            option1 = size,
                            option2 = null,
                            quantity = qty
                        )
                    }
                    variantDao.insertAll(rows)
                }
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudo guardar el stock por talle."
            }
        }
    }

    fun saveVariantMatrix(
        product: ProductEntity,
        rows: List<VariantEntity>
    ) {
        val normalizedRows = rows.mapNotNull { row ->
            val size = row.option1?.trim().orEmpty()
            val color = row.option2?.trim().orEmpty()
            if (size.isBlank() || color.isBlank()) return@mapNotNull null
            row.copy(
                id = 0,
                productId = product.id,
                option1 = size,
                option2 = color,
                quantity = row.quantity.coerceAtLeast(0),
                sku = row.sku?.trim()?.ifBlank { null },
                updatedAt = LocalDate.now()
            )
        }

        viewModelScope.launch {
            val totalByVariants = normalizedRows.sumOf { it.quantity }
            if (totalByVariants > product.quantity) {
                _message.value = "La suma de stock por variantes no puede superar el stock total del producto."
                return@launch
            }
            runCatching {
                variantDao.deleteByProduct(product.id)
                if (normalizedRows.isNotEmpty()) {
                    variantDao.insertAll(normalizedRows)
                }
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudieron guardar las variantes."
            }
        }
    }

    fun importVariantsFromCsv(product: ProductEntity, csvText: String) {
        val lines = csvText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            _message.value = "Pegá al menos una fila con formato talle,color,stock[,sku]."
            return
        }

        val parsed = mutableListOf<VariantEntity>()
        val errors = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val cells = line.split(",").map { it.trim() }
            if (cells.size < 3) {
                errors += "Fila ${index + 1}: faltan columnas (talle,color,stock[,sku])."
                return@forEachIndexed
            }
            val qty = cells[2].toIntOrNull()
            if (qty == null || qty < 0) {
                errors += "Fila ${index + 1}: stock inválido."
                return@forEachIndexed
            }
            parsed += VariantEntity(
                productId = product.id,
                sku = cells.getOrNull(3)?.ifBlank { null },
                option1 = cells[0],
                option2 = cells[1],
                quantity = qty
            )
        }

        if (errors.isNotEmpty()) {
            _message.value = errors.take(2).joinToString("\n")
            return
        }
        saveVariantMatrix(product, parsed)
        _message.value = "Variantes importadas: ${parsed.size}."
    }

    fun clearMessage() {
        _message.value = null
    }
}

private fun ManageProductsUiState.toFilterParams(): ProductFilterParams = ProductFilterParams(
    query = query,
    parentCategory = parentCategory.ifBlank { null },
    category = category.ifBlank { null },
    color = color.ifBlank { null },
    size = size.ifBlank { null },
    minPrice = minPrice.toDoubleOrNull(),
    maxPrice = maxPrice.toDoubleOrNull(),
    onlyLowStock = onlyLowStock,
    onlyNoImage = onlyNoImage,
    onlyNoBarcode = onlyNoBarcode,
    sort = sort
)
