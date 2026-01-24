package com.example.selliaapp.ui.screens.public

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.selliaapp.R
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.MarketingConfigViewModel
import com.example.selliaapp.viewmodel.ProductViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PublicProductCardScreen(
    qrValue: String,
    onBack: () -> Unit,
    vm: ProductViewModel = hiltViewModel(),
    marketingVm: MarketingConfigViewModel = hiltViewModel()
) {
    val settings by marketingVm.settings.collectAsState()
    var product by remember { mutableStateOf<ProductEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(qrValue) {
        loading = true
        errorMessage = null
        product = vm.getByQrValue(qrValue)
        if (product == null) {
            errorMessage = "No encontramos el producto asociado a este QR."
        }
        loading = false
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Producto", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                loading -> {
                    Text(
                        text = "Buscando producto...",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                errorMessage != null -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                product != null -> {
                    ProductCard(
                        product = product!!,
                        storeName = settings.storeName,
                        storePhone = settings.storePhone,
                        storeWhatsapp = settings.storeWhatsapp,
                        storeEmail = settings.storeEmail
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
    product: ProductEntity,
    storeName: String,
    storePhone: String,
    storeWhatsapp: String,
    storeEmail: String
) {
    val images: List<Any> = if (!product.imageUrl.isNullOrBlank()) {
        listOf(product.imageUrl!!, product.imageUrl!!, product.imageUrl!!)
    } else {
        listOf(R.drawable.ic_sell, R.drawable.ic_sell, R.drawable.ic_sell)
    }
    val pagerState = rememberPagerState { images.size }
    val currency = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) { page ->
                AsyncImage(
                    model = images[page],
                    contentDescription = "Imagen del producto ${product.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_sell),
                    error = painterResource(id = R.drawable.ic_sell)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(images.size) { index ->
                    val color = if (pagerState.currentPage == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .background(color = color, shape = CircleShape)
                    )
                }
            }

            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge
            )

            PriceRow(label = "Precio lista", value = formatPrice(product.listPrice, currency))
            PriceRow(label = "Precio efectivo", value = formatPrice(product.cashPrice, currency))

            if (!product.description.isNullOrBlank()) {
                Text(
                    text = product.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Contacto de la tienda",
                style = MaterialTheme.typography.titleMedium
            )
            if (storeName.isNotBlank()) {
                Text(text = storeName, style = MaterialTheme.typography.bodyMedium)
            }
            if (storePhone.isNotBlank()) {
                Text(text = "Teléfono: $storePhone", style = MaterialTheme.typography.bodyMedium)
            }
            if (storeWhatsapp.isNotBlank()) {
                Text(text = "WhatsApp: $storeWhatsapp", style = MaterialTheme.typography.bodyMedium)
            }
            if (storeEmail.isNotBlank()) {
                Text(text = "Email: $storeEmail", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatPrice(value: Double?, currency: NumberFormat): String =
    value?.let { currency.format(it) } ?: "-"
