package com.example.selliaapp.data.remote

object TenantConfigContract {
    const val COLLECTION_TENANTS = "tenants"
    const val COLLECTION_CONFIG = "config"

    const val DOC_PRICING = "pricing"
    const val DOC_MARKETING = "marketing"
    const val DOC_SECURITY = "security"
    const val DOC_CLOUD_SERVICES = "cloud_services"
    const val DOC_DEVELOPMENT_OPTIONS = "development_options"

    const val CURRENT_SCHEMA_VERSION = 1

    object Fields {
        const val SCHEMA_VERSION = "schemaVersion"
        const val UPDATED_AT = "updatedAt"
        const val UPDATED_BY = "updatedBy"
        const val AUDIT = "audit"
        const val DATA = "data"
    }
}
