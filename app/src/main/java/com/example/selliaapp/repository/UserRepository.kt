// File: UserRepository.kt
package com.example.selliaapp.repository

import android.content.Context
import android.net.Uri
import com.example.selliaapp.data.csv.UserCsvImporter
import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.data.model.User
import com.example.selliaapp.data.model.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class UserRepository(private val userDao: UserDao) {

    fun observeUsers(): Flow<List<User>> = userDao.observeAll()
        .onStart { /* hook para loading si querés */ }

    suspend fun insert(user: User): Long = withContext(Dispatchers.IO) { userDao.insert(user) }

    suspend fun update(user: User): Int = withContext(Dispatchers.IO) { userDao.update(user) }

    suspend fun delete(user: User): Int = withContext(Dispatchers.IO) { userDao.delete(user) }

    suspend fun importUsersFromFile(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
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
                    userDao.insert(User(name = name, email = email, role = role))
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
}
