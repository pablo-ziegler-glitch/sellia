package com.example.selliaapp.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO de productos.
 * - Usa exclusivamente ProductEntity (persistencia).
 * - Expone flujos reactivos + operaciones puntuales.
 */
@Dao
interface ProductDao {

    // --------- Lecturas base ---------

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Int): ProductEntity?

    @Query("SELECT * FROM products WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<ProductEntity>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcodeOnce(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE name = :name LIMIT 1")
    suspend fun getByNameOnce(name: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?


    @Query("""
        SELECT * FROM products 
        WHERE (:term IS NULL OR :term = '')
           OR (name LIKE '%' || :term || '%' OR code LIKE '%' || :term || '%' OR barcode LIKE '%' || :term || '%') 
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun search(term: String?): Flow<List<ProductEntity>>

    /**
     * Devuelve los productos con stock igual o por debajo del mínimo configurado.
     * Ordena por la mayor falta de stock para priorizar reposiciones.
     */
    @Query(
        """
        SELECT id,
               name,
               quantity,
               COALESCE(minStock, 0) AS minStock
        FROM products
        WHERE minStock IS NOT NULL
          AND quantity <= minStock
        ORDER BY (COALESCE(minStock, 0) - quantity) DESC,
                 name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeLowStock(limit: Int): Flow<List<LowStockProduct>>

    // --------- Escrituras ---------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(p: ProductEntity): Long

    @Update
    suspend fun update(p: ProductEntity): Int

    /**
     * Upsert manual por ID:
     * - Si id != 0 → update; si no → insert.
     * Devuelve el ID final.
     */
    suspend fun upsert(p: ProductEntity): Int {
        return if (p.id != 0) {
            update(p)
            p.id
        } else {
            insert(p).toInt()
        }
    }

    /** Inserta/actualiza lista de productos (REPLACE). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    /** Borrado por entidad. */
    @Delete
    suspend fun delete(product: ProductEntity)

    /** Borrado por ID. Devuelve cantidad de filas borradas. */
    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    // --------- Ayuda para importación/merge ---------

    /**
     * Upsert por “claves” (barcode o nombre).
     * - Si encuentra existente por barcode, usa ese.
     * - Si no, intenta por nombre.
     * - Si no existe, inserta.
     * - Si existe, fusiona campos no nulos del entrante.
     */
    @Transaction
    suspend fun upsertByKeys(incoming: ProductEntity): Int {
        val existing: ProductEntity? = when {
            !incoming.barcode.isNullOrBlank() -> getByBarcodeOnce(incoming.barcode!!)
            !incoming.name.isNullOrBlank()    -> getByNameOnce(incoming.name)
            else                              -> null
        }
        return if (existing == null) {
            insert(incoming).toInt()
        } else {
            val merged = existing.copy(
                code        = incoming.code        ?: existing.code,
                barcode     = incoming.barcode     ?: existing.barcode,
                name        = if (incoming.name.isNotBlank()) incoming.name else existing.name,
                basePrice   = incoming.basePrice   ?: existing.basePrice,
                taxRate     = incoming.taxRate     ?: existing.taxRate,
                finalPrice  = incoming.finalPrice  ?: existing.finalPrice,
                price       = incoming.price       ?: existing.price,
                quantity    = if (incoming.quantity != 0) incoming.quantity else existing.quantity,
                description = incoming.description ?: existing.description,
                imageUrl    = incoming.imageUrl    ?: existing.imageUrl,
                categoryId  = incoming.categoryId  ?: existing.categoryId,
                providerId  = incoming.providerId  ?: existing.providerId,
                providerName= incoming.providerName?: existing.providerName,
                category    = incoming.category    ?: existing.category,
                minStock    = incoming.minStock    ?: existing.minStock,
                updatedAt   = incoming.updatedAt   // no forzamos si viene null; si querés: incoming.updatedAt ?: existing.updatedAt
            )
            update(merged)
            merged.id
        }
    }


    // ---------- Movimientos / stock atómicos ----------

    /**
     * Aplica un delta a quantity (positivo o negativo) sólo si existe el producto.
     * Devuelve cantidad de filas afectadas (0 si no existe).
     */
    @Query("""
        UPDATE products 
        SET quantity = quantity + :delta, updatedAt = :today 
        WHERE id = :productId
    """)
    suspend fun applyDelta(productId: Int, delta: Int, today: LocalDate): Int

    /**
     * Decrementa stock en qty *sólo* si hay stock suficiente (quantity >= qty).
     * Si no hay stock suficiente, devuelve 0 y no cambia nada (para rollback desde la TX).
     */
    @Query("""
        UPDATE products 
        SET quantity = quantity - :qty, updatedAt = :today 
        WHERE id = :productId AND quantity >= :qty
    """)
    suspend fun _decrementStockIfEnough(productId: Int, qty: Int, today: LocalDate): Int

    /**
     * Wrapper conveniente que pasa LocalDate.now() como 'today'.
     */
    @Transaction
    suspend fun decrementStockIfEnough(productId: Int, qty: Int): Int =
        _decrementStockIfEnough(productId, qty, LocalDate.now())

    /**
     * Incrementa stock (por ejemplo, al revertir una venta o registrar entrada).
     */
    @Query("""
        UPDATE products 
        SET quantity = quantity + :qty, updatedAt = :today 
        WHERE id = :productId
    """)
    suspend fun _increaseStock(productId: Int, qty: Int, today: LocalDate): Int

    @Transaction
    suspend fun increaseStockIfExists(productId: Int, qty: Int): Int =
        _increaseStock(productId, qty, LocalDate.now())

    // --------- Paging ---------

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun pagingAll(): PagingSource<Int, ProductEntity>

    @Query("""
        SELECT * FROM products 
        WHERE name LIKE '%' || :q || '%' OR 
              barcode LIKE '%' || :q || '%' OR 
              code LIKE '%' || :q || '%'
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun pagingSearch(q: String): PagingSource<Int, ProductEntity>

    @Query("""
        SELECT * FROM products 
        WHERE (:category IS NULL OR category = :category)
          AND (:provider IS NULL OR providerName = :provider)
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun pagingFiltered(category: String?, provider: String?): PagingSource<Int, ProductEntity>

    // --------- Listas para pickers ---------

    @Query("""
        SELECT DISTINCT category FROM products 
        WHERE category IS NOT NULL AND TRIM(category) <> '' 
        ORDER BY category COLLATE NOCASE ASC
    """)
    fun distinctCategories(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT providerName FROM products 
        WHERE providerName IS NOT NULL AND TRIM(providerName) <> '' 
        ORDER BY providerName COLLATE NOCASE ASC
    """)
    fun distinctProviders(): Flow<List<String>>



    // Si NO tenés CategoryDao/ProviderDao, podés derivar opciones para los pickers desde acá:
    @Query("SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND category <> ''")
    fun observeAllCategoryNamesFromProducts(): Flow<List<String>>

    @Query("SELECT DISTINCT providerName FROM products WHERE providerName IS NOT NULL AND providerName <> ''")
    fun observeAllProviderNamesFromProducts(): Flow<List<String>>




}