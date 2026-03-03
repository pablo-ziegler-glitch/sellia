package com.floki.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.floki.app.R

/**
 * Menú principal con muchos botones grandes.
 * Ahora: Column scrollable + padding seguro.
 */
@Composable
fun MainScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { navController.navigate("sell") },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_sell), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Vender", style = MaterialTheme.typography.titleMedium)
        }

        Button(
            onClick = { navController.navigate("stock") },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_stock), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Stock", style = MaterialTheme.typography.titleMedium)
        }

        // ... el resto de tus botones
        Button(
            onClick = { navController.navigate("config") },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_settings), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Configuración", style = MaterialTheme.typography.titleSmall)
        }
    }
}
