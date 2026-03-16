package com.example.selliaapp.data.remote

import com.google.firebase.Timestamp

data class StorefrontConfig(
    val storeName: String = "",
    val tagline: String = "",
    val logoUrl: String = "",
    val bannerImageUrl: String = "",
    val bannerTitle: String = "",
    val bannerSubtitle: String = "",
    val bannerCtaText: String = "",
    val contactWhatsapp: String = "",
    val contactInstagram: String = "",
    val contactAddress: String = "",
    val bannerMessages: List<BannerMessageLocal> = emptyList(),
    val storeType: StoreType = StoreType.PHYSICAL,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationAddress: String? = null,
    val locationCity: String? = null,
    val locationCountry: String? = null,
    val hasDelivery: Boolean = false,
    val updatedAt: Timestamp? = null
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "storeName" to storeName,
        "tagline" to tagline,
        "logoUrl" to logoUrl,
        "bannerImageUrl" to bannerImageUrl,
        "bannerTitle" to bannerTitle,
        "bannerSubtitle" to bannerSubtitle,
        "bannerCtaText" to bannerCtaText,
        "contact" to mapOf(
            "whatsapp" to contactWhatsapp,
            "instagram" to contactInstagram,
            "address" to contactAddress
        ),
        "bannerMessages" to bannerMessages.map { it.toMap() },
        "storeType" to storeType.raw,
        "location" to buildMap {
            if (locationLat != null && locationLng != null) {
                put("lat", locationLat)
                put("lng", locationLng)
                put("address", locationAddress ?: "")
            }
            if (!locationCity.isNullOrBlank()) put("city", locationCity)
            if (!locationCountry.isNullOrBlank()) put("country", locationCountry)
        }.ifEmpty { null },
        "hasDelivery" to hasDelivery
    )

    companion object {
        fun fromFirestore(data: Map<String, Any?>): StorefrontConfig {
            val contact = data["contact"] as? Map<*, *> ?: emptyMap<String, Any>()
            val loc = data["location"] as? Map<*, *>
            val messages = (data["bannerMessages"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { BannerMessageLocal.fromMap(it) }
            } ?: emptyList()
            return StorefrontConfig(
                storeName = (data["storeName"] as? String).orEmpty(),
                tagline = (data["tagline"] as? String).orEmpty(),
                logoUrl = (data["logoUrl"] as? String).orEmpty(),
                bannerImageUrl = (data["bannerImageUrl"] as? String).orEmpty(),
                bannerTitle = (data["bannerTitle"] as? String).orEmpty(),
                bannerSubtitle = (data["bannerSubtitle"] as? String).orEmpty(),
                bannerCtaText = (data["bannerCtaText"] as? String).orEmpty(),
                contactWhatsapp = (contact["whatsapp"] as? String).orEmpty(),
                contactInstagram = (contact["instagram"] as? String).orEmpty(),
                contactAddress = (contact["address"] as? String).orEmpty(),
                bannerMessages = messages,
                storeType = StoreType.fromRaw(data["storeType"] as? String),
                locationLat = (loc?.get("lat") as? Number)?.toDouble(),
                locationLng = (loc?.get("lng") as? Number)?.toDouble(),
                locationAddress = loc?.get("address") as? String,
                locationCity = loc?.get("city") as? String,
                locationCountry = loc?.get("country") as? String,
                hasDelivery = (data["hasDelivery"] as? Boolean) ?: false,
                updatedAt = data["updatedAt"] as? Timestamp
            )
        }
    }
}

data class BannerMessageLocal(
    val id: String = "",
    val text: String = "",
    val active: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "text" to text,
        "active" to active
    )

    companion object {
        fun fromMap(map: Map<*, *>): BannerMessageLocal = BannerMessageLocal(
            id = (map["id"] as? String).orEmpty(),
            text = (map["text"] as? String).orEmpty(),
            active = (map["active"] as? Boolean) ?: true
        )
    }
}

data class PublicBannerMessage(
    val id: String = "",
    val tenantId: String = "",
    val storeName: String = "",
    val text: String = "",
    val active: Boolean = true,
    val updatedAt: Timestamp? = null
)

data class StoreRequest(
    val id: String = "",
    val userId: String = "",
    val email: String = "",
    val storeName: String = "",
    val storeDescription: String = "",
    val storePhone: String = "",
    val status: StoreRequestStatus = StoreRequestStatus.PENDING,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "email" to email,
        "storeName" to storeName,
        "storeDescription" to storeDescription,
        "storePhone" to storePhone,
        "status" to status.raw
    )
}

enum class StoreRequestStatus(val raw: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        fun fromRaw(value: String?): StoreRequestStatus =
            entries.firstOrNull { it.raw == value?.lowercase() } ?: PENDING
    }
}

data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val actionRoute: String? = null,
    val createdAt: Timestamp? = null
)
