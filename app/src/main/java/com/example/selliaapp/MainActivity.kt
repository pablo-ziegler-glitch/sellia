package com.example.selliaapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.security.DeepLinkSecurity
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.ui.navigation.SelliaRoot
import com.example.selliaapp.ui.theme.ValkirjaTheme
import com.example.selliaapp.viewmodel.AppThemeViewModel
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
