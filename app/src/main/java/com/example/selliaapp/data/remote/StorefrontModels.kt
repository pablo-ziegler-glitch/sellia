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
        "bannerMessages" to bannerMessages.map { it.toMap() }
    )

    companion object {
        fun fromFirestore(data: Map<String, Any?>): StorefrontConfig {
            val contact = data["contact"] as? Map<*, *> ?: emptyMap<String, Any>()
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
