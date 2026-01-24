// File: Routes.kt
package com.example.selliaapp.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


sealed class Routes(val route: String) {
    object Home : Routes("home_root")
    object Sell : Routes("sell")
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
    object Checkout : Routes("checkout")
    object Reports : Routes("reports")

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
