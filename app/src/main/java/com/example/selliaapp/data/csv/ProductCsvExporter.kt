package com.example.selliaapp.data.csv

import com.example.selliaapp.data.local.entity.ProductEntity

object ProductCsvExporter {
    private val header = listOf(
        "code",
        "barcode",
        "name",
        "quantity",
        "purchase_price",
        "price",
        "list_price",
        "cash_price",
        "transfer_price",
        "transfer_net_price",
        "ml_price",
        "ml_3c_price",
        "ml_6c_price",
        "category",
        "provider",
        "provider_sku",
        "min_stock",
        "description",
        "image_url",
        "image_urls",
        "updated_at"
    )

    fun export(products: List<ProductEntity>): String {
        return buildString {
            appendLine(CsvExportUtils.line(header))
            products.forEach { product ->
                val imageUrls = product.imageUrls.joinToString("|").ifBlank { "" }
                appendLine(
                    CsvExportUtils.line(
                        listOf(
                            product.code,
                            product.barcode,
                            product.name,
                            product.quantity.toString(),
                            product.purchasePrice?.toString(),
                            product.price?.toString(),
                            product.listPrice?.toString(),
                            product.cashPrice?.toString(),
                            product.transferPrice?.toString(),
                            product.transferNetPrice?.toString(),
                            product.mlPrice?.toString(),
                            product.ml3cPrice?.toString(),
                            product.ml6cPrice?.toString(),
                            product.category,
                            product.providerName,
                            product.providerSku,
                            product.minStock?.toString(),
                            product.description,
                            product.imageUrl,
                            imageUrls,
                            product.updatedAt.toString()
                        )
                    )
                )
            }
        }
    }

    fun exportFileName(timestamp: String): String = "productos_$timestamp.csv"

    fun mimeType(): String = "text/csv"
}
