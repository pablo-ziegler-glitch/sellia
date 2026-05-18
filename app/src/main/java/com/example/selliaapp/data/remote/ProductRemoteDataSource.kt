package com.example.selliaapp.data.remote

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.local.entity.ProductEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class ProductRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider
) {
    private suspend fun deletionsCollection() =
        firestore.collection("tenants")
            .document(tenantProvider.requireTenantId())
            .collection("product_deletions")

    private suspend fun collection() =
        firestore.collection("tenants")
            .document(tenantProvider.requireTenantId())
            .collection("products")

    suspend fun upsert(product: ProductEntity, imageUrls: List<String> = emptyList()) {
        val tenantId = tenantProvider.requireTenantId()
        val col = firestore.collection("tenants").document(tenantId).collection("products")
        val deletionsCol = firestore.collection("tenants").document(tenantId).collection("product_deletions")
        val resolvedUuid = resolveProductUuid(product)
        val docRef = col.document(resolvedUuid)
        val map = ProductFirestoreMappers.toMap(
            product = product.copy(productUuid = resolvedUuid),
            imageUrls = imageUrls,
            tenantId = tenantId
        ).toMutableMap()
        map["id"] = product.id
        map["productUuid"] = resolvedUuid
        val batch = firestore.batch()
        batch.set(docRef, map)
        batch.delete(deletionsCol.document(resolvedUuid))
        batch.commit().await()
    }

    suspend fun upsertAll(
        products: List<ProductEntity>,
        imageUrlsByProductId: Map<Int, List<String>> = emptyMap()
    ) {
        if (products.isEmpty()) return
        val tenantId = tenantProvider.requireTenantId()
        val col = firestore.collection("tenants").document(tenantId).collection("products")
        val deletionsCol = firestore.collection("tenants").document(tenantId).collection("product_deletions")
        products.chunked(MAX_BATCH_OPS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { product ->
                val resolvedUuid = resolveProductUuid(product)
                val doc = col.document(resolvedUuid)
                val imageUrls = imageUrlsByProductId[product.id].orEmpty()
                batch.set(
                    doc,
                    ProductFirestoreMappers.toMap(
                        product = product.copy(productUuid = resolvedUuid),
                        imageUrls = imageUrls,
                        tenantId = tenantId
                    ),
                    SetOptions.merge()
                )
                batch.delete(deletionsCol.document(resolvedUuid))
            }
            batch.commit().await()
        }
    }

    @Deprecated("Usar markDeletedByUuid para identidad estable.")
    suspend fun deleteById(id: Int) {
        if (id <= 0) return
        val legacyUuid = ProductFirestoreMappers.buildLegacyProductUuid("legacy-local-$id")
        markDeletedByUuid(
            productUuid = legacyUuid,
            deletedAtEpochMs = System.currentTimeMillis(),
            legacyLocalId = id
        )
    }

    suspend fun markDeletedByUuid(
        productUuid: String,
        deletedAtEpochMs: Long,
        legacyLocalId: Int? = null
    ) {
        if (productUuid.isBlank()) return
        val tenantId = tenantProvider.requireTenantId()
        val col = firestore.collection("tenants").document(tenantId).collection("products")
        val deletionsCol = firestore.collection("tenants").document(tenantId).collection("product_deletions")
        val batch = firestore.batch()
        batch.set(
            col.document(productUuid),
            mapOf(
                "productUuid" to productUuid,
                "deletedAtEpochMs" to deletedAtEpochMs,
                "syncStatus" to "DELETED",
                "visible" to false,
                "updatedAtEpochMs" to deletedAtEpochMs
            ),
            SetOptions.merge()
        )
        batch.set(
            deletionsCol.document(productUuid),
            mapOf(
                "productUuid" to productUuid,
                "legacyLocalId" to legacyLocalId,
                "productId" to legacyLocalId,
                "deletedAt" to FieldValue.serverTimestamp(),
                "deletedAtEpochMs" to deletedAtEpochMs,
                "purgeBackup" to false
            ),
            SetOptions.merge()
        )
        batch.commit().await()
    }

    suspend fun listAll(): List<ProductFirestoreMappers.RemoteProduct> {
        val allDocs = mutableListOf<DocumentSnapshot>()
        var lastDoc: DocumentSnapshot? = null
        do {
            val query = collection()
                .orderBy(FieldPath.documentId())
                .let { if (lastDoc != null) it.startAfter(lastDoc!!) else it }
                .limit(PAGE_SIZE)
            val page = query.get().await()
            allDocs.addAll(page.documents)
            lastDoc = page.documents.lastOrNull()
        } while (page.size() >= PAGE_SIZE)
        val tombstones = mutableMapOf<String, ProductFirestoreMappers.RemoteTombstone>()
        var lastDeletionDoc: DocumentSnapshot? = null
        do {
            val query = deletionsCollection()
                .orderBy(FieldPath.documentId())
                .let { if (lastDeletionDoc != null) it.startAfter(lastDeletionDoc!!) else it }
                .limit(PAGE_SIZE)
            val page = query.get().await()
            page.documents.forEach { doc ->
                @Suppress("UNCHECKED_CAST")
                val data = doc.data as? Map<String, Any?> ?: return@forEach
                val marker = ProductFirestoreMappers.tombstoneFromMap(doc.id, data) ?: return@forEach
                tombstones[marker.productUuid] = marker
            }
            lastDeletionDoc = page.documents.lastOrNull()
        } while (page.size() >= PAGE_SIZE)

        return allDocs.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            val remote = ProductFirestoreMappers.fromMap(doc.id, data)
            val tombstone = tombstones[remote.entity.productUuid]
            if (tombstone != null && tombstone.deletedAtEpochMs > remote.entity.updatedAtEpochMs) {
                return@mapNotNull null
            }
            remote
        }
    }

    private fun resolveProductUuid(product: ProductEntity): String {
        val explicit = product.productUuid.trim()
        if (explicit.isNotBlank()) return explicit
        val legacyId = product.legacyLocalId ?: product.id.takeIf { it > 0 }
        if (legacyId != null) {
            return ProductFirestoreMappers.buildLegacyProductUuid("legacy-local-$legacyId")
        }
        return java.util.UUID.randomUUID().toString()
    }

    companion object {
        private const val PAGE_SIZE = 500L
        private const val MAX_BATCH_OPS = 450
    }
}
