package com.example.selliaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ImageUrlListEditor(
    imageUrls: SnapshotStateList<String>,
    modifier: Modifier = Modifier,
    label: String = "Imágenes"
) {
    var newUrl by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                label = { Text("Nueva URL") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val trimmed = newUrl.trim()
                    if (trimmed.isNotEmpty()) {
                        imageUrls.add(trimmed)
                        newUrl = ""
                    }
                }
            ) {
                Text("Agregar")
            }
        }

        if (imageUrls.isEmpty()) {
            Text("Sin imágenes cargadas.")
        }

        imageUrls.forEachIndexed { index, url ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Preview de imagen ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { updated -> imageUrls[index] = updated },
                    label = { Text("URL #${index + 1}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = {
                            val item = imageUrls.removeAt(index)
                            imageUrls.add(index - 1, item)
                        },
                        enabled = index > 0
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Mover arriba")
                    }
                    IconButton(
                        onClick = {
                            val item = imageUrls.removeAt(index)
                            imageUrls.add(index + 1, item)
                        },
                        enabled = index < imageUrls.lastIndex
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Mover abajo")
                    }
                    IconButton(onClick = { imageUrls.removeAt(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
