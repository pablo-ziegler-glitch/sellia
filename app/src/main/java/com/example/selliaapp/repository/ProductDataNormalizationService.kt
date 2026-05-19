package com.example.selliaapp.repository

import android.util.Log
import com.example.selliaapp.auth.TenantProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.UUID

class ProductDataNormalizationService(
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider
) {
    data class RunConfig(
        val tenantId: String,
        val runId: String = UUID.randomUUID().toString(),
        val simulateOnly: Boolean = true,
        val applyChanges: Boolean = false
    )

    data class ProductDoc(
        val docId: String,
        val productUuid: String,
        val legacyLocalId: Int?,
        val code: String?,
        val barcode: String?,
        val name: String,
        val brand: String?,
        val providerSku: String?,
        val providerName: String?,
        val category: String?,
        val quantity: Int,
        val imageUrls: List<String>,
        val updatedAtEpochMs: Long,
        val createdAtEpochMs: Long,
        val publicStatus: String?,
        val raw: Map<String, Any?>
    )

    data class Conflict(
        val groupKey: String,
        val reason: String,
        val productUuids: List<String>
    )

    data class Report(
        val runId: String,
        val simulateOnly: Boolean,
        val tenantId: String,
        val startedAtEpochMs: Long,
        val finishedAtEpochMs: Long,
        val productsScanned: Int,
        val candidateGroups: Int,
        val autoConsolidatedGroups: Int,
        val manualReviewGroups: Int,
        val archivedDocuments: Int,
        val canonicalDocumentsUpdated: Int,
        val tombstonesScanned: Int,
        val conflicts: List<Conflict>,
        val mapping: List<Map<String, Any?>>
    )

    suspend fun run(config: RunConfig): Report {
        val startedAt = System.currentTimeMillis()
        val tenantId = config.tenantId.ifBlank { tenantProvider.requireTenantId() }
        val products = readProducts(tenantId)
        val tombstones = readTombstones(tenantId)
        val groups = buildDuplicateGroups(products)

        var autoConsolidatedGroups = 0
        var manualReviewGroups = 0
        var archivedDocuments = 0
        var canonicalDocumentsUpdated = 0
        val conflicts = mutableListOf<Conflict>()
        val mapping = mutableListOf<Map<String, Any?>>()

        for ((groupKey, group) in groups) {
            if (group.size < 2) continue
            val canonical = selectCanonical(group)
            val nonCanonical = group.filter { it.productUuid != canonical.productUuid }
            val ambiguity = detectAmbiguity(groupKey, group)
            if (ambiguity != null) {
                conflicts += ambiguity
            }

            autoConsolidatedGroups += 1
            mapping += mapOf(
                "groupKey" to groupKey,
                "canonicalProductUuid" to canonical.productUuid,
                "mergedProductUuids" to nonCanonical.map { it.productUuid },
                "canonicalDocId" to canonical.docId
            )

            if (!config.simulateOnly && config.applyChanges) {
                val result = applyConsolidation(
                    tenantId = tenantId,
                    runId = config.runId,
                    canonical = canonical,
                    duplicates = nonCanonical
                )
                archivedDocuments += result.archived
                canonicalDocumentsUpdated += if (result.canonicalUpdated) 1 else 0
            }
        }

        val finishedAt = System.currentTimeMillis()
        val report = Report(
            runId = config.runId,
            simulateOnly = config.simulateOnly,
            tenantId = tenantId,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = finishedAt,
            productsScanned = products.size,
            candidateGroups = groups.size,
            autoConsolidatedGroups = autoConsolidatedGroups,
            manualReviewGroups = manualReviewGroups,
            archivedDocuments = archivedDocuments,
            canonicalDocumentsUpdated = canonicalDocumentsUpdated,
            tombstonesScanned = tombstones.size,
            conflicts = conflicts,
            mapping = mapping
        )

        persistReport(tenantId, report)
        return report
    }

    private suspend fun readProducts(tenantId: String): List<ProductDoc> {
        val collection = firestore.collection("tenants").document(tenantId).collection("products")
        val docs = fetchPaged(collection.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING))
        return docs.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val raw = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            val explicitUuid = (raw["productUuid"] as? String)?.trim().orEmpty()
            val productUuid = when {
                explicitUuid.isNotBlank() -> explicitUuid
                doc.id.isNotBlank() -> doc.id
                else -> UUID.randomUUID().toString()
            }
            ProductDoc(
                docId = doc.id,
                productUuid = productUuid,
                legacyLocalId = (raw["legacyLocalId"] as? Number)?.toInt() ?: doc.id.toIntOrNull(),
                code = raw["code"] as? String,
                barcode = raw["barcode"] as? String,
                name = (raw["name"] as? String).orEmpty(),
                brand = raw["brand"] as? String,
                providerSku = raw["providerSku"] as? String,
                providerName = raw["providerName"] as? String,
                category = raw["category"] as? String,
                quantity = (raw["quantity"] as? Number)?.toInt() ?: 0,
                imageUrls = (raw["imageUrls"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                updatedAtEpochMs = (raw["updatedAtEpochMs"] as? Number)?.toLong() ?: 0L,
                createdAtEpochMs = (raw["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                publicStatus = raw["publicStatus"] as? String,
                raw = raw
            )
        }
    }

    private suspend fun readTombstones(tenantId: String): List<Map<String, Any?>> {
        val collection = firestore.collection("tenants").document(tenantId).collection("product_deletions")
        val docs = fetchPaged(collection.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING))
        return docs.mapNotNull { it.data as? Map<String, Any?> }
    }

    private suspend fun fetchPaged(baseQuery: Query): List<DocumentSnapshot> {
        val docs = mutableListOf<DocumentSnapshot>()
        var lastDoc: DocumentSnapshot? = null
        do {
            val query = if (lastDoc == null) baseQuery.limit(PAGE_SIZE) else baseQuery.startAfter(lastDoc).limit(PAGE_SIZE)
            val page = query.get().await()
            docs += page.documents
            lastDoc = page.documents.lastOrNull()
        } while (lastDoc != null)
        return docs
    }

    private fun buildDuplicateGroups(products: List<ProductDoc>): Map<String, List<ProductDoc>> {
        val groups = linkedMapOf<String, MutableSet<ProductDoc>>()
        fun bind(key: String?, product: ProductDoc) {
            if (key.isNullOrBlank()) return
            groups.getOrPut(key) { linkedSetOf() }.add(product)
        }

        products.forEach { product ->
            bind("uuid:${product.productUuid}", product)
            bind(product.legacyLocalId?.let { "legacyId:$it" }, product)
            bind(normalizeCode(product.code)?.let { "code:$it" }, product)
            bind(normalizeBarcode(product.barcode)?.let { "barcode:$it" }, product)
            bind(
                buildProviderKey(product.providerSku, product.providerName)
                    ?.let { "provider:$it" },
                product
            )
            bind(
                buildNameBrandCategoryKey(product.name, product.brand, product.category)
                    ?.let { "nameBrandCat:$it" },
                product
            )
        }
        return groups
            .filterValues { it.size > 1 }
            .mapValues { (_, values) -> values.toList() }
    }

    private fun selectCanonical(group: List<ProductDoc>): ProductDoc {
        val byUpdated = group.maxByOrNull { it.updatedAtEpochMs }
        if (byUpdated != null && byUpdated.updatedAtEpochMs > 0L) return byUpdated
        return group.maxByOrNull { candidate ->
            var score = 0
            if (candidate.productUuid.isNotBlank()) score += 40
            if (candidate.updatedAtEpochMs > 0) score += 30
            if (candidate.imageUrls.isNotEmpty()) score += 10
            if (!candidate.code.isNullOrBlank()) score += 8
            if (!candidate.barcode.isNullOrBlank()) score += 8
            if (candidate.quantity > 0) score += 6
            if (candidate.publicStatus.equals("published", ignoreCase = true)) score += 6
            score
        } ?: group.first()
    }

    private fun detectAmbiguity(groupKey: String, group: List<ProductDoc>): Conflict? {
        val distinctBarcodes = group.mapNotNull { normalizeBarcode(it.barcode) }.toSet()
        if (distinctBarcodes.size > 1) {
            return Conflict(groupKey, "different_non_empty_barcodes", group.map { it.productUuid })
        }
        val distinctCodes = group.mapNotNull { normalizeCode(it.code) }.toSet()
        if (distinctCodes.size > 1) {
            return Conflict(groupKey, "different_non_empty_codes", group.map { it.productUuid })
        }
        val positiveStocks = group.count { it.quantity > 0 }
        if (positiveStocks > 1) {
            return Conflict(groupKey, "multiple_positive_stock_documents", group.map { it.productUuid })
        }
        return null
    }

    private data class ApplyResult(
        val canonicalUpdated: Boolean,
        val archived: Int
    )

    private suspend fun applyConsolidation(
        tenantId: String,
        runId: String,
        canonical: ProductDoc,
        duplicates: List<ProductDoc>
    ): ApplyResult {
        if (duplicates.isEmpty()) return ApplyResult(canonicalUpdated = false, archived = 0)
        val productsCollection = firestore.collection("tenants").document(tenantId).collection("products")
        val now = System.currentTimeMillis()
        val batch = firestore.batch()

        (listOf(canonical) + duplicates).forEach { product ->
            batch.set(
                firestore.collection("tenants")
                    .document(tenantId)
                    .collection("product_state_history")
                    .document("${runId}_${product.productUuid}_$now"),
                mapOf(
                    "runId" to runId,
                    "productUuid" to product.productUuid,
                    "legacyLocalId" to product.legacyLocalId,
                    "sourceDocId" to product.docId,
                    "snapshot" to product.raw,
                    "recordedAtEpochMs" to now
                ),
                SetOptions.merge()
            )
        }

        val legacyLocalIds = (listOfNotNull(canonical.legacyLocalId) + duplicates.mapNotNull { it.legacyLocalId }).distinct()
        val legacyCodes = (listOfNotNull(canonical.code) + duplicates.mapNotNull { it.code })
            .mapNotNull(::normalizeCode)
            .distinct()
        val legacyBarcodes = (listOfNotNull(canonical.barcode) + duplicates.mapNotNull { it.barcode })
            .mapNotNull(::normalizeBarcode)
            .distinct()
        val mergedDocIds = (listOf(canonical.docId) + duplicates.map { it.docId }).distinct()
        val mergedUuids = duplicates.map { it.productUuid }.distinct()
        val mergedImages = (canonical.imageUrls + duplicates.flatMap { it.imageUrls }).distinct()

        batch.set(
            productsCollection.document(canonical.productUuid),
            mapOf(
                "productUuid" to canonical.productUuid,
                "legacyLocalIds" to legacyLocalIds,
                "legacyCodes" to legacyCodes,
                "legacyBarcodes" to legacyBarcodes,
                "legacyRemoteDocIds" to mergedDocIds,
                "mergedFromProductUuids" to mergedUuids,
                "normalizationRunIds" to FieldValue.arrayUnion(runId),
                "imageUrls" to mergedImages,
                "updatedAtEpochMs" to now,
                "syncStatus" to "SYNCED"
            ),
            SetOptions.merge()
        )

        duplicates.forEach { duplicate ->
            batch.set(
                productsCollection.document(duplicate.productUuid),
                mapOf(
                    "mergeStatus" to "merged",
                    "mergedIntoProductUuid" to canonical.productUuid,
                    "archivedAtEpochMs" to now,
                    "visible" to false,
                    "syncStatus" to "MERGED",
                    "normalizationRunId" to runId,
                    "updatedAtEpochMs" to now
                ),
                SetOptions.merge()
            )
        }
        batch.commit().await()

        duplicates.forEach { duplicate ->
            if (duplicate.docId != duplicate.productUuid) {
                runCatching {
                    productsCollection.document(duplicate.docId).set(
                        mapOf(
                            "mergeStatus" to "merged",
                            "mergedIntoProductUuid" to canonical.productUuid,
                            "archivedAtEpochMs" to now,
                            "visible" to false,
                            "syncStatus" to "MERGED",
                            "normalizationRunId" to runId,
                            "updatedAtEpochMs" to now
                        ),
                        SetOptions.merge()
                    ).await()
                }.onFailure { error ->
                    Log.w(TAG, "No se pudo archivar doc legacy ${duplicate.docId}", error)
                }
            }
        }
        return ApplyResult(canonicalUpdated = true, archived = duplicates.size)
    }

    private suspend fun persistReport(tenantId: String, report: Report) {
        val reportMap = mapOf(
            "runId" to report.runId,
            "simulateOnly" to report.simulateOnly,
            "tenantId" to report.tenantId,
            "startedAtEpochMs" to report.startedAtEpochMs,
            "finishedAtEpochMs" to report.finishedAtEpochMs,
            "productsScanned" to report.productsScanned,
            "candidateGroups" to report.candidateGroups,
            "autoConsolidatedGroups" to report.autoConsolidatedGroups,
            "manualReviewGroups" to report.manualReviewGroups,
            "archivedDocuments" to report.archivedDocuments,
            "canonicalDocumentsUpdated" to report.canonicalDocumentsUpdated,
            "tombstonesScanned" to report.tombstonesScanned,
            "conflicts" to report.conflicts.map { conflict ->
                mapOf(
                    "groupKey" to conflict.groupKey,
                    "reason" to conflict.reason,
                    "productUuids" to conflict.productUuids
                )
            },
            "mapping" to report.mapping
        )
        val tenantDoc = firestore.collection("tenants").document(tenantId)
        tenantDoc
            .collection("migration_reports")
            .document(report.runId)
            .set(reportMap, SetOptions.merge())
            .await()

        val batch = firestore.batch()
        report.mapping.forEachIndexed { index, mappingEntry ->
            batch.set(
                tenantDoc.collection("product_merge_audit").document("${report.runId}_$index"),
                mapOf(
                    "runId" to report.runId,
                    "tenantId" to tenantId,
                    "mapping" to mappingEntry,
                    "createdAtEpochMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }
        if (report.mapping.isNotEmpty()) {
            batch.commit().await()
        }
    }

    private fun normalizeCode(code: String?): String? =
        code?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    private fun normalizeBarcode(barcode: String?): String? =
        barcode?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeText(raw: String?): String? {
        val base = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val noAccents = Normalizer.normalize(base, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
        return noAccents.lowercase().replace("\\s+".toRegex(), " ").trim()
    }

    private fun buildProviderKey(providerSku: String?, providerName: String?): String? {
        val sku = normalizeText(providerSku) ?: return null
        val provider = normalizeText(providerName) ?: return null
        return "$sku|$provider"
    }

    private fun buildNameBrandCategoryKey(name: String?, brand: String?, category: String?): String? {
        val normalizedName = normalizeText(name) ?: return null
        val normalizedBrand = normalizeText(brand)
        val normalizedCategory = normalizeText(category)
        return listOf(normalizedName, normalizedBrand, normalizedCategory).joinToString("|")
    }

    companion object {
        private const val PAGE_SIZE = 500L
        private const val TAG = "ProductNormalization"
    }
}
