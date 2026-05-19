package com.example.selliaapp.data.remote

import com.example.selliaapp.data.local.entity.ProductEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ProductFirestoreMappersTest {

    @Test
    fun toMap_includesStableIdentityAndSyncMetadata() {
        val product = ProductEntity(
            id = 7,
            productUuid = "92d41f7d-5ad4-4d88-ae16-c6939b2757ad",
            legacyLocalId = 7,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 2000L,
            syncVersion = 2L,
            syncStatus = "SYNCED",
            code = "SKU-1",
            barcode = "7791234567890",
            name = "Producto",
            quantity = 3,
            updatedAt = LocalDate.of(2026, 1, 5)
        )

        val map = ProductFirestoreMappers.toMap(product, tenantId = "t-1")

        assertThat(map["productUuid"]).isEqualTo(product.productUuid)
        assertThat(map["legacyLocalId"]).isEqualTo(7)
        assertThat(map["createdAtEpochMs"]).isEqualTo(1000L)
        assertThat(map["updatedAtEpochMs"]).isEqualTo(2000L)
        assertThat(map["syncVersion"]).isEqualTo(2L)
        assertThat(map["syncStatus"]).isEqualTo("SYNCED")
    }

    @Test
    fun fromMap_usesDocIdWhenUuidDocAndProductUuidMissing() {
        val uuidDocId = "8f59d875-1d02-4c64-8a87-8ec911bce611"
        val remote = ProductFirestoreMappers.fromMap(
            docId = uuidDocId,
            data = mapOf(
                "name" to "Coca",
                "quantity" to 2
            )
        )

        assertThat(remote.entity.productUuid).isEqualTo(uuidDocId)
        assertThat(remote.entity.legacyLocalId).isNull()
    }

    @Test
    fun fromMap_numericLegacyDoc_generatesStableUuidAndLegacyLocalId() {
        val remote = ProductFirestoreMappers.fromMap(
            docId = "123",
            data = mapOf(
                "name" to "Legacy",
                "quantity" to 1
            )
        )

        assertThat(remote.entity.productUuid).isNotEmpty()
        assertThat(remote.entity.legacyLocalId).isEqualTo(123)
        assertThat(remote.entity.id).isEqualTo(123)
    }

    @Test
    fun fromMap_nonNumericLegacyDoc_withoutProductUuid_isDeterministic() {
        val data = mapOf(
            "code" to "A-1",
            "barcode" to "7791234567000",
            "name" to "Test",
            "quantity" to 1
        )
        val first = ProductFirestoreMappers.fromMap("legacy-doc-alpha", data)
        val second = ProductFirestoreMappers.fromMap("legacy-doc-alpha", data)

        assertThat(first.entity.productUuid).isEqualTo(second.entity.productUuid)
        assertThat(first.entity.productUuid).isNotEmpty()
    }
}
