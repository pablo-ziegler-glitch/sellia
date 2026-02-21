package com.example.selliaapp.ui.screens.barcode

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.selliaapp.ui.screens.barcode.internal.BarcodeAnalyzer
import com.google.zxing.client.android.BeepManager
import kotlinx.coroutines.android.awaitFrame

/**
 * Pantalla de scanner usando CameraX + ML Kit.
 * - Muestra preview de cámara.
 * - Analiza frames con BarcodeAnalyzer.
 * - Activa/Desactiva linterna.
 * - Al detectar un código válido, invoca [onDetected] UNA sola vez y vuelve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onDetected: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lastTs by remember { mutableStateOf(0L) }
    val windowMs = 800L
    var flashlightOn by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }

    // Estado de permiso de cámara
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    // Si el permiso no está, mostramos un mensaje y un botón para abrir ajustes/solicitar.
    // (Podés reemplazar esto por Accompanist Permissions si lo preferís.)
    if (!hasCameraPermission) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Se requiere permiso de cámara para escanear",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    // Reintenta consultar el permiso (si ya fue dado por Ajustes)
                    hasCameraPermission =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                }) {
                    Text("Volver a intentar")
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose) {
                    Text("Cancelar")
                }
            }
        }
        return
    }

    // Torch (linterna) on/off
    var torchEnabled by remember { mutableStateOf(false) }

    // Bandera para asegurarnos de llamar onDetected solo una vez por pantalla
    var consumed by remember { mutableStateOf(false) }

    // BeepManager necesita una Activity, NO un Context genérico.
    val activity = context as? Activity
    val beepManager = remember(activity) {activity?.let { BeepManager(it) }}

    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            // ---- Preview de cámara con CameraX ----
            val previewView = remember { PreviewView(context) }

            // AndroidView para alojar el PreviewView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView }
            )

            // Bind de cámara
            LaunchedEffect(Unit) {
                // Espera un frame para que el PreviewView esté medido
                awaitFrame()
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(
                                AspectRatioStrategy(
                                    AspectRatio.RATIO_16_9,
                                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                                )
                            )
                            .build()
                    )
                    .setTargetRotation(previewView.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                // Ventana anti-rebote
                val windowMs = 800L
                var lastTs = 0L

                analysis.setAnalyzer(ContextCompat.getMainExecutor(context),
                    BarcodeAnalyzer { code ->
                        val now = System.currentTimeMillis()
                        if (!consumed && now - lastTs > windowMs) {
                            lastTs = now
                            consumed = true // Evitamos múltiples llamadas
                            // Sonido de beep (opcional)
                            beepManager?.playBeepSoundAndVibrate()
                            onDetected(code)
                        }
                    }
                )

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, selector, preview, analysis
                )

                // Control de linterna
                camera.cameraControl.enableTorch(torchEnabled)
                snapshotFlow { torchEnabled }.collect { enabled ->
                    camera.cameraControl.enableTorch(enabled)
                }
            }

            // ---- Overlay superior con controles ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { torchEnabled = !torchEnabled }) {
                    Icon(
                        if (torchEnabled) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                        contentDescription = if (torchEnabled) "Apagar linterna" else "Encender linterna"
                    )
                }
            }

            // ---- Ayuda visual abajo ----
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Apuntá el código de barras dentro del recuadro")
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Se confirmará automáticamente al detectar un código válido")
                }
            }
        }
    }
}