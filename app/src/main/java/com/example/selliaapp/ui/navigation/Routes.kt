// File: Routes.kt
package com.example.selliaapp.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


sealed class Routes(val route: String) {
    object Home : Routes("home_root")
    object Pos : Routes("sell")
    object Cash : Routes("cash")
    object More : Routes("more")
    object Sell : Routes("sell")
    object PosCheckout : Routes("pos_checkout")
    object PosPayment : Routes("pos_checkout")
    object PosSuccess : Routes("pos_success?invoiceId={invoiceId}&total={total}&method={method}") {
        const val ARG_ID = "invoiceId"
        const val ARG_TOTAL = "total"
        const val ARG_METHOD = "method"
        fun build(invoiceId: Long, total: Double, method: String): String =
            "pos_success?invoiceId=$invoiceId&total=$total&method=${encode(method)}"

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
    object Stock : Routes("stock")
    object Stock_import : Routes("stock_import")
    object QuickAdjustStock : Routes("stock/adjust/{productId}") {
        const val ARG_PRODUCT_ID = "productId"
        fun withProduct(productId: Int) = "stock/adjust/$productId"
        val arguments = listOf(navArgument(ARG_PRODUCT_ID) { type = NavType.IntType })
    }
    object QuickReorder : Routes("stock/reorder/{productId}") {
        const val ARG_PRODUCT_ID = "productId"
        fun withProduct(productId: Int) = "stock/reorder/$productId"
        val arguments = listOf(navArgument(ARG_PRODUCT_ID) { type = NavType.IntType })
    }
    object StockMovements : Routes("stock/movements")

    object Config : Routes("config")
    object PricingConfig : Routes("pricing_config")
    object MarketingConfig : Routes("marketing_config")
    object BulkData : Routes("bulk_data")
    object CloudServicesAdmin : Routes("cloud_services_admin")
    object DevelopmentOptions : Routes("development_options")
    object Checkout : Routes("checkout")
    object Reports : Routes("reports")
    object PriceSummary : Routes("price_summary")
    object Sales : Routes("sales")
    object SaleDetail : Routes("sale_detail/{invoiceId}") {
        const val ARG_ID = "invoiceId"
        fun withId(id: Long) = "sale_detail/$id"
    }
    object StockHistory : Routes("stock_history")
    object Customers : Routes("customers")
    object Providers : Routes("providers")
    object Expenses : Routes("expenses")
    object Settings : Routes("settings")
    object CashOpen : Routes("cash_open")
    object CashAudit : Routes("cash_audit")
    object CashClose : Routes("cash_close")
    object CashMovements : Routes("cash_movements")
    object CashReport : Routes("cash_report")
    object UsageAlerts : Routes("usage_alerts")
    object SecuritySettings : Routes("security_settings")

    object AddUser : Routes("add_user")
    object ManageProducts : Routes("manage_products")
    object ProductQr : Routes("product_qr")
    object ManageCustomers : Routes("manage_customers")
    object Sync : Routes("sync")

    // Hub de Clientes y subrutas nuevas
    object ClientsHub : Routes("clients_hub")
    object ClientPurchases : Routes("client_purchases")
    object ClientMetrics : Routes("client_metrics")

    object ScannerForSell : Routes("scanner_for_sell")
    object ScannerForStock : Routes("scanner_for_stock") // [NUEVO] unificado al mismo patrón
    object PublicProductScan : Routes("public_product_scan")
    object PublicProductCatalog : Routes("public_product_catalog")
    object PublicProductDetail : Routes("public_product_detail/{productId}") {
        const val ARG_PRODUCT_ID = "productId"
        fun withId(productId: Int) = "public_product_detail/$productId"
        val arguments = listOf(navArgument(ARG_PRODUCT_ID) { type = NavType.IntType })
    }
    object PublicProductCard : Routes("public_product_card") {
        const val ARG_QR = "qrValue"
        fun withQr(value: String) = "$route?$ARG_QR=${encode(value)}"
        val arguments = listOf(
            navArgument(ARG_QR) {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            }
        )

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    // ---------- NUEVO: Proveedores ----------
    object ProvidersHub : Routes("providers_hub")
    object ManageProviders : Routes("manage_providers")
    object ProviderInvoices : Routes("provider_invoices")              // listado por proveedor
    object ProviderInvoiceDetail : Routes("provider_invoice_detail?invoiceId={invoiceId}") {
        const val ARG_ID = "invoiceId"
        fun build(id: Int) = "provider_invoice_detail?invoiceId=$id"
    }
    object ProviderPayments : Routes("provider_payments")              // pendientes

    // ---------- NUEVO: Gastos ----------
    object ExpensesHub : Routes("expenses_hub")
    object ExpenseTemplates : Routes("expense_templates")
    object ExpenseEntries : Routes("expense_entries")
    object ExpensesCashflow : Routes("expenses_cashflow")



    // ---------- NUEVO: Facturas de venta a clientes ----------
    object SalesInvoices : Routes("sales_invoices") // [NUEVO]
    object SalesInvoiceDetail : Routes("sales_invoice_detail/{invoiceId}") { // [NUEVO]
        const val ARG_ID = "invoiceId"
        fun withId(id: Long) = "sales_invoice_detail/$id"
    }

    /**
     * [NUEVO] Rutas claras para el flujo de venta con nested graph:
     * - SELL_FLOW_ROUTE es el padre para compartir el MISMO SellViewModel.
     * - SELL_SCREEN_ROUTE y CHECKOUT_SCREEN_ROUTE son sus destinos hijos.
     */
    object SellRoutes {
        const val SELL_FLOW_ROUTE = "sell_flow"
        const val SELL_SCREEN_ROUTE = "sell"
        const val CHECKOUT_SCREEN_ROUTE = "checkout"
    }


    /**
     * Pantalla de Alta/Edición de Producto.
     *
     * - Alta nueva:  add_product[?barcode=...&name=...]
     * - Edición:     add_product/{id}
     */
    object AddProduct : Routes("add_product") {
        const val ARG_ID = "id"           // [NUEVO]
        const val ARG_BARCODE = "barcode" // [NUEVO]
        const val ARG_NAME = "name"       // [NUEVO]

        /** Ruta para patrón de EDICIÓN: add_product/{id} */
        val withIdPattern = "$route/{$ARG_ID}"  // add_product/{id}

        /** Construye la ruta concreta para EDICIÓN: add_product/123 */
        fun withId(id: Long): String = "$route/$id"

        /**
         * [NUEVO] Construye la ruta para ALTA con parámetros opcionales por query.
         * Ej.: add_product?barcode=779...&name=Leche
         */
        fun build(
            prefillBarcode: String? = null,
            prefillName: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (!prefillBarcode.isNullOrBlank()) {
                params += "$ARG_BARCODE=${encode(prefillBarcode)}"
            }
            if (!prefillName.isNullOrBlank()) {
                params += "$ARG_NAME=${encode(prefillName)}"
            }
            return if (params.isEmpty()) {
                route
            } else {
                "$route?${params.joinToString("&")}"
            }
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }


    /**
     * [NUEVO] Utilidad simple para escapar el barcode en querystring sin dependencias extra.
     */
    private object UriEncoder {
        fun encode(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
    }

    /**
     * [NUEVO] Argumentos Nav para registrar en el NavGraph:
     */
    object RouteArgs {
        // Para "add_product/{id}"
        val addProductIdArgs = listOf(
            navArgument(Routes.AddProduct.ARG_ID) { type = NavType.LongType }
        )

        // Para "add_product?barcode={barcode}" (opcional)
        val addProductBarcodeArgs = listOf(
            navArgument(Routes.AddProduct.ARG_BARCODE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
        val quickAdjustArgs = Routes.QuickAdjustStock.arguments
        val quickReorderArgs = Routes.QuickReorder.arguments
    }

}
