package com.example.selliaapp.data.model.onboarding

enum class AccountRequestType(val raw: String, val label: String) {
    FINAL_CUSTOMER("final_customer", "Cliente final"),
    STORE_OWNER("store_owner", "Dueño tienda");

    companion object {
        fun fromRaw(value: String?): AccountRequestType =
            entries.firstOrNull { it.raw == value } ?: FINAL_CUSTOMER
    }
}

enum class AccountRequestStatus(val raw: String, val label: String) {
    PENDING("pending", "Pendiente"),
    ACTIVE("active", "Habilitado"),
    REJECTED("rejected", "Rechazado"),
    DISABLED("disabled", "Deshabilitado");

    companion object {
        fun fromRaw(value: String?): AccountRequestStatus =
            entries.firstOrNull { it.raw == value } ?: PENDING
    }
}

enum class BusinessModule(val key: String, val label: String) {
    CATALOG("catalog", "Catálogo"),
    SALES("sales", "Ventas"),
    STOCK("stock", "Stock"),
    REPORTS("reports", "Reportes"),
    CASH("cash", "Caja"),
    MARKETING("marketing", "Marketing")
}

data class AccountRequest(
    val id: String,
    val email: String,
    val accountType: AccountRequestType,
    val status: AccountRequestStatus,
    val tenantId: String?,
    val tenantName: String?,
    val storeName: String?,
    val storeAddress: String?,
    val storePhone: String?,
    val contactName: String?,
    val contactPhone: String?,
    val enabledModules: Map<String, Boolean>
) {
    fun isModuleEnabled(module: BusinessModule): Boolean =
        enabledModules[module.key] == true
}
