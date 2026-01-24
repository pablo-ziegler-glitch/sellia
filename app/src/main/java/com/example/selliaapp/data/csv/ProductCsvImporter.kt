package com.example.selliaapp.data.csv

import android.content.ContentResolver
import android.net.Uri
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ProductImageDao
import com.example.selliaapp.data.local.entity.ProductImageEntity
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ImportResult
import java.io.InputStream
import java.time.LocalDate

/**
 * Importador de productos desde CSV con soporte de:
 * - lectura robusta de filas/encabezados vía CsvUtils (comillas, escapes, etc.)
 * - alias de encabezados
 * - mapeo a ProductEntity (mínimo viable; extendible)
 *
 * Puntos clave para compatibilidad:
 *  - Exponemos `data class Row` como tipo anidado público => se puede usar `ProductCsvImporter.Row`.
 *  - Exponemos `parseCsv(input)` en un companion object (API estática).
 */
class ProductCsvImporter(
    private val productDao: ProductDao,
    private val productImageDao: ProductImageDao? = null
) {

    /**
     * Fila parseada del CSV. Se deja pública y anidada para que el repo pueda usar
     * la referencia `ProductCsvImporter.Row`.
     */
    data class Row(
        val code: String?,
        val barcode: String?,
        val name: String,
        val quantity: Int,
        val price: Double?,
        val purchasePrice: Double?,
        val listPrice: Double?,
        val cashPrice: Double?,
        val transferPrice: Double?,
        val transferNetPrice: Double?,
        val mlPrice: Double?,
        val ml3cPrice: Double?,
        val ml6cPrice: Double?,
        val description: String?,
        val imageUrls: List<String>,
        val category: String?,
        val providerName: String?,
        val providerSku: String?,
        val minStock: Int?,
        val updatedAt: LocalDate?
    )

    /**
     * Importa insertando siempre (Append): si hay barcode y existe, lo trata como nueva inserción
     * (si querés upsert, usá importUpsertByBarcode).
     */
    suspend fun importAppend(resolver: ContentResolver, uri: Uri): ImportResult {
        val rows = openAndParse(resolver, uri) ?: return ImportResult(
            inserted = 0,
            updated = 0,
            errors = listOf("No se pudo abrir el archivo")
        )

        var inserted = 0
        val errors = mutableListOf<String>()

        rows.forEachIndexed { idx, r ->
            try {
                val entity = ProductEntity(
                    id = 0,
                    code = r.code,
                    barcode = r.barcode,
                    name = r.name,
                    purchasePrice = r.purchasePrice,
                    price = r.price,
                    listPrice = r.listPrice,
                    cashPrice = r.cashPrice,
                    transferPrice = r.transferPrice,
                    transferNetPrice = r.transferNetPrice,
                    mlPrice = r.mlPrice,
                    ml3cPrice = r.ml3cPrice,
                    ml6cPrice = r.ml6cPrice,
                    autoPricing = false,
                    quantity = r.quantity,
                    description = r.description,
                    imageUrls = r.imageUrls,
                    category = r.category,
                    providerName = r.providerName,
                    providerSku = r.providerSku,
                    minStock = r.minStock,
                    updatedAt = r.updatedAt ?: LocalDate.now()
                )
                val id = productDao.upsert(entity)
                replaceImagesIfNeeded(id, r.imageUrls)
                inserted++
            } catch (t: Throwable) {
                errors += "L${idx + 2}: ${t.message ?: t::class.java.simpleName}"
            }
        }

        return ImportResult(inserted = inserted, updated = 0, errors = errors)
    }

    /**
     * Importa con upsert por código de barras:
     * - Si encuentra un producto con mismo barcode, hace merge y update.
     * - Si no existe, inserta.
     */
    suspend fun importUpsertByBarcode(resolver: ContentResolver, uri: Uri): ImportResult {
        val rows = openAndParse(resolver, uri) ?: return ImportResult(
            inserted = 0,
            updated = 0,
            errors = listOf("No se pudo abrir el archivo")
        )

        var inserted = 0
        var updated = 0
        val errors = mutableListOf<String>()

        rows.forEachIndexed { idx, r ->
            try {
                if (!r.barcode.isNullOrBlank()) {
                    val existing = productDao.getByBarcode(r.barcode!!)
                    if (existing == null) {
                        // Inserta
                val entity = ProductEntity(
                    id = 0,
                    code = r.code,
                    barcode = r.barcode,
                    name = r.name,
                    purchasePrice = r.purchasePrice,
                    price = r.price,
                            listPrice = r.listPrice,
                            cashPrice = r.cashPrice,
                            transferPrice = r.transferPrice,
                            transferNetPrice = r.transferNetPrice,
                            mlPrice = r.mlPrice,
                            ml3cPrice = r.ml3cPrice,
                            ml6cPrice = r.ml6cPrice,
                            autoPricing = false,
                            quantity = r.quantity,
                            description = r.description,
                            imageUrls = r.imageUrls,
                            category = r.category,
                            providerName = r.providerName,
                            providerSku = r.providerSku,
                            minStock = r.minStock,
                            updatedAt = r.updatedAt ?: LocalDate.now()
                        )
                        val id = productDao.upsert(entity)
                        replaceImagesIfNeeded(id, r.imageUrls)
                        inserted++
                    } else {
                        // Merge simple
                        val merged = existing.copy(
                            name        = r.name.ifBlank { existing.name },
                            price       = r.price ?: existing.price,
                            listPrice   = r.listPrice ?: existing.listPrice,
                            cashPrice   = r.cashPrice ?: existing.cashPrice,
                            transferPrice = r.transferPrice ?: existing.transferPrice,
                            transferNetPrice = r.transferNetPrice ?: existing.transferNetPrice,
                            purchasePrice = r.purchasePrice ?: existing.purchasePrice,
                            mlPrice     = r.mlPrice ?: existing.mlPrice,
                            ml3cPrice   = r.ml3cPrice ?: existing.ml3cPrice,
                            ml6cPrice   = r.ml6cPrice ?: existing.ml6cPrice,
                            autoPricing = existing.autoPricing,
                            quantity    = (existing.quantity) + (r.quantity),
                            description = r.description ?: existing.description,
                            category    = r.category ?: existing.category,
                            providerName = r.providerName ?: existing.providerName,
                            providerSku  = r.providerSku ?: existing.providerSku,
                            minStock    = r.minStock ?: existing.minStock,
                            updatedAt   = r.updatedAt ?: existing.updatedAt
                        )
                        productDao.upsert(merged)
                        replaceImagesIfNeeded(existing.id, r.imageUrls)
                        updated++
                    }
                } else {
                    // Sin barcode → insertar directo
                    val entity = ProductEntity(
                        id = 0,
                        code = r.code,
                        barcode = r.barcode,
                        name = r.name,
                        purchasePrice = r.purchasePrice,
                        price = r.price,
                        listPrice = r.listPrice,
                        cashPrice = r.cashPrice,
                        transferPrice = r.transferPrice,
                        transferNetPrice = r.transferNetPrice,
                        mlPrice = r.mlPrice,
                        ml3cPrice = r.ml3cPrice,
                        ml6cPrice = r.ml6cPrice,
                        autoPricing = false,
                        quantity = r.quantity,
                        description = r.description,
                        imageUrls = r.imageUrls,
                        category = r.category,
                        providerName = r.providerName,
                        providerSku = r.providerSku,
                        minStock = r.minStock,
                        updatedAt = r.updatedAt ?: LocalDate.now()
                    )
                    val id = productDao.upsert(entity)
                    replaceImagesIfNeeded(id, r.imageUrls)
                    inserted++
                }
            } catch (t: Throwable) {
                errors += "L${idx + 2}: ${t.message ?: t::class.java.simpleName}"
            }
        }

        return ImportResult(inserted = inserted, updated = updated, errors = errors)
    }

    // -------------------- Helpers internos --------------------

    private fun openAndParse(resolver: ContentResolver, uri: Uri): List<Row>? = try {
        parseFile(resolver, uri)
    } catch (notFound: java.io.FileNotFoundException) {
        null
    } catch (illegal: IllegalArgumentException) {
        throw illegal
    } catch (state: IllegalStateException) {
        throw IllegalArgumentException(state.message ?: "Error al procesar el archivo")
    } catch (t: Exception) {
        throw IllegalArgumentException(t.message ?: "Error al procesar el archivo", t)
    }

    companion object {
        /**
         * Parser robusto que usa CsvUtils para leer todo el CSV.
         * Devuelve una lista de Row (normalizada y saneada).
         */
        fun parseCsv(input: InputStream): List<Row> = parseTable(CsvUtils.readAll(input))

        fun parseFile(resolver: ContentResolver, uri: Uri): List<Row> =
            parseTable(TabularFileReader.readAll(resolver, uri))

        fun parseTable(table: List<List<String>>): List<Row> {
            require(table.isNotEmpty()) { "Archivo vacío" }

            val header = table.first()
            val idx = CsvUtils.HeaderIndex(header)

            val rows = mutableListOf<Row>()

            table.drop(1).forEachIndexed { lineIdx, row ->
                if (row.isEmpty() || row.all { it.isBlank() }) return@forEachIndexed

                val name = idx.get(row, "name", aliases = listOf("nombre", "product", "producto"))
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("name vacío (línea ${lineIdx + 2})")

                val code = idx.get(row, "code", aliases = listOf("codigo_interno", "sku"))?.ifBlank { null }
                val barcode = idx.get(row, "barcode", aliases = listOf("codigo", "código", "ean", "upc", "sku"))?.ifBlank { null }

                val price = idx.get(row, "price", aliases = listOf("precio", "amount"))
                    ?.replace(',', '.')?.toDoubleOrNull()
                val purchasePrice = idx.get(
                    row,
                    "purchase_price",
                    aliases = listOf("precio_adquisicion", "cost_price", "purchase")
                )?.replace(',', '.')?.toDoubleOrNull()
                val listPrice = idx.get(row, "list_price", aliases = listOf("precio_lista", "price_list"))
                    ?.replace(',', '.')?.toDoubleOrNull()
                val cashPrice = idx.get(row, "cash_price", aliases = listOf("precio_efectivo", "price_cash"))
                    ?.replace(',', '.')?.toDoubleOrNull()
                val transferPrice = idx.get(
                    row,
                    "transfer_price",
                    aliases = listOf("precio_transferencia", "price_transfer")
                )?.replace(',', '.')?.toDoubleOrNull()
                val transferNetPrice = idx.get(
                    row,
                    "transfer_net_price",
                    aliases = listOf("precio_transferencia_neto", "transfer_net")
                )?.replace(',', '.')?.toDoubleOrNull()
                val mlPrice = idx.get(row, "ml_price", aliases = listOf("precio_ml", "price_ml"))
                    ?.replace(',', '.')?.toDoubleOrNull()
                val ml3cPrice = idx.get(
                    row,
                    "ml_3c_price",
                    aliases = listOf("precio_ml_3c", "price_ml_3c")
                )?.replace(',', '.')?.toDoubleOrNull()
                val ml6cPrice = idx.get(
                    row,
                    "ml_6c_price",
                    aliases = listOf("precio_ml_6c", "price_ml_6c")
                )?.replace(',', '.')?.toDoubleOrNull()

                val quantity = idx.get(row, "quantity", aliases = listOf("qty", "stock", "cantidad"))
                    ?.toIntOrNull() ?: 0

                val description = idx.get(row, "description", aliases = listOf("descripcion", "desc"))?.ifBlank { null }
                val imageUrl = idx.get(row, "imageUrl", aliases = listOf("imagen", "url"))?.ifBlank { null }
                val rawImageUrls = idx.get(
                    row,
                    "image_urls",
                    aliases = listOf("imagenes", "urls", "imagenes_urls", "imageUrls")
                )
                val imageUrls = rawImageUrls
                    ?.split("|")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?: emptyList()
                val combinedImages = (listOfNotNull(imageUrl) + imageUrls).distinct()
                val category = idx.get(row, "category", aliases = listOf("categoria"))?.ifBlank { null }
                val providerName = idx.get(row, "provider", aliases = listOf("proveedor", "provider_name", "supplier"))
                    ?.ifBlank { null }
                val providerSku = idx.get(
                    row,
                    "provider_sku",
                    aliases = listOf("sku_proveedor", "skuProveedor", "supplier_sku")
                )?.ifBlank { null }
                val minStock = idx.get(row, "min_stock", aliases = listOf("minimo", "minstock", "stockmin"))
                    ?.toIntOrNull()?.let { if (it < 0) 0 else it }

                val updatedAt = idx.get(row, "updated_at", aliases = listOf("actualizado", "fecha"))
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                rows += Row(
                    code = code,
                    barcode = barcode,
                    name = name,
                    quantity = if (quantity < 0) 0 else quantity,
                    price = price,
                    purchasePrice = purchasePrice,
                    listPrice = listPrice,
                    cashPrice = cashPrice,
                    transferPrice = transferPrice,
                    transferNetPrice = transferNetPrice,
                    mlPrice = mlPrice,
                    ml3cPrice = ml3cPrice,
                    ml6cPrice = ml6cPrice,
                    description = description,
                    imageUrls = combinedImages,
                    category = category,
                    providerName = providerName,
                    providerSku = providerSku,
                    minStock = minStock,
                    updatedAt = updatedAt
                )
            }
            return rows
        }
    }

    private suspend fun replaceImagesIfNeeded(productId: Int, urls: List<String>) {
        if (urls.isEmpty()) return
        val dao = productImageDao ?: return
        val normalized = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        dao.deleteByProductId(productId)
        val entities = normalized.mapIndexed { index, url ->
            ProductImageEntity(
                productId = productId,
                url = url,
                position = index
            )
        }
        dao.insertAll(entities)
    }
}
