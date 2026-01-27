package com.example.selliaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.rememberNavController
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.ui.navigation.SelliaRoot
import com.example.selliaapp.ui.theme.ValkirjaTheme
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Crea el NavController de Compose
            val navController = rememberNavController()

            ValkirjaTheme {
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
