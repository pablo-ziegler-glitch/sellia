package com.example.selliaapp.data.remote


import com.example.selliaapp.data.local.entity.ProductEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Mappers entre ProductEntity (Room) y Map<String, Any?> (Firestore).
 * Guardamos LocalDate como string ISO (yyyy-MM-dd) para legibilidad.
 */
object ProductFirestoreMappers {
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    data class RemoteProduct(
        val entity: ProductEntity,
        val imageUrls: List<String>
    )

    fun toMap(
        product: ProductEntity,
        imageUrls: List<String> = emptyList(),
        tenantId: String
    ): Map<String, Any?> {
        val normalizedUrls = imageUrls.ifEmpty { product.imageUrls }
        return mapOf(
            "id"           to product.id,               // también guardamos id para depuración (docId será el id string)
            "tenantId"     to tenantId,
            "code"         to product.code,
            "barcode"      to product.barcode,
            "name"         to product.name,
            "purchasePrice" to product.purchasePrice,
            "listPrice"    to product.listPrice,
            "cashPrice"    to product.cashPrice,
            "transferPrice" to product.transferPrice,
            "transferNetPrice" to product.transferNetPrice,
            "mlPrice"      to product.mlPrice,
            "ml3cPrice"    to product.ml3cPrice,
            "ml6cPrice"    to product.ml6cPrice,
            "autoPricing"  to product.autoPricing,
            "quantity"     to product.quantity,
            "description"  to product.description,
            "imageUrl"     to (product.imageUrl ?: normalizedUrls.firstOrNull()),
            "imageUrls"    to normalizedUrls,
            "categoryId"   to product.categoryId,
            "providerId"   to product.providerId,
            "providerName" to product.providerName,
            "providerSku"  to product.providerSku,
            "brand"        to product.brand,
            "parentCategory" to product.parentCategory,
            "category"     to product.category,
            "color"        to product.color,
            "sizes"        to product.sizes,
            "minStock"     to product.minStock,
            "updatedAt"    to product.updatedAt.format(ISO_DATE)
        )
    }

    fun fromMap(docId: String, data: Map<String, Any?>): RemoteProduct {
        val updatedAtStr = data["updatedAt"] as? String
        val updatedAt = updatedAtStr?.let { LocalDate.parse(it, ISO_DATE) } ?: LocalDate.now()
        val legacyImage = data["imageUrl"] as? String
        val imageUrls = (data["imageUrls"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val combinedUrls = (listOfNotNull(legacyImage) + imageUrls).distinct()
        val entity = ProductEntity(
            id           = docId.toIntOrNull() ?: 0, // si docId es numérico, lo usamos; si no, 0 para insert local
            code         = data["code"] as? String,
            barcode      = data["barcode"] as? String,
            name         = (data["name"] as? String).orEmpty(),
            purchasePrice = (data["purchasePrice"] as? Number)?.toDouble(),
            listPrice    = (data["listPrice"] as? Number)?.toDouble(),
            cashPrice    = (data["cashPrice"] as? Number)?.toDouble(),
            transferPrice = (data["transferPrice"] as? Number)?.toDouble(),
            transferNetPrice = (data["transferNetPrice"] as? Number)?.toDouble(),
            mlPrice      = (data["mlPrice"] as? Number)?.toDouble(),
            ml3cPrice    = (data["ml3cPrice"] as? Number)?.toDouble(),
            ml6cPrice    = (data["ml6cPrice"] as? Number)?.toDouble(),
            autoPricing  = (data["autoPricing"] as? Boolean) ?: false,
            quantity     = (data["quantity"] as? Number)?.toInt() ?: 0,
            description  = data["description"] as? String,
            imageUrl     = legacyImage ?: combinedUrls.firstOrNull(),
            imageUrls    = combinedUrls,
            categoryId   = (data["categoryId"] as? Number)?.toInt(),
            providerId   = (data["providerId"] as? Number)?.toInt(),
            providerName = data["providerName"] as? String,
            providerSku  = data["providerSku"] as? String,
            brand        = data["brand"] as? String,
            parentCategory = data["parentCategory"] as? String,
            category     = data["category"] as? String,
            color        = data["color"] as? String,
            sizes        = (data["sizes"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            minStock     = (data["minStock"] as? Number)?.toInt(),
            updatedAt    = updatedAt
        )
        return RemoteProduct(entity = entity, imageUrls = combinedUrls)
    }
}
