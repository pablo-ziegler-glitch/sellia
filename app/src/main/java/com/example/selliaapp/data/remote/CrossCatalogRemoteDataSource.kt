package com.example.selliaapp.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val MIN_BARCODE_LENGTH = 4
private const val MAX_BARCODE_LENGTH = 64

data class CrossCatalogEntry(
    val barcode: String,
    val name: String,
    val brand: String?,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?
)

data class CrossCatalogAuditContext(
    val tenantId: String,
    val storeName: String?,
    val updatedByUid: String?,
    val updatedByEmail: String?
)

class InvalidCrossCatalogDataException(message: String) : IllegalArgumentException(message)

class CrossCatalogRemoteDataSource(
    private val firestore: FirebaseFirestore
) {
    private fun normalizeBarcode(raw: String): String = raw.trim()

    private fun collection() = firestore.collection("cross_catalog")

    private fun validateBarcode(barcode: String) {
        if (barcode.length !in MIN_BARCODE_LENGTH..MAX_BARCODE_LENGTH) {
            throw InvalidCrossCatalogDataException(
                "Barcode inválido: debe tener entre $MIN_BARCODE_LENGTH y $MAX_BARCODE_LENGTH caracteres."
            )
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) {
            throw InvalidCrossCatalogDataException("El nombre del producto no puede estar vacío para catálogo CROSS.")
        }
    }

    suspend fun findByBarcode(rawBarcode: String): CrossCatalogEntry? {
        val barcode = normalizeBarcode(rawBarcode)
        if (barcode.isBlank()) return null
        validateBarcode(barcode)
        val snapshot = collection().document(barcode).get().await()
        if (!snapshot.exists()) return null
        val name = snapshot.getString("name")?.trim().orEmpty()
        if (name.isBlank()) return null
        return CrossCatalogEntry(
            barcode = snapshot.getString("barcode")?.trim().takeUnless { it.isNullOrBlank() } ?: barcode,
            name = name,
            brand = snapshot.getString("brand")?.trim()?.takeIf { it.isNotBlank() },
            createdAt = snapshot.getTimestamp("createdAt"),
            updatedAt = snapshot.getTimestamp("updatedAt")
        )
    }

    suspend fun upsertByBarcode(
        rawBarcode: String,
        name: String,
        brand: String?,
        audit: CrossCatalogAuditContext
    ) {
        val barcode = normalizeBarcode(rawBarcode)
        val normalizedName = name.trim()
        val normalizedBrand = brand?.trim()?.takeIf { it.isNotBlank() }
        validateBarcode(barcode)
        validateName(normalizedName)

        val ref = collection().document(barcode)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(ref)
            val payload = mutableMapOf<String, Any?>(
                "barcode" to barcode,
                "name" to normalizedName,
                "brand" to normalizedBrand,
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to mapOf(
                    "uid" to audit.updatedByUid,
                    "email" to audit.updatedByEmail,
                    "tenantId" to audit.tenantId,
                    "storeName" to audit.storeName,
                ).filterValues { it != null },
                "createdAt" to (existing.get("createdAt") ?: FieldValue.serverTimestamp()),
                "createdBy" to (
                    existing.get("createdBy")
                        ?: mapOf(
                            "uid" to audit.updatedByUid,
                            "email" to audit.updatedByEmail,
                            "tenantId" to audit.tenantId,
                            "storeName" to audit.storeName,
                        ).filterValues { it != null }
                    )
            ).filterValues { it != null }
            transaction.set(ref, payload, SetOptions.merge())
            null
        }.await()
    }
}
