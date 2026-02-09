package com.example.selliaapp.repository.impl

import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.TenantDirectoryRepository
import com.example.selliaapp.repository.TenantSummary
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantDirectoryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : TenantDirectoryRepository {

    override suspend fun fetchTenants(): Result<List<TenantSummary>> = withContext(io) {
        runCatching {
            val snapshot = firestore.collection("tenant_directory")
                .orderBy("name")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name")?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                TenantSummary(id = doc.id, name = name)
            }
        }
    }
}
