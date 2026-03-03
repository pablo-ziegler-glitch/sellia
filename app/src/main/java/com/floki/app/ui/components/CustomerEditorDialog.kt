package com.floki.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.floki.app.data.local.entity.CustomerEntity

@Composable
fun CustomerEditorDialog(
    initial: CustomerEntity?  = null,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial?.name.orEmpty())) }
    var email by remember { mutableStateOf(TextFieldValue(initial?.email.orEmpty())) }
    var phone by remember { mutableStateOf(TextFieldValue(initial?.phone.orEmpty())) }
    var nickname by remember { mutableStateOf(TextFieldValue(initial?.nickname.orEmpty())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cliente") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre *") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") })
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Apodo") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entity = (initial ?: CustomerEntity(id = 0, name = name.text.trim()))
                    .copy(
                        name = name.text.trim(),
                        email = email.text.trim().ifBlank { null },
                        phone = phone.text.trim().ifBlank { null },
                        nickname = nickname.text.trim().ifBlank { null }
                        // createdAt: queda por default si es alta nueva
                    )
                onSave(entity)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}