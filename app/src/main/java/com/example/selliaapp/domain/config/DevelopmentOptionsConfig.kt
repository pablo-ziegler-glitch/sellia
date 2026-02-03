package com.example.selliaapp.domain.config

enum class DevelopmentFeatureKey(val label: String) {
    SALES("Ventas"),
    STOCK("Stock"),
    CUSTOMERS("Clientes"),
    PROVIDERS("Proveedores"),
    EXPENSES("Gastos"),
    REPORTS("Reportes"),
    CASH("Caja"),
    USAGE_ALERTS("Alertas de uso"),
    CONFIG("Configuración"),
    PUBLIC_CATALOG("Catálogo público")
}

data class DevelopmentOptionsConfig(
    val ownerEmail: String,
    val salesEnabled: Boolean,
    val stockEnabled: Boolean,
    val customersEnabled: Boolean,
    val providersEnabled: Boolean,
    val expensesEnabled: Boolean,
    val reportsEnabled: Boolean,
    val cashEnabled: Boolean,
    val usageAlertsEnabled: Boolean,
    val configEnabled: Boolean,
    val publicCatalogEnabled: Boolean
) {
    fun isEnabled(feature: DevelopmentFeatureKey): Boolean = when (feature) {
        DevelopmentFeatureKey.SALES -> salesEnabled
        DevelopmentFeatureKey.STOCK -> stockEnabled
        DevelopmentFeatureKey.CUSTOMERS -> customersEnabled
        DevelopmentFeatureKey.PROVIDERS -> providersEnabled
        DevelopmentFeatureKey.EXPENSES -> expensesEnabled
        DevelopmentFeatureKey.REPORTS -> reportsEnabled
        DevelopmentFeatureKey.CASH -> cashEnabled
        DevelopmentFeatureKey.USAGE_ALERTS -> usageAlertsEnabled
        DevelopmentFeatureKey.CONFIG -> configEnabled
        DevelopmentFeatureKey.PUBLIC_CATALOG -> publicCatalogEnabled
    }

    fun withFeature(feature: DevelopmentFeatureKey, enabled: Boolean): DevelopmentOptionsConfig = when (feature) {
        DevelopmentFeatureKey.SALES -> copy(salesEnabled = enabled)
        DevelopmentFeatureKey.STOCK -> copy(stockEnabled = enabled)
        DevelopmentFeatureKey.CUSTOMERS -> copy(customersEnabled = enabled)
        DevelopmentFeatureKey.PROVIDERS -> copy(providersEnabled = enabled)
        DevelopmentFeatureKey.EXPENSES -> copy(expensesEnabled = enabled)
        DevelopmentFeatureKey.REPORTS -> copy(reportsEnabled = enabled)
        DevelopmentFeatureKey.CASH -> copy(cashEnabled = enabled)
        DevelopmentFeatureKey.USAGE_ALERTS -> copy(usageAlertsEnabled = enabled)
        DevelopmentFeatureKey.CONFIG -> copy(configEnabled = enabled)
        DevelopmentFeatureKey.PUBLIC_CATALOG -> copy(publicCatalogEnabled = enabled)
    }

    companion object {
        fun defaultFor(ownerEmail: String): DevelopmentOptionsConfig = DevelopmentOptionsConfig(
            ownerEmail = ownerEmail,
            salesEnabled = true,
            stockEnabled = true,
            customersEnabled = true,
            providersEnabled = true,
            expensesEnabled = true,
            reportsEnabled = true,
            cashEnabled = true,
            usageAlertsEnabled = true,
            configEnabled = true,
            publicCatalogEnabled = true
        )
    }
}
