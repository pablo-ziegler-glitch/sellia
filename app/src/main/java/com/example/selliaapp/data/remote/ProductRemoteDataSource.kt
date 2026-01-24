package com.example.selliaapp.data.remote

import com.example.selliaapp.data.local.entity.ProductEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class ProductRemoteDataSource(
    private val firestore: FirebaseFirestore
) {
    private val col = firestore.collection("products")

    suspend fun upsert(product: ProductEntity, imageUrls: List<String> = emptyList()) {
        val docRef = if (product.id != 0) col.document(product.id.toString()) else col.document()
        val map = ProductFirestoreMappers.toMap(product, imageUrls).toMutableMap()
        map["id"] = docRef.id.toIntOrNull() ?: product.id
        docRef.set(map).await()
    }

    suspend fun upsertAll(
        products: List<ProductEntity>,
        imageUrlsByProductId: Map<Int, List<String>> = emptyMap()
    ) {
        if (products.isEmpty()) return
        val batch = firestore.batch()
        products.forEach { product ->
            if (product.id == 0) return@forEach
            val doc = col.document(product.id.toString())
            val imageUrls = imageUrlsByProductId[product.id].orEmpty()
            batch.set(doc, ProductFirestoreMappers.toMap(product, imageUrls), SetOptions.merge())
        }
        batch.commit().await()
    }

    suspend fun deleteById(id: Int) {
        if (id == 0) return
        col.document(id.toString()).delete().await()
    }

    suspend fun listAll(): List<ProductFirestoreMappers.RemoteProduct> {
        val snap = col.get().await()
        return snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            ProductFirestoreMappers.fromMap(doc.id, data)
        }
    }
}
