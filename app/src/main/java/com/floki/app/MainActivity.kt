package com.floki.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.floki.app.repository.CustomerRepository
import com.floki.app.security.DeepLinkSecurity
import com.floki.app.repository.ProductRepository
import com.floki.app.ui.navigation.SelliaRoot
import com.floki.app.ui.theme.ValkirjaTheme
import com.floki.app.viewmodel.AppThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity principal con entrada para Hilt.
 */


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Inyecciones de Hilt (AppModule debe proveerlas)
    @Inject
    lateinit var productRepository: ProductRepository
    @Inject lateinit var customerRepository: CustomerRepository


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(DeepLinkSecurity.sanitizeIncomingIntent(intent))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setIntent(DeepLinkSecurity.sanitizeIncomingIntent(intent))
        super.onCreate(savedInstanceState)
        setContent {
            // Crea el NavController de Compose
            val navController = rememberNavController()

            val appThemeViewModel: AppThemeViewModel = hiltViewModel()
            val palette = appThemeViewModel.themePalette.collectAsStateWithLifecycle()

            ValkirjaTheme(dynamicPalette = palette.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SelliaRoot(
                        navController = navController,
                        customerRepo = customerRepository
                    )
                }
            }
        }
    }
}
