package androidx.compose.material3

import androidx.compose.ui.Modifier

/**
 * Compat helper for projects using Material3 artifacts without menuAnchor().
 * Keeps ExposedDropdownMenu anchors compiling across versions.
 */
fun Modifier.menuAnchor(): Modifier = this
