package com.example.selliaapp.ui.navigation


import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.ui.screens.HomeScreen
import com.example.selliaapp.ui.screens.barcode.BarcodeScannerScreen
import com.example.selliaapp.ui.screens.checkout.CheckoutScreen
import com.example.selliaapp.ui.screens.clients.ClientMetricsScreen
import com.example.selliaapp.ui.screens.clients.ClientPurchasesScreen
import com.example.selliaapp.ui.screens.clients.ClientsHubScreen
import com.example.selliaapp.ui.screens.config.AddUserScreen
import com.example.selliaapp.ui.screens.config.ConfigScreen
import com.example.selliaapp.ui.screens.expenses.ExpenseEntriesScreen
import com.example.selliaapp.ui.screens.expenses.ExpenseTemplatesScreen
import com.example.selliaapp.ui.screens.expenses.ExpensesHubScreen
import com.example.selliaapp.ui.screens.manage.ManageCustomersScreen
import com.example.selliaapp.ui.screens.manage.ManageProductsScreen
import com.example.selliaapp.ui.screens.manage.SyncScreen
import com.example.selliaapp.ui.screens.providers.ManageProvidersScreen
import com.example.selliaapp.ui.screens.providers.ProviderInvoiceDetailScreen
import com.example.selliaapp.ui.screens.providers.ProviderInvoicesScreen
import com.example.selliaapp.ui.screens.providers.ProviderPaymentsScreen
import com.example.selliaapp.ui.screens.providers.ProvidersHubScreen
import com.example.selliaapp.ui.screens.reports.ReportsScreen
import com.example.selliaapp.ui.screens.sales.SalesInvoiceDetailScreen
import com.example.selliaapp.ui.screens.sales.SalesInvoicesScreen
import com.example.selliaapp.ui.screens.sell.AddProductScreen
import com.example.selliaapp.ui.screens.sell.SellScreen
import com.example.selliaapp.ui.screens.stock.StockImportScreen
import com.example.selliaapp.ui.screens.stock.StockScreen
import com.example.selliaapp.viewmodel.ClientMetricsViewModel
import com.example.selliaapp.viewmodel.ClientPurchasesViewModel
import com.example.selliaapp.viewmodel.HomeViewModel
import com.example.selliaapp.viewmodel.ManageProductsViewModel
import com.example.selliaapp.viewmodel.ProductViewModel
import com.example.selliaapp.viewmodel.ReportsViewModel
import com.example.selliaapp.viewmodel.SellViewModel
import com.example.selliaapp.viewmodel.StockImportViewModel
import com.example.selliaapp.viewmodel.UserViewModel
import com.example.selliaapp.viewmodel.sales.SalesInvoiceDetailViewModel
import com.example.selliaapp.viewmodel.sales.SalesInvoicesViewModel


 private const val SELL_FLOW_ROUTE = "sell_flow"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelliaApp(
    navController: NavHostController = rememberNavController(),
     customerRepo: CustomerRepository

) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // ViewModels inyectados por Hilt (scope de navegación)
    val userViewModel: UserViewModel = hiltViewModel()


    // CAMBIO: Envolvemos el NavHost en un Scaffold con SnackbarHost (Opción A)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        // CAMBIO: Usamos el navController recibido por parámetro (no crear otro).
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.padding(paddingValues)

        ) {
            // -------------------- HOME (rediseñada) --------------------
            composable(Routes.Home.route) {
                val homeVm: HomeViewModel = hiltViewModel()

                HomeScreen(
                    onNewSale = { navController.navigate(Routes.Sell.route) },
                    onStock = { navController.navigate(Routes.Stock.route) },
                    onClientes = { navController.navigate(Routes.ClientsHub.route) },
                    onConfig = { navController.navigate(Routes.Config.route) },
                    onReports = { navController.navigate(Routes.Reports.route) },
                    onProviders = { navController.navigate(Routes.ProvidersHub.route) },   // NUEVO
                    onExpenses = { navController.navigate(Routes.ExpensesHub.route) },
                    vm = homeVm,

                )
            }

            // -------------------- HUB DE CLIENTES ----------------------
            composable(Routes.ClientsHub.route) {
                ClientsHubScreen(
                    onCrud = { navController.navigate(Routes.ManageCustomers.route) },
                    onSearchPurchases = { navController.navigate(Routes.ClientPurchases.route) },
                    onMetrics = { navController.navigate(Routes.ClientMetrics.route) },
                    onExportCsv = null, // habilitable luego si sumamos Exportar CSV
                    onBack = { navController.popBackStack() }
                )
            }

            // CRUD clientes
            composable(Routes.ManageCustomers.route) {
                // Versión que usa repos inyectados desde SelliaApp()
                ManageCustomersScreen(
                    customerRepository = customerRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // Búsqueda de compras por cliente
            composable(Routes.ClientPurchases.route) {
                val vm = hiltViewModel<ClientPurchasesViewModel>()
                ClientPurchasesScreen(vm = vm, onBack = { navController.popBackStack() })
            }

            // Métricas de clientes
            composable(Routes.ClientMetrics.route) {
                val vm = hiltViewModel<ClientMetricsViewModel>()
                ClientMetricsScreen(vm = vm, onBack = { navController.popBackStack() })
            }




            // -------------------- FLUJO VENTA (scope compartido) -------
            navigation(
                startDestination = Routes.Sell.route,
                route = SELL_FLOW_ROUTE
            ) {
                // VENDER
                composable(Routes.Sell.route) {
                    val sellVm: SellViewModel = hiltViewModel()
                    val productVm: ProductViewModel = hiltViewModel()

                    val currentEntry = navController.currentBackStackEntry
                    val scannedCode by currentEntry
                        ?.savedStateHandle
                        ?.getStateFlow<String?>("scanned_code", null)
                        ?.collectAsState(initial = null)
                        ?: remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(scannedCode) {
                        scannedCode?.let { code ->
                            val product = productVm.getByBarcode(code)
                            if (product != null) {
                                sellVm.addToCart(product, 1)
                            } else {
                                navController.navigate(Routes.AddProduct.build(prefillBarcode = code))
                            }
                            currentEntry?.savedStateHandle?.set("scanned_code", null)
                        }
                    }

                    SellScreen(
                        sellVm = sellVm,
                        productVm = productVm,
                        onScanClick = { navController.navigate(Routes.ScannerForSell.route) },
                        onBack = { navController.popBackStack() },
                        navController = navController
                    )
                }


                // CHECKOUT (usa el MISMO VM del flujo con CompositionLocalProvider)
                composable(Routes.Checkout.route) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val parentEntry = remember(navBackStackEntry) {
                        navController.getBackStackEntry(SELL_FLOW_ROUTE) // ej.: "sell_flow"
                    }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides parentEntry) {
                        CheckoutScreen(
                            onCancel = { navController.popBackStack() },
                            navController = navController
                        )
                    }
                }
            }

            // Escáner para venta → devuelve "scanned_code" (puede quedar fuera del flow)
            composable(Routes.ScannerForSell.route) {
                BarcodeScannerScreen(
                    onClose = { navController.popBackStack() },
                    onDetected = { code ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_code", code)
                        navController.popBackStack()
                    }
                )
            }


            // -------------------- STOCK -------------------------------
            composable(Routes.Stock.route) {

                 // Si querés usar el VM de este destino:
                val vm: ProductViewModel = hiltViewModel()
                // Escuchar el resultado del escáner de stock
                val entry = navController.currentBackStackEntry
                val scannedForStock by entry
                    ?.savedStateHandle
                    ?.getStateFlow<String?>("scanned_stock_code", null) // <- llave unificada stock
                    ?.collectAsState(initial = null)
                    ?: remember { mutableStateOf<String?>(null) }

                // Si llega un código de barras desde el escáner, lo manejamos en tu StockScreen (diálogo o acción)
                LaunchedEffect(scannedForStock) {
                    scannedForStock?.let { barcode ->
                        entry?.savedStateHandle?.set("scanned_stock_code", null)
                    }
                }

                StockScreen(
                    vm = vm,
                    onAddProduct = { navController.navigate(Routes.AddProduct.route) },
                    onScan =  { navController.navigate(Routes.ScannerForStock.route) },
                    onImportCsv =  { navController.navigate(Routes.Stock_import.route) }, // <-- ÚNICO callback para importar CSV
                    onProductClick = { product ->
                        // EDICIÓN: ir a add_product/{id}
                        navController.navigate(Routes.AddProduct.withId(product.id.toLong()))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.Stock_import.route) {
                val vm: StockImportViewModel = hiltViewModel()
                StockImportScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }


            composable(
                route = Routes.AddProduct.route +
                        "?${Routes.AddProduct.ARG_BARCODE}={${Routes.AddProduct.ARG_BARCODE}}" +
                        "&${Routes.AddProduct.ARG_NAME}={${Routes.AddProduct.ARG_NAME}}",
                arguments = listOf<NamedNavArgument>( // [NUEVO] tipado explícito
                    navArgument(Routes.AddProduct.ARG_BARCODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument(Routes.AddProduct.ARG_NAME) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val vm: ProductViewModel = hiltViewModel()
                val barcodeArg = backStackEntry.arguments?.getString(Routes.AddProduct.ARG_BARCODE).orEmpty()
                val nameArg = backStackEntry.arguments?.getString(Routes.AddProduct.ARG_NAME).orEmpty()

                AddProductScreen(
                    viewModel = vm,
                    prefillBarcode = barcodeArg.ifBlank { null },
                    prefillName = nameArg.ifBlank { null },
                    editId = null,
                    onSaved = { navController.popBackStack() },
                    navController = navController
                )
            }

            // 2) EDICIÓN con id en el path: add_product/{id}
            composable(
                route = Routes.AddProduct.withIdPattern,
                arguments = listOf<NamedNavArgument>( // [NUEVO] tipado explícito
                    navArgument(Routes.AddProduct.ARG_ID) { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val vm: ProductViewModel = hiltViewModel()
                val id = backStackEntry.arguments?.getLong(Routes.AddProduct.ARG_ID) ?: 0L

                AddProductScreen(
                    viewModel = vm,
                    prefillBarcode = null,
                    prefillName = null,
                    editId = id.toInt(),
                    onSaved = { navController.popBackStack() },
                    navController = navController
                )
            }

            // Escáner de stock → devuelve "scanned_stock_code"
            composable(Routes.ScannerForStock.route) {
                BarcodeScannerScreen(
                    onClose = { navController.popBackStack() },
                    onDetected = { code ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_stock_code", code)
                        navController.popBackStack()
                    },
                )
            }



            // -------------------- CONFIGURACIÓN ------------------------
            composable(Routes.Config.route) {
                ConfigScreen(
                    onAddUser = { navController.navigate(Routes.AddUser.route) },
                    onManageProducts = { navController.navigate(Routes.ManageProducts.route) },
                    onManageCustomers = { navController.navigate(Routes.ManageCustomers.route) },
                    onSync = { navController.navigate(Routes.Sync.route) },
                    onBack = { navController.popBackStack() }
                )
            }

            // Alta de usuario desde Config
            composable(Routes.AddUser.route) {
                AddUserScreen(
                    onSave = { name, email, role ->
                        if (name.isBlank() || email.isBlank()) {
                            Toast.makeText(context, "Completá nombre y email", Toast.LENGTH_SHORT).show()
                            return@AddUserScreen
                        }
                        userViewModel.addUser(name, email, role)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            // -------------------- REPORTES -----------------------------
            composable(Routes.Reports.route) {
                val vm: ReportsViewModel = hiltViewModel()
                // Si tu ReportsScreen actual sólo necesita onBack, dejamos simple:
                ReportsScreen(
                    onBack = { navController.popBackStack() },
                    vm = vm,
                    navController =  navController
                )

            }


            // -------------------- (NUEVO) LISTA FACTURAS VENTA ---------
            composable(Routes.SalesInvoices.route) { // [NUEVO]
                val vm: SalesInvoicesViewModel = hiltViewModel()
                SalesInvoicesScreen(
                    vm = vm,
                    onOpenDetail = { id -> navController.navigate(Routes.SalesInvoiceDetail.withId(id)) },
                    onBack = { navController.popBackStack() }
                )
            }

            // -------------------- (NUEVO) DETALLE FACTURA --------------
            composable(
                route = Routes.SalesInvoiceDetail.route, // sales_invoice_detail/{invoiceId}
                arguments = listOf<NamedNavArgument>(
                    navArgument(Routes.SalesInvoiceDetail.ARG_ID) { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val vm: SalesInvoiceDetailViewModel = hiltViewModel()
                SalesInvoiceDetailScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }



            // -------------------- MANAGE PRODUCTS ----------------------
            composable(Routes.ManageProducts.route) {
                val vm: ManageProductsViewModel = hiltViewModel()
                ManageProductsScreen(
                     vm = vm,
                     onBack = { navController.popBackStack() }
                )
            }

            // -------------------- SYNC ----------------------
            composable(Routes.Sync.route) {
                SyncScreen(onBack = { navController.popBackStack() })
            }

            // ---------- PROVEEDORES ----------
            composable(Routes.ProvidersHub.route) {
                ProvidersHubScreen(
                    onManageProviders = { navController.navigate(Routes.ManageProviders.route) },
                    onProviderInvoices = { navController.navigate(Routes.ProviderInvoices.route) },
                    onProviderPayments = { navController.navigate(Routes.ProviderPayments.route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ManageProviders.route) {
                val repo = hiltViewModel<ProvidersEntryPoint>().repo // ver nota de DI abajo
                ManageProvidersScreen(repo = repo, onBack = { navController.popBackStack() })
            }

            composable(Routes.ProviderInvoices.route) {
                val pRepo = hiltViewModel<ProvidersEntryPoint>().repo
                val invRepo = hiltViewModel<ProviderInvoicesEntryPoint>().repo
                ProviderInvoicesScreen(
                    providerRepo = pRepo,
                    invoiceRepo = invRepo,
                    onOpenDetail = { id -> navController.navigate("provider_invoice_detail?invoiceId=$id") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ProviderInvoiceDetail.route) { backStackEntry ->
                val invRepo = hiltViewModel<ProviderInvoicesEntryPoint>().repo
                val id = backStackEntry.arguments?.getString("invoiceId")?.toIntOrNull() ?: 0
                ProviderInvoiceDetailScreen(invoiceId = id, repo = invRepo, onBack = { navController.popBackStack() })
            }

            composable(Routes.ProviderPayments.route) {
                val invRepo = hiltViewModel<ProviderInvoicesEntryPoint>().repo
                ProviderPaymentsScreen(repo = invRepo, onBack = { navController.popBackStack() })
            }

            // ---------- GASTOS ----------
            composable(Routes.ExpensesHub.route) {
                ExpensesHubScreen(
                    onTemplates = { navController.navigate(Routes.ExpenseTemplates.route) },
                    onEntries = { navController.navigate(Routes.ExpenseEntries.route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ExpenseTemplates.route) {
                val repo = hiltViewModel<ExpensesEntryPoint>().repo
                ExpenseTemplatesScreen(repo = repo, onBack = { navController.popBackStack() })
            }

            composable(Routes.ExpenseEntries.route) {
                val repo = hiltViewModel<ExpensesEntryPoint>().repo
                ExpenseEntriesScreen(repo = repo, onBack = { navController.popBackStack() })
            }



        }
    }
}
