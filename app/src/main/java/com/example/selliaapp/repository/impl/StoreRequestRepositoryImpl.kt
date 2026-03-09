package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.remote.StoreRequest
import com.example.selliaapp.data.remote.StoreRequestStatus
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.StoreRequestRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StoreRequestRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : StoreRequestRepository {

    companion object {
        const val COLLECTION = "store_requests"
    }

    override suspend fun submitRequest(request: StoreRequest): Result<String> =
        withContext(io) {
            runCatching {
                val data = request.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val ref = firestore.collection(COLLECTION).add(data).await()
                ref.id
            }
        }

    override suspend fun getMyRequests(userId: String): Result<List<StoreRequest>> =
        withContext(io) {
            runCatching {
                val snapshot = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
                snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    StoreRequest(
                        id = doc.id,
                        userId = (data["userId"] as? String).orEmpty(),
                        email = (data["email"] as? String).orEmpty(),
                        storeName = (data["storeName"] as? String).orEmpty(),
                        storeDescription = (data["storeDescription"] as? String).orEmpty(),
                        storePhone = (data["storePhone"] as? String).orEmpty(),
                        status = StoreRequestStatus.fromRaw(data["status"] as? String),
                        createdAt = data["createdAt"] as? Timestamp,
                        updatedAt = data["updatedAt"] as? Timestamp
                    )
                }
            }
        }

    override suspend fun getPendingRequests(): Result<List<StoreRequest>> =
        withContext(io) {
            runCatching {
                val snapshot = firestore.collection(COLLECTION)
                    .whereEqualTo("status", StoreRequestStatus.PENDING.raw)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get()
                    .await()
                snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    StoreRequest(
                        id = doc.id,
                        userId = (data["userId"] as? String).orEmpty(),
                        email = (data["email"] as? String).orEmpty(),
                        storeName = (data["storeName"] as? String).orEmpty(),
                        storeDescription = (data["storeDescription"] as? String).orEmpty(),
                        storePhone = (data["storePhone"] as? String).orEmpty(),
                        status = StoreRequestStatus.fromRaw(data["status"] as? String),
                        createdAt = data["createdAt"] as? Timestamp,
                        updatedAt = data["updatedAt"] as? Timestamp
                    )
                }
            }
        }

    override suspend fun updateRequestStatus(requestId: String, status: String): Result<Unit> =
        withContext(io) {
            runCatching {
                firestore.collection(COLLECTION)
                    .document(requestId)
                    .update(
                        "status", status,
                        "updatedAt", FieldValue.serverTimestamp()
                    )
                    .await()
            }
        }
}
