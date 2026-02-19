package com.example.selliaapp.data.remote

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.local.entity.ProductEntity
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
        val docRef = if (product.id != 0) col.document(product.id.toString()) else col.document()
        val map = ProductFirestoreMappers.toMap(
            product = product,
            imageUrls = imageUrls,
            tenantId = tenantId
        ).toMutableMap()
        map["id"] = docRef.id.toIntOrNull() ?: product.id
        if (product.id != 0) {
            val batch = firestore.batch()
            batch.set(docRef, map)
            batch.delete(deletionsCol.document(product.id.toString()))
            batch.commit().await()
        } else {
            docRef.set(map).await()
        }
    }

    suspend fun upsertAll(
        products: List<ProductEntity>,
        imageUrlsByProductId: Map<Int, List<String>> = emptyMap()
    ) {
        if (products.isEmpty()) return
        val tenantId = tenantProvider.requireTenantId()
        val col = firestore.collection("tenants").document(tenantId).collection("products")
        val deletionsCol = firestore.collection("tenants").document(tenantId).collection("product_deletions")
        val batch = firestore.batch()
        products.forEach { product ->
            if (product.id == 0) return@forEach
            val doc = col.document(product.id.toString())
            val imageUrls = imageUrlsByProductId[product.id].orEmpty()
            batch.set(
                doc,
                ProductFirestoreMappers.toMap(
                    product = product,
                    imageUrls = imageUrls,
                    tenantId = tenantId
                ),
                SetOptions.merge()
            )
            batch.delete(deletionsCol.document(product.id.toString()))
        }
        batch.commit().await()
    }

    suspend fun deleteById(id: Int) {
        if (id == 0) return
        val tenantId = tenantProvider.requireTenantId()
        val col = firestore.collection("tenants").document(tenantId).collection("products")
        val deletionsCol = firestore.collection("tenants").document(tenantId).collection("product_deletions")
        val now = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.delete(col.document(id.toString()))
        batch.set(
            deletionsCol.document(id.toString()),
            mapOf(
                "productId" to id,
                "deletedAt" to FieldValue.serverTimestamp(),
                "deletedAtEpochMs" to now,
                "purgeBackup" to true
            ),
            SetOptions.merge()
        )
        batch.commit().await()
    }

    suspend fun listAll(): List<ProductFirestoreMappers.RemoteProduct> {
        val snap = collection().get().await()
        val deletedIds = deletionsCollection().get().await().documents.mapNotNull { it.id.toIntOrNull() }.toSet()
        return snap.documents.mapNotNull { doc ->
            if (doc.id.toIntOrNull() in deletedIds) {
                return@mapNotNull null
            }
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            ProductFirestoreMappers.fromMap(doc.id, data)
        }
    }
}
