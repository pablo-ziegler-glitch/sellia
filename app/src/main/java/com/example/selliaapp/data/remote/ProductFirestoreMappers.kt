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

    fun toMap(p: ProductEntity, imageUrls: List<String> = emptyList()): Map<String, Any?> {
        val normalizedUrls = imageUrls.ifEmpty { p.imageUrls }
        return mapOf(
            "id"           to p.id,               // también guardamos id para depuración (docId será el id string)
            "code"         to p.code,
            "barcode"      to p.barcode,
            "name"         to p.name,
            "purchasePrice" to p.purchasePrice,
            "price"        to p.price,
            "listPrice"    to p.listPrice,
            "cashPrice"    to p.cashPrice,
            "transferPrice" to p.transferPrice,
            "transferNetPrice" to p.transferNetPrice,
            "mlPrice"      to p.mlPrice,
            "ml3cPrice"    to p.ml3cPrice,
            "ml6cPrice"    to p.ml6cPrice,
            "autoPricing"  to p.autoPricing,
            "quantity"     to p.quantity,
            "description"  to p.description,
            "imageUrl"     to (p.imageUrl ?: normalizedUrls.firstOrNull()),
            "imageUrls"    to normalizedUrls,
            "categoryId"   to p.categoryId,
            "providerId"   to p.providerId,
            "providerName" to p.providerName,
            "providerSku"  to p.providerSku,
            "category"     to p.category,
            "minStock"     to p.minStock,
            "updatedAt"    to p.updatedAt.format(ISO_DATE)
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
            price        = (data["price"] as? Number)?.toDouble(),
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
            category     = data["category"] as? String,
            minStock     = (data["minStock"] as? Number)?.toInt(),
            updatedAt    = updatedAt
        )
        return RemoteProduct(entity = entity, imageUrls = combinedUrls)
    }
}
