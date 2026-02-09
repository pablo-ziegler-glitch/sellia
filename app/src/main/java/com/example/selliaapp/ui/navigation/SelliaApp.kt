package com.example.selliaapp.ui.navigation


import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.example.selliaapp.auth.AuthState
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.MarketingSettings
import com.example.selliaapp.ui.components.AppScaffold
import com.example.selliaapp.ui.screens.HomeScreen
import com.example.selliaapp.ui.screens.MoreScreen
import com.example.selliaapp.ui.screens.alerts.UsageAlertsScreen
import com.example.selliaapp.ui.screens.barcode.BarcodeScannerScreen
import com.example.selliaapp.ui.screens.checkout.CheckoutScreen
import com.example.selliaapp.ui.screens.clients.ClientMetricsScreen
import com.example.selliaapp.ui.screens.clients.ClientPurchasesScreen
import com.example.selliaapp.ui.screens.clients.ClientsHubScreen
import com.example.selliaapp.ui.screens.config.BulkDataScreen
import com.example.selliaapp.ui.screens.config.CloudServicesAdminScreen
import com.example.selliaapp.ui.screens.config.DevelopmentOptionsScreen
import com.example.selliaapp.ui.screens.config.ConfigScreen
import com.example.selliaapp.ui.screens.config.SecuritySettingsScreen
import com.example.selliaapp.ui.screens.config.UserProfileDetails
import com.example.selliaapp.ui.screens.config.ManageUsersScreen
import com.example.selliaapp.ui.screens.config.MarketingConfigScreen
import com.example.selliaapp.ui.screens.config.PricingConfigScreen
import com.example.selliaapp.ui.screens.expenses.ExpenseEntriesScreen
import com.example.selliaapp.ui.screens.expenses.ExpenseTemplatesScreen
import com.example.selliaapp.ui.screens.expenses.ExpensesCashflowScreen
import com.example.selliaapp.ui.screens.expenses.ExpensesHubScreen
import com.example.selliaapp.ui.screens.cash.CashAuditScreen
import com.example.selliaapp.ui.screens.cash.CashCloseScreen
import com.example.selliaapp.ui.screens.cash.CashMovementsScreen
import com.example.selliaapp.ui.screens.cash.CashOpenScreen
import com.example.selliaapp.ui.screens.cash.CashReportScreen
import com.example.selliaapp.ui.screens.cash.CashScreen
import com.example.selliaapp.ui.screens.manage.ManageCustomersScreen
import com.example.selliaapp.ui.screens.manage.ManageProductsScreen
import com.example.selliaapp.ui.screens.manage.ProductQrScreen
import com.example.selliaapp.ui.screens.manage.SyncScreen
import com.example.selliaapp.ui.screens.public.PublicProductCatalogScreen
import com.example.selliaapp.ui.screens.public.PublicProductCardScreen
import com.example.selliaapp.ui.screens.providers.ManageProvidersScreen
import com.example.selliaapp.ui.screens.providers.ProviderInvoiceDetailScreen
import com.example.selliaapp.ui.screens.providers.ProviderInvoicesScreen
import com.example.selliaapp.sync.SyncScheduler
import com.example.selliaapp.ui.screens.providers.ProviderPaymentsScreen
import com.example.selliaapp.ui.screens.providers.ProvidersHubScreen
import com.example.selliaapp.ui.screens.reports.ReportsScreen
import com.example.selliaapp.ui.screens.sales.SalesInvoiceDetailScreen
import com.example.selliaapp.ui.screens.sales.SalesInvoicesScreen
import com.example.selliaapp.ui.screens.sell.AddProductScreen
import com.example.selliaapp.ui.screens.sell.SellScreen
import com.example.selliaapp.ui.screens.admin.UsageDashboardScreen
import com.example.selliaapp.ui.screens.pos.PosSuccessScreen
import com.example.selliaapp.ui.screens.stock.QuickReorderScreen
import com.example.selliaapp.ui.screens.stock.QuickStockAdjustScreen
import com.example.selliaapp.ui.screens.stock.StockImportScreen
import com.example.selliaapp.ui.screens.stock.StockMovementsScreen
import com.example.selliaapp.ui.screens.stock.StockScreen
import com.example.selliaapp.viewmodel.ClientMetricsViewModel
import com.example.selliaapp.viewmodel.ClientPurchasesViewModel
import com.example.selliaapp.viewmodel.HomeViewModel
import com.example.selliaapp.viewmodel.ManageProductsViewModel
import com.example.selliaapp.viewmodel.MarketingConfigViewModel
import com.example.selliaapp.viewmodel.ProductViewModel
import com.example.selliaapp.viewmodel.QuickReorderViewModel
import com.example.selliaapp.viewmodel.QuickStockAdjustViewModel
import com.example.selliaapp.viewmodel.ReportsViewModel
import com.example.selliaapp.viewmodel.SellViewModel
import com.example.selliaapp.viewmodel.StockImportViewModel
import com.example.selliaapp.viewmodel.UserViewModel
import com.example.selliaapp.viewmodel.StockMovementsViewModel
import com.example.selliaapp.viewmodel.AccessControlViewModel
import com.example.selliaapp.viewmodel.AuthViewModel
import com.example.selliaapp.viewmodel.SecuritySettingsViewModel
import com.example.selliaapp.viewmodel.cash.CashViewModel
import com.example.selliaapp.viewmodel.sales.SalesInvoiceDetailViewModel
import com.example.selliaapp.viewmodel.sales.SalesInvoicesViewModel
import com.example.selliaapp.viewmodel.admin.UsageDashboardViewModel
import com.example.selliaapp.viewmodel.admin.AccountRequestsViewModel
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.domain.security.Permission
import com.example.selliaapp.ui.components.buildAccountSummary


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
    val authViewModel: AuthViewModel = hiltViewModel()


    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    AppScaffold(
        currentDestination = currentDestination,
        onNavigate = { route ->
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = false
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
        },
        snackbarHostState = snackbarHostState
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // -------------------- HOME (rediseñada) --------------------
            composable(Routes.Home.route) {
                val homeVm: HomeViewModel = hiltViewModel()
                val accessVm: AccessControlViewModel = hiltViewModel()
                val marketingVm: MarketingConfigViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val accountSummary = remember(authState, accessState) {
                    buildAccountSummary(authState, accessState)
                }
                val marketingSettings by marketingVm.settings.collectAsStateWithLifecycle(
                    initialValue = MarketingSettings()
                )

                HomeScreen(
                    onNewSale = { navController.navigate(Routes.Pos.route) },
                    onStock = { navController.navigate(Routes.Stock.route) },
                    onClientes = { navController.navigate(Routes.ClientsHub.route) },
                    onConfig = { navController.navigate(Routes.Config.route) },
                    onReports = { navController.navigate(Routes.Reports.route) },
                    onProviders = { navController.navigate(Routes.ProvidersHub.route) },   // NUEVO
                    onExpenses = { navController.navigate(Routes.ExpensesHub.route) },
                    onPublicCatalog = { navController.navigate(Routes.PublicProductCatalog.route) },
                    onPublicProductScan = { navController.navigate(Routes.PublicProductScan.route) },
                    onSyncNow = { SyncScheduler.enqueueNow(context, false) },
                    onAlertAdjustStock = { productId ->
                        navController.navigate(Routes.QuickAdjustStock.withProduct(productId))
                    },
                    onAlertCreatePurchase = { productId ->
                        navController.navigate(Routes.QuickReorder.withProduct(productId))
                    },
                    onViewStockMovements = { navController.navigate(Routes.StockMovements.route) },
                    onCashOpen = { navController.navigate(Routes.CashOpen.route) },
                    onCashHub = { navController.navigate(Routes.Cash.route) },
                    vm = homeVm,
                    accountSummary = accountSummary,
                    storeName = marketingSettings.storeName,
                    storeLogoUrl = marketingSettings.storeLogoUrl
                )
            }

            composable(Routes.Cash.route) {
                val cashVm: CashViewModel = hiltViewModel()
                val accessVm: AccessControlViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val accountSummary = remember(authState, accessState) {
                    buildAccountSummary(authState, accessState)
                }
                CashScreen(
                    vm = cashVm,
                    onOpen = { navController.navigate(Routes.CashOpen.route) },
                    onAudit = { navController.navigate(Routes.CashAudit.route) },
                    onMovements = { navController.navigate(Routes.CashMovements.route) },
                    onClose = { navController.navigate(Routes.CashClose.route) },
                    onReport = { navController.navigate(Routes.CashReport.route) },
                    accountSummary = accountSummary
                )
            }

            composable(Routes.CashOpen.route) {
                val cashVm: CashViewModel = hiltViewModel()
                CashOpenScreen(vm = cashVm, onBack = { navController.popBackStack() })
            }

            composable(Routes.CashAudit.route) {
                val cashVm: CashViewModel = hiltViewModel()
                CashAuditScreen(vm = cashVm, onBack = { navController.popBackStack() })
            }

            composable(Routes.CashMovements.route) {
                val cashVm: CashViewModel = hiltViewModel()
                CashMovementsScreen(vm = cashVm, onBack = { navController.popBackStack() })
            }

            composable(Routes.CashClose.route) {
                val cashVm: CashViewModel = hiltViewModel()
                CashCloseScreen(
                    vm = cashVm,
                    onBack = { navController.popBackStack() },
                    onCloseSuccess = {
                        navController.navigate(Routes.Cash.route) {
                            launchSingleTop = true
                            popUpTo(Routes.Cash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.CashReport.route) {
                val cashVm: CashViewModel = hiltViewModel()
                CashReportScreen(vm = cashVm, onBack = { navController.popBackStack() })
            }

            composable(Routes.More.route) {
                val accessVm: AccessControlViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val accountSummary = remember(authState, accessState) {
                    buildAccountSummary(authState, accessState)
                }
                MoreScreen(
                    onStock = { navController.navigate(Routes.Stock.route) },
                    onStockHistory = { navController.navigate(Routes.StockMovements.route) },
                    onCustomers = { navController.navigate(Routes.ClientsHub.route) },
                    onProviders = { navController.navigate(Routes.ProvidersHub.route) },
                    onExpenses = { navController.navigate(Routes.ExpensesHub.route) },
                    onReports = { navController.navigate(Routes.Reports.route) },
                    onAlerts = { navController.navigate(Routes.UsageAlerts.route) },
                    onSettings = { navController.navigate(Routes.Config.route) },
                    onSync = { SyncScheduler.enqueueNow(context, false) },
                    onManageUsers = { navController.navigate(Routes.AddUser.route) },
                    onSignOut = { authViewModel.signOut() },
                    accountSummary = accountSummary,
                    canManageUsers = accessState.permissions.contains(Permission.MANAGE_USERS)
                )
            }

            composable(Routes.UsageAlerts.route) {
                val accessVm: AccessControlViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                UsageAlertsScreen(
                    onBack = { navController.popBackStack() },
                    canEditLimits = accessState.role == AppRole.ADMIN || accessState.role == AppRole.SUPER_ADMIN
                )
            }

            composable(
                route = Routes.PosSuccess.route,
                arguments = listOf(
                    navArgument(Routes.PosSuccess.ARG_ID) { type = NavType.LongType },
                    navArgument(Routes.PosSuccess.ARG_TOTAL) { type = NavType.FloatType },
                    navArgument(Routes.PosSuccess.ARG_METHOD) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val invoiceId = backStackEntry.arguments?.getLong(Routes.PosSuccess.ARG_ID) ?: 0L
                val total = backStackEntry.arguments?.getFloat(Routes.PosSuccess.ARG_TOTAL)?.toDouble() ?: 0.0
                val method = backStackEntry.arguments?.getString(Routes.PosSuccess.ARG_METHOD).orEmpty()
                PosSuccessScreen(
                    invoiceId = invoiceId,
                    total = total,
                    method = method,
                    onNewSale = { navController.navigate(Routes.Pos.route) },
                    onViewSale = { navController.navigate(Routes.SalesInvoiceDetail.withId(invoiceId)) }
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
                startDestination = Routes.Pos.route,
                route = Routes.SellRoutes.SELL_FLOW_ROUTE
            ) {
                // VENDER
                composable(Routes.Pos.route) {
                    val parentEntry = remember { navController.getBackStackEntry(Routes.SellRoutes.SELL_FLOW_ROUTE) }
                    val sellVm: SellViewModel = hiltViewModel(parentEntry)
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
                                currentEntry?.savedStateHandle?.set("pending_product_id", product.id)
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
                composable(Routes.PosPayment.route) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val parentEntry = remember(navBackStackEntry) {
                        navController.getBackStackEntry(Routes.SellRoutes.SELL_FLOW_ROUTE) // ej.: "sell_flow"
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

            composable(Routes.PublicProductScan.route) {
                BarcodeScannerScreen(
                    onClose = { navController.popBackStack() },
                    onDetected = { code ->
                        navController.navigate(Routes.PublicProductCard.withQr(code))
                    }
                )
            }

            composable(Routes.PublicProductCatalog.route) {
                val productVm: ProductViewModel = hiltViewModel()
                PublicProductCatalogScreen(
                    onBack = { navController.popBackStack() },
                    onProductSelected = { productId ->
                        navController.navigate(Routes.PublicProductDetail.withId(productId))
                    },
                    vm = productVm
                )
            }

            composable(
                route = Routes.PublicProductCard.route +
                    "?${Routes.PublicProductCard.ARG_QR}={${Routes.PublicProductCard.ARG_QR}}",
                arguments = Routes.PublicProductCard.arguments,
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "sellia://product?q={${Routes.PublicProductCard.ARG_QR}}"
                    }
                )
            ) { backStackEntry ->
                val qrValue = backStackEntry.arguments
                    ?.getString(Routes.PublicProductCard.ARG_QR)
                    .orEmpty()
                PublicProductCardScreen(
                    qrValue = qrValue,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.PublicProductDetail.route,
                arguments = Routes.PublicProductDetail.arguments
            ) { backStackEntry ->
                val productId = backStackEntry.arguments
                    ?.getInt(Routes.PublicProductDetail.ARG_PRODUCT_ID)
                PublicProductCardScreen(
                    productId = productId,
                    onBack = { navController.popBackStack() }
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
                route = Routes.QuickAdjustStock.route,
                arguments = Routes.QuickAdjustStock.arguments
            ) {
                val vm: QuickStockAdjustViewModel = hiltViewModel()
                val uiState by vm.state.collectAsState()
                LaunchedEffect(uiState.success) {
                    if (uiState.success) {
                        snackbarHostState.showSnackbar("Ajuste registrado correctamente")
                        navController.popBackStack()
                    }
                }
                QuickStockAdjustScreen(
                    state = uiState,
                    onBack = { navController.popBackStack() },
                    onDeltaChange = vm::onDeltaChange,
                    onReasonSelected = vm::onReasonSelected,
                    onNoteChange = vm::onNoteChange,
                    onSubmit = vm::submit
                )
            }

            composable(
                route = Routes.QuickReorder.route,
                arguments = Routes.QuickReorder.arguments
            ) {
                val vm: QuickReorderViewModel = hiltViewModel()
                val uiState by vm.state.collectAsState()
                LaunchedEffect(uiState.success) {
                    if (uiState.success) {
                        snackbarHostState.showSnackbar("Orden creada")
                        val invoiceId = uiState.createdInvoiceId
                        navController.popBackStack()
                        if (invoiceId != null) {
                            navController.navigate(Routes.ProviderInvoiceDetail.build(invoiceId))
                        }
                    }
                }
                QuickReorderScreen(
                    state = uiState,
                    onBack = { navController.popBackStack() },
                    onProviderSelected = vm::onProviderSelected,
                    onQuantityChange = vm::onQuantityChange,
                    onUnitPriceChange = vm::onUnitPriceChange,
                    onToggleReceive = vm::onToggleAutoReceive,
                    onSubmit = vm::createOrder
                )
            }

            composable(Routes.StockMovements.route) {
                val vm: StockMovementsViewModel = hiltViewModel()
                val uiState by vm.state.collectAsState()
                StockMovementsScreen(
                    state = uiState,
                    onBack = { navController.popBackStack() },
                    onFilterChange = vm::selectReason
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
                val accessVm: AccessControlViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val accountSummary = remember(authState, accessState) {
                    buildAccountSummary(authState, accessState)
                }
                val userProfile = remember(authState, accessState) {
                    val session = (authState as? AuthState.Authenticated)?.session
                    UserProfileDetails(
                        displayName = accountSummary.displayName,
                        email = accountSummary.email,
                        roleLabel = accountSummary.roleLabel,
                        uid = session?.uid,
                        tenantId = session?.tenantId
                    )
                }
                ConfigScreen(
                    accountSummary = accountSummary,
                    userProfile = userProfile,
                    onPricingConfig = { navController.navigate(Routes.PricingConfig.route) },
                    onMarketingConfig = { navController.navigate(Routes.MarketingConfig.route) },
                    onSync = { navController.navigate(Routes.Sync.route) },
                    onProductQrs = { navController.navigate(Routes.ProductQr.route) },
                    onBulkData = { navController.navigate(Routes.BulkData.route) },
                    onCloudServicesAdmin = { navController.navigate(Routes.CloudServicesAdmin.route) },
                    onManageUsers = { navController.navigate(Routes.AddUser.route) },
                    onUsageAlerts = { navController.navigate(Routes.UsageAlerts.route) },
                    onSecuritySettings = { navController.navigate(Routes.SecuritySettings.route) },
                    canManageCloudServices = accessState.permissions.contains(Permission.MANAGE_CLOUD_SERVICES),
                    canManageUsers = accessState.permissions.contains(Permission.MANAGE_USERS),
                    onDevelopmentOptions = { navController.navigate(Routes.DevelopmentOptions.route) },
                    showDevelopmentOptions = accessState.role == AppRole.ADMIN || accessState.role == AppRole.SUPER_ADMIN,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.BulkData.route) {
                val accessVm: AccessControlViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                BulkDataScreen(
                    onBack = { navController.popBackStack() },
                    onManageProducts = { navController.navigate(Routes.ManageProducts.route) },
                    onManageCustomers = { navController.navigate(Routes.ManageCustomers.route) },
                    onManageUsers = { navController.navigate(Routes.AddUser.route) },
                    canManageUsers = accessState.permissions.contains(Permission.MANAGE_USERS)
                )
            }

            composable(Routes.PricingConfig.route) {
                PricingConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.MarketingConfig.route) {
                val vm: MarketingConfigViewModel = hiltViewModel()
                MarketingConfigScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SecuritySettings.route) {
                val vm: SecuritySettingsViewModel = hiltViewModel()
                SecuritySettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CloudServicesAdmin.route) {
                CloudServicesAdminScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.DevelopmentOptions.route) {
                DevelopmentOptionsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Gestión de usuarios y roles
            composable(Routes.AddUser.route) {
                val accessVm: AccessControlViewModel = hiltViewModel()
                val accessState by accessVm.state.collectAsStateWithLifecycle()
                val requestsVm: AccountRequestsViewModel = hiltViewModel()
                ManageUsersScreen(
                    vm = userViewModel,
                    requestsViewModel = requestsVm,
                    onBack = { navController.popBackStack() },
                    canManageUsers = accessState.permissions.contains(Permission.MANAGE_USERS)
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

            composable(Routes.Sales.route) {
                val vm: SalesInvoicesViewModel = hiltViewModel()
                SalesInvoicesScreen(
                    vm = vm,
                    onOpenDetail = { id -> navController.navigate(Routes.SaleDetail.withId(id)) },
                    onBack = { navController.popBackStack() }
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

            composable(
                route = Routes.SaleDetail.route,
                arguments = listOf<NamedNavArgument>(
                    navArgument(Routes.SaleDetail.ARG_ID) { type = NavType.LongType }
                )
            ) {
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
                     onBack = { navController.popBackStack() },
                     onShowQr = { navController.navigate(Routes.ProductQr.route) },
                     onBulkImport = { navController.navigate(Routes.BulkData.route) }
                )
            }

            composable(Routes.ProductQr.route) {
                ProductQrScreen(
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
                    onCashflow = { navController.navigate(Routes.ExpensesCashflow.route) },
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

            composable(Routes.ExpensesCashflow.route) {
                val repo = hiltViewModel<ExpensesEntryPoint>().repo
                ExpensesCashflowScreen(repo = repo, onBack = { navController.popBackStack() })
            }



        }
    }
}
