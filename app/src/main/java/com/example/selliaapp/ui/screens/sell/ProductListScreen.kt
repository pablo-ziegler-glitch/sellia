package com.example.selliaapp.ui.screens.sell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.viewmodel.ProductViewModel

@Composable
fun ProductListScreen(
    navController: NavController,
    viewModel: ProductViewModel
) {
    val products by viewModel.products.collectAsState(emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("add_product") }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar")
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(products) { product ->
                ProductItem(product)
            }
        }
    }
}

@Composable
fun ProductItem(product: ProductEntity) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        Text(text = product.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Lista: ${product.listPrice ?: product.price ?: "-"} · " +
                "Efectivo: ${product.cashPrice ?: product.listPrice ?: product.price ?: "-"} · " +
                "Transferencia: ${product.transferPrice ?: product.listPrice ?: product.price ?: "-"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "ML: ${product.mlPrice ?: "-"} · ML 3C: ${product.ml3cPrice ?: "-"} · ML 6C: ${product.ml6cPrice ?: "-"}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
