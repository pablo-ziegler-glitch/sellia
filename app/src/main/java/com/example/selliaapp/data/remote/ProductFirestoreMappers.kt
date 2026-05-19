package com.example.selliaapp.data.remote


import com.example.selliaapp.data.local.entity.ProductEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Mappers entre ProductEntity (Room) y Map<String, Any?> (Firestore).
 * Guardamos LocalDate como string ISO (yyyy-MM-dd) para legibilidad.
 */
object ProductFirestoreMappers {
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    data class RemoteProduct(
        val entity: ProductEntity,
        val imageUrls: List<String>,
        val remoteDocumentId: String
    )

    data class RemoteTombstone(
        val productUuid: String,
        val legacyLocalId: Int?,
        val deletedAtEpochMs: Long,
        val remoteDocumentId: String
    )

    fun toMap(
        product: ProductEntity,
        imageUrls: List<String> = emptyList(),
        tenantId: String
    ): Map<String, Any?> {
        val normalizedUrls = imageUrls.ifEmpty { product.imageUrls }
        val now = System.currentTimeMillis()
        val productUuid = product.productUuid.ifBlank {
            buildLegacyProductUuid("local-${product.id.takeIf { it > 0 } ?: now}")
        }
        val legacyIds = listOfNotNull(product.legacyLocalId, product.id.takeIf { it > 0 }).distinct()
        return mapOf(
            "id"           to product.id,
            "productUuid"  to productUuid,
            "legacyLocalId" to product.legacyLocalId,
            "legacyLocalIds" to legacyIds,
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
            "gainTargetPercent" to product.gainTargetPercent,
            "publicStatus" to product.publicStatus,
            "isPublic"     to (product.publicStatus == "published"),
            "updatedAt"    to product.updatedAt.format(ISO_DATE),
            "createdAtEpochMs" to (product.createdAtEpochMs.takeIf { it > 0L } ?: now),
            "updatedAtEpochMs" to (product.updatedAtEpochMs.takeIf { it > 0L } ?: now),
            "deletedAtEpochMs" to product.deletedAtEpochMs,
            "syncVersion" to product.syncVersion,
            "syncStatus" to product.syncStatus
        )
    }

    fun fromMap(docId: String, data: Map<String, Any?>): RemoteProduct {
        val updatedAtStr = data["updatedAt"] as? String
        val updatedAt = updatedAtStr?.let { LocalDate.parse(it, ISO_DATE) } ?: LocalDate.now()
        val legacyImage = data["imageUrl"] as? String
        val imageUrls = (data["imageUrls"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val combinedUrls = (listOfNotNull(legacyImage) + imageUrls).distinct()
        val publicStatus = (data["publicStatus"] as? String)
            ?.lowercase()
            ?.takeIf { it == "published" || it == "draft" }
            ?: if ((data["isPublic"] as? Boolean) == true) "published" else "draft"
        val explicitProductUuid = (data["productUuid"] as? String)?.trim().orEmpty()
        val resolvedProductUuid = when {
            explicitProductUuid.isNotBlank() -> explicitProductUuid
            docId.looksLikeUuid() -> docId
            docId.toIntOrNull() != null -> buildLegacyProductUuid("legacy-local-${docId.toInt()}")
            else -> buildLegacyProductUuid(
                listOfNotNull(
                    data["code"] as? String,
                    data["barcode"] as? String,
                    data["name"] as? String
                ).joinToString("|")
            )
        }
        val legacyLocalId = (data["legacyLocalId"] as? Number)?.toInt() ?: docId.toIntOrNull()
        val createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong()
            ?: ((data["updatedAtEpochMs"] as? Number)?.toLong() ?: (updatedAt.toEpochDay() * MILLIS_PER_DAY))
        val updatedAtEpochMs = (data["updatedAtEpochMs"] as? Number)?.toLong()
            ?: createdAtEpochMs
        val entity = ProductEntity(
            id           = ((data["id"] as? Number)?.toInt() ?: 0).takeIf { it > 0 } ?: (legacyLocalId ?: 0),
            productUuid  = resolvedProductUuid,
            legacyLocalId = legacyLocalId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = (data["deletedAtEpochMs"] as? Number)?.toLong(),
            syncVersion = (data["syncVersion"] as? Number)?.toLong() ?: 0L,
            syncStatus = (data["syncStatus"] as? String)?.ifBlank { "SYNCED" } ?: "SYNCED",
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
            gainTargetPercent = (data["gainTargetPercent"] as? Number)?.toDouble(),
            publicStatus = publicStatus,
            updatedAt    = updatedAt
        )
        return RemoteProduct(entity = entity, imageUrls = combinedUrls, remoteDocumentId = docId)
    }

    fun tombstoneFromMap(docId: String, data: Map<String, Any?>): RemoteTombstone? {
        val explicitUuid = (data["productUuid"] as? String)?.trim().orEmpty()
        val resolvedUuid = when {
            explicitUuid.isNotBlank() -> explicitUuid
            docId.looksLikeUuid() -> docId
            docId.toIntOrNull() != null -> buildLegacyProductUuid("legacy-local-${docId.toInt()}")
            else -> return null
        }
        val deletedAtEpochMs = (data["deletedAtEpochMs"] as? Number)?.toLong() ?: return null
        val legacyLocalId = (data["legacyLocalId"] as? Number)?.toInt()
            ?: (data["productId"] as? Number)?.toInt()
            ?: docId.toIntOrNull()
        return RemoteTombstone(
            productUuid = resolvedUuid,
            legacyLocalId = legacyLocalId,
            deletedAtEpochMs = deletedAtEpochMs,
            remoteDocumentId = docId
        )
    }

    fun buildLegacyProductUuid(seed: String): String {
        val normalized = seed.trim().ifBlank { "legacy-empty" }
        return UUID.nameUUIDFromBytes(normalized.toByteArray()).toString()
    }

    private fun String.looksLikeUuid(): Boolean {
        return UUID_REGEX.matches(this)
    }

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private const val MILLIS_PER_DAY = 86_400_000L
}
