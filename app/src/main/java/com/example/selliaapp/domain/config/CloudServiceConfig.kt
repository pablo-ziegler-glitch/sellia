package com.example.selliaapp.domain.config

data class CloudServiceConfig(
    val ownerEmail: String,
    val cloudEnabled: Boolean,
    val firestoreBackupEnabled: Boolean,
    val authSyncEnabled: Boolean,
    val storageBackupEnabled: Boolean,
    val functionsEnabled: Boolean,
    val hostingEnabled: Boolean
) {
    companion object {
        fun defaultFor(ownerEmail: String): CloudServiceConfig = CloudServiceConfig(
            ownerEmail = ownerEmail,
            cloudEnabled = false,
            firestoreBackupEnabled = false,
            authSyncEnabled = false,
            storageBackupEnabled = false,
            functionsEnabled = false,
            hostingEnabled = false
        )
    }
}
