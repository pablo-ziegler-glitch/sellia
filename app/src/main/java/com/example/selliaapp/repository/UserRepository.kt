// File: UserRepository.kt
package com.example.selliaapp.repository

import android.content.Context
import android.net.Uri
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.csv.UserCsvImporter
import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.data.model.User
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.di.AppModule
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) {

    fun observeUsers(): Flow<List<User>> = userDao.observeAll()
        .onStart { /* hook para loading si querés */ }

    suspend fun insert(user: User): Long = withContext(io) {
        val rowId = userDao.insert(user)
        upsertCloudUser(user)
        rowId
    }

    suspend fun countUsers(): Int = withContext(io) { userDao.countUsers() }

    suspend fun update(user: User): Int = withContext(io) {
        val rows = userDao.update(user)
        upsertCloudUser(user)
        rows
    }

    suspend fun delete(user: User): Int = withContext(io) {
        val rows = userDao.delete(user)
        deleteCloudUser(user)
        rows
    }

    suspend fun syncFromCloud(): Result<Unit> = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val snapshot = firestore.collection("tenant_users")
                .whereEqualTo("tenantId", tenantId)
                .get()
                .await()
            snapshot.documents.forEach { doc ->
                val email = doc.getString("email")?.trim().orEmpty()
                val name = doc.getString("name")?.trim().orEmpty()
                val role = doc.getString("role")?.trim().orEmpty()
                val isActive = doc.getBoolean("isActive") ?: true
                if (email.isBlank() || name.isBlank() || role.isBlank()) return@forEach
                val existing = userDao.getByEmail(email)
                if (existing == null) {
                    userDao.insert(User(name = name, email = email, role = role, isActive = isActive))
                } else {
                    userDao.update(
                        existing.copy(name = name, role = role, isActive = isActive)
                    )
                }
            }
        }
    }

    suspend fun importUsersFromFile(context: Context, uri: Uri): ImportResult = withContext(io) {
        val rows = UserCsvImporter.parseFile(context.contentResolver, uri)
        if (rows.isEmpty()) {
            return@withContext ImportResult(0, 0, listOf("El archivo no contiene filas válidas."))
        }

        var inserted = 0
        var updated = 0
        val errors = mutableListOf<String>()

        rows.forEachIndexed { idx, row ->
            val name = row.name.trim()
            val email = row.email.trim()
            val role = row.role.trim()
            if (name.isBlank() || email.isBlank() || role.isBlank()) {
                errors += "L${idx + 2}: name, email y role son obligatorios."
                return@forEachIndexed
            }

            try {
                val existing = userDao.getByEmail(email)
                if (existing == null) {
                    userDao.insert(User(name = name, email = email, role = role, isActive = true))
                    inserted++
                } else {
                    userDao.update(existing.copy(name = name, role = role))
                    updated++
                }
            } catch (t: Throwable) {
                errors += "L${idx + 2}: ${t.message ?: t::class.java.simpleName}"
            }
        }

        ImportResult(inserted, updated, errors)
    }

    private suspend fun upsertCloudUser(user: User) {
        val tenantId = tenantProvider.requireTenantId()
        val docId = "${tenantId}_${user.email.trim().lowercase()}"
        firestore.collection("tenant_users")
            .document(docId)
            .set(
                mapOf(
                    "tenantId" to tenantId,
                    "name" to user.name,
                    "email" to user.email,
                    "role" to user.role,
                    "isActive" to user.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    private suspend fun deleteCloudUser(user: User) {
        val tenantId = tenantProvider.requireTenantId()
        val docId = "${tenantId}_${user.email.trim().lowercase()}"
        firestore.collection("tenant_users")
            .document(docId)
            .delete()
            .await()
    }
}
