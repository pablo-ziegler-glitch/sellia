package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.remote.AppNotification
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.NotificationRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : NotificationRepository {

    companion object {
        const val COLLECTION = "notifications"
    }

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.map { doc ->
                    val data = doc.data ?: emptyMap()
                    AppNotification(
                        id = doc.id,
                        userId = (data["userId"] as? String).orEmpty(),
                        type = (data["type"] as? String).orEmpty(),
                        title = (data["title"] as? String).orEmpty(),
                        body = (data["body"] as? String).orEmpty(),
                        read = (data["read"] as? Boolean) ?: false,
                        actionRoute = data["actionRoute"] as? String,
                        createdAt = data["createdAt"] as? Timestamp
                    )
                } ?: emptyList()
                trySend(notifications)
            }
        awaitClose { registration.remove() }
    }

    override fun observeUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> =
        withContext(io) {
            runCatching {
                firestore.collection(COLLECTION)
                    .document(notificationId)
                    .update("read", true)
                    .await()
                Unit
            }
        }

    override suspend fun markAllAsRead(userId: String): Result<Unit> =
        withContext(io) {
            runCatching {
                val unread = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
                    .get()
                    .await()
                val batch = firestore.batch()
                unread.documents.forEach { doc ->
                    batch.update(doc.reference, "read", true)
                }
                batch.commit().await()
                Unit
            }
        }
}
