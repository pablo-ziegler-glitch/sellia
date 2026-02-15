package com.example.selliaapp.repository.impl

import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.PublicCatalogProduct
import com.example.selliaapp.repository.TenantSummary
import com.example.selliaapp.repository.ViewerStoreRepository
import com.example.selliaapp.repository.ViewerStoreSelection
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewerStoreRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : ViewerStoreRepository {

    override suspend fun fetchViewerStoreSelection(): Result<ViewerStoreSelection> = withContext(io) {
        runCatching {
            val uid = requireUid()
            val userSnapshot = firestore.collection("users").document(uid).get().await()
            val selectedStoreId = userSnapshot.getString("selectedCatalogTenantId")?.takeIf { it.isNotBlank() }
            val followedStoreIds = (userSnapshot.get("followedTenantIds") as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }
                .distinct()

            val followedStores = followedStoreIds.mapNotNull { storeId ->
                val snapshot = firestore.collection("tenant_directory").document(storeId).get().await()
                if (!snapshot.exists()) {
                    return@mapNotNull null
                }
                val name = snapshot.getString("name")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                TenantSummary(id = storeId, name = name)
            }.sortedBy { it.name.lowercase() }

            ViewerStoreSelection(
                followedStores = followedStores,
                selectedStoreId = selectedStoreId?.takeIf { selected -> followedStores.any { it.id == selected } }
                    ?: followedStores.firstOrNull()?.id
            )
        }
    }

    override suspend fun followStore(store: TenantSummary): Result<Unit> = withContext(io) {
        runCatching {
            val uid = requireUid()
            val userRef = firestore.collection("users").document(uid)
            userRef.set(
                mapOf(
                    "followedTenantIds" to FieldValue.arrayUnion(store.id),
                    "selectedCatalogTenantId" to store.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    override suspend fun selectStore(storeId: String): Result<Unit> = withContext(io) {
        runCatching {
            val uid = requireUid()
            firestore.collection("users").document(uid)
                .set(
                    mapOf(
                        "selectedCatalogTenantId" to storeId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    override suspend fun fetchPublicCatalog(storeId: String): Result<List<PublicCatalogProduct>> = withContext(io) {
        runCatching {
            val snapshot = firestore.collection("tenants")
                .document(storeId)
                .collection("public_products")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                val name = doc.getString("name")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                PublicCatalogProduct(
                    id = id,
                    name = name,
                    imageUrl = doc.getString("imageUrl")
                        ?: (doc.get("imageUrls") as? List<*>)?.firstOrNull() as? String,
                    listPrice = doc.getDouble("listPrice"),
                    cashPrice = doc.getDouble("cashPrice"),
                    transferPrice = doc.getDouble("transferPrice"),
                    category = doc.getString("parentCategory"),
                    subcategory = doc.getString("category")
                )
            }.sortedBy { it.name.lowercase() }
        }
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("Sesión no disponible")
    }
}
