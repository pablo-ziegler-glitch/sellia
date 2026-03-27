package com.example.selliaapp.repository


import com.example.selliaapp.data.dao.ProviderDao
import com.example.selliaapp.data.local.entity.ProviderEntity
import com.example.selliaapp.data.mappers.toEntity
import com.example.selliaapp.data.mappers.toModel
import com.example.selliaapp.data.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val dao: ProviderDao
) {
    /** Flujo de Proveedores en modelo de dominio. */
    fun observeAllModels(): Flow<List<Provider>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    /** Inserta/actualiza un Provider de dominio. Devuelve ID. */
    suspend fun upsert(model: Provider): Int =
        dao.upsert(model.toEntity())

    /** Borra un Provider de dominio. */
    suspend fun delete(model: Provider): Int =
        dao.delete(model.toEntity())

    /** Obtiene por id en modelo de dominio. */
    suspend fun getModelById(id: Int): Provider? =
        dao.getById(id)?.toModel()

    /**
     * Busca un proveedor por nombre exacto (trim) o lo crea si no existe.
     * Retorna el ID del proveedor encontrado o recién creado.
     */
    suspend fun findOrCreateByName(name: String): Int {
        val trimmed = name.trim()
        val existing = dao.getByName(trimmed)
        if (existing != null) return existing.id
        return dao.insert(ProviderEntity(name = trimmed)).toInt()
    }

    // ===== API previa (por si hay código que la usa) =====

    fun observeAll(): Flow<List<ProviderEntity>> = dao.observeAll()

    suspend fun upsert(entity: ProviderEntity): Int = dao.upsert(entity)

    suspend fun delete(entity: ProviderEntity): Int = dao.delete(entity)

    suspend fun getById(id: Int): ProviderEntity? = dao.getById(id)
}
