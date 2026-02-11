package com.example.selliaapp.domain.product

import com.example.selliaapp.data.local.entity.ProductEntity
import java.util.Locale

enum class ProductSortOption(val label: String) {
    UPDATED_DESC("Último actualizado"),
    UPDATED_ASC("Más antiguo"),
    PRICE_DESC("Precio mayor a menor"),
    PRICE_ASC("Precio menor a mayor"),
    STOCK_DESC("Mayor stock"),
    STOCK_ASC("Menor stock"),
    NAME_ASC("Nombre A-Z"),
    NAME_DESC("Nombre Z-A")
}

data class ProductFilterParams(
    val query: String = "",
    val parentCategory: String? = null,
    val category: String? = null,
    val color: String? = null,
    val size: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val onlyLowStock: Boolean = false,
    val onlyNoImage: Boolean = false,
    val onlyNoBarcode: Boolean = false,
    val sort: ProductSortOption = ProductSortOption.UPDATED_DESC
)

fun filterAndSortProducts(
    products: List<ProductEntity>,
    params: ProductFilterParams
): List<ProductEntity> {
    val query = params.query.trim().lowercase(Locale.getDefault())

    return products
        .asSequence()
        .filter { product ->
            matchesText(product, query) &&
                matchesOptionalContains(product.parentCategory, params.parentCategory) &&
                matchesOptionalContains(product.category, params.category) &&
                matchesOptionalContains(product.color, params.color) &&
                matchesSize(product, params.size) &&
                matchesPriceRange(product, params.minPrice, params.maxPrice) &&
                (!params.onlyLowStock || ((product.minStock ?: 0) > 0 && product.quantity < (product.minStock ?: 0))) &&
                (!params.onlyNoImage || product.imageUrls.isEmpty()) &&
                (!params.onlyNoBarcode || product.barcode.isNullOrBlank())
        }
        .sortedWith(productComparator(params.sort))
        .toList()
}

private fun matchesText(product: ProductEntity, query: String): Boolean {
    if (query.isBlank()) return true
    return product.searchableText().contains(query)
}

private fun ProductEntity.searchableText(): String = buildList {
    add(name)
    add(code)
    add(barcode)
    add(description)
    add(parentCategory)
    add(category)
    add(color)
    add(providerName)
    add(providerSku)
    add(brand)
    add(quantity.toString())
    add(minStock?.toString())
    add(listPrice?.toString())
    add(cashPrice?.toString())
    add(transferPrice?.toString())
    add(purchasePrice?.toString())
    addAll(sizes)
}.filterNotNull().joinToString(" ").lowercase(Locale.getDefault())

private fun matchesOptionalContains(source: String?, filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (source.isNullOrBlank()) return false
    return source.contains(filter.trim(), ignoreCase = true)
}

private fun matchesSize(product: ProductEntity, size: String?): Boolean {
    if (size.isNullOrBlank()) return true
    return product.sizes.any { it.contains(size.trim(), ignoreCase = true) }
}

private fun matchesPriceRange(product: ProductEntity, min: Double?, max: Double?): Boolean {
    if (min == null && max == null) return true
    val price = product.referencePrice()
    if (price == null) return false
    val passMin = min == null || price >= min
    val passMax = max == null || price <= max
    return passMin && passMax
}

private fun ProductEntity.referencePrice(): Double? =
    listPrice ?: cashPrice ?: transferPrice ?: purchasePrice

private fun productComparator(sort: ProductSortOption): Comparator<ProductEntity> {
    val compareByName = compareBy<ProductEntity> { it.name.lowercase(Locale.getDefault()) }
    return when (sort) {
        ProductSortOption.UPDATED_DESC -> compareByDescending<ProductEntity> { it.updatedAt }.then(compareByName)
        ProductSortOption.UPDATED_ASC -> compareBy<ProductEntity> { it.updatedAt }.then(compareByName)
        ProductSortOption.PRICE_DESC -> compareByDescending<ProductEntity> { it.referencePrice() ?: Double.MIN_VALUE }.then(compareByName)
        ProductSortOption.PRICE_ASC -> compareBy<ProductEntity> { it.referencePrice() ?: Double.MAX_VALUE }.then(compareByName)
        ProductSortOption.STOCK_DESC -> compareByDescending<ProductEntity> { it.quantity }.then(compareByName)
        ProductSortOption.STOCK_ASC -> compareBy<ProductEntity> { it.quantity }.then(compareByName)
        ProductSortOption.NAME_ASC -> compareByName
        ProductSortOption.NAME_DESC -> compareByName.reversed()
    }
}
