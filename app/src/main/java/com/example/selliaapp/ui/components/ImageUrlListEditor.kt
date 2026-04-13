package com.example.selliaapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageUrlListEditor(
    imageUrls: SnapshotStateList<String>,
    modifier: Modifier = Modifier,
    label: String = "Imágenes"
) {
    var fullscreenImageIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        if (imageUrls.isEmpty()) {
            Text("Sin imágenes cargadas.")
            return@Column
        }

        val pagerState = rememberPagerState(pageCount = { imageUrls.size })

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            val url = imageUrls.getOrNull(page).orEmpty()
            AsyncImage(
                model = url,
                contentDescription = "Preview de imagen ${page + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable {
                        fullscreenImageIndex = page
                    },
                contentScale = ContentScale.Crop
            )
        }

        Text("Imagen ${pagerState.currentPage + 1} de ${imageUrls.size}")

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = {
                    val current = pagerState.currentPage
                    if (current <= 0) return@IconButton
                    val item = imageUrls.removeAt(current)
                    imageUrls.add(current - 1, item)
                },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Mover arriba")
            }
            IconButton(
                onClick = {
                    val current = pagerState.currentPage
                    if (current >= imageUrls.lastIndex) return@IconButton
                    val item = imageUrls.removeAt(current)
                    imageUrls.add(current + 1, item)
                },
                enabled = pagerState.currentPage < imageUrls.lastIndex
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Mover abajo")
            }
            IconButton(onClick = {
                val current = pagerState.currentPage
                if (current in imageUrls.indices) {
                    imageUrls.removeAt(current)
                }
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
            }
        }

        Spacer(Modifier.height(4.dp))
    }

    if (fullscreenImageIndex >= 0 && imageUrls.isNotEmpty()) {
        FullscreenImageViewerDialog(
            images = imageUrls.toList(),
            initialPage = fullscreenImageIndex,
            onDismiss = { fullscreenImageIndex = -1 }
        )
    }
}
