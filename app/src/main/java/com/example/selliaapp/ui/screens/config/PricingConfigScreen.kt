package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.sync.PricingScheduler
import com.example.selliaapp.viewmodel.PricingConfigViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingConfigScreen(
    onBack: () -> Unit,
    viewModel: PricingConfigViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val fixedCosts by viewModel.fixedCosts.collectAsState()
    val mlFixedCostTiers by viewModel.mlFixedCostTiers.collectAsState()
    val mlShippingTiers by viewModel.mlShippingTiers.collectAsState()
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }
    var editingCost by remember { mutableStateOf<PricingFixedCostEntity?>(null) }
    var showMlFixedDialog by remember { mutableStateOf(false) }
    var editingMlFixed by remember { mutableStateOf<PricingMlFixedCostTierEntity?>(null) }
    var showMlShippingDialog by remember { mutableStateOf(false) }
    var editingMlShipping by remember { mutableStateOf<PricingMlShippingTierEntity?>(null) }

    var ivaTerminalText by remember { mutableStateOf("") }
    var ventasMensualesText by remember { mutableStateOf("") }
    var operativosLocalText by remember { mutableStateOf("") }
    var posnetText by remember { mutableStateOf("") }
    var transferenciaText by remember { mutableStateOf("") }
    var gananciaText by remember { mutableStateOf("") }
    var mlCommissionText by remember { mutableStateOf("") }
    var mlCuotas3Text by remember { mutableStateOf("") }
    var mlCuotas6Text by remember { mutableStateOf("") }
    var mlGainMinimumText by remember { mutableStateOf("") }
    var mlShippingThresholdText by remember { mutableStateOf("") }
    var mlWeightText by remember { mutableStateOf("") }
    var coef0To1500Text by remember { mutableStateOf("") }
    var coef1501To3000Text by remember { mutableStateOf("") }
    var coef3001To5000Text by remember { mutableStateOf("") }
    var coef5001To7500Text by remember { mutableStateOf("") }
    var coef7501To10000Text by remember { mutableStateOf("") }
    var coef10001PlusText by remember { mutableStateOf("") }
    var recalcIntervalText by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        settings?.let {
            ivaTerminalText = it.ivaTerminalPercent.toString()
            ventasMensualesText = it.monthlySalesEstimate.toString()
            operativosLocalText = it.operativosLocalPercent.toString()
            posnetText = it.posnet3CuotasPercent.toString()
            transferenciaText = it.transferenciaRetencionPercent.toString()
            gananciaText = it.gainTargetPercent.toString()
            mlCommissionText = it.mlCommissionPercent.toString()
            mlCuotas3Text = it.mlCuotas3Percent.toString()
            mlCuotas6Text = it.mlCuotas6Percent.toString()
            mlGainMinimumText = it.mlGainMinimum.toString()
            mlShippingThresholdText = it.mlShippingThreshold.toString()
            mlWeightText = it.mlDefaultWeightKg.toString()
            coef0To1500Text = it.coefficient0To1500Percent.toString()
            coef1501To3000Text = it.coefficient1501To3000Percent.toString()
            coef3001To5000Text = it.coefficient3001To5000Percent.toString()
            coef5001To7500Text = it.coefficient5001To7500Percent.toString()
            coef7501To10000Text = it.coefficient7501To10000Percent.toString()
            coef10001PlusText = it.coefficient10001PlusPercent.toString()
            recalcIntervalText = it.recalcIntervalMinutes.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pricing y costos") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Volver") }
                }
            )
        },
        floatingActionButton = {
            IconButton(onClick = {
                editingCost = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar costo")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Parámetros generales")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ivaTerminalText,
                    onValueChange = { ivaTerminalText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("IVA terminal (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ventasMensualesText,
                    onValueChange = { ventasMensualesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Ventas mensuales estimadas") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = operativosLocalText,
                    onValueChange = { operativosLocalText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Operativos local (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = posnetText,
                    onValueChange = { posnetText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Posnet 3 cuotas (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = transferenciaText,
                    onValueChange = { transferenciaText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Transferencia retención (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gananciaText,
                    onValueChange = { gananciaText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Ganancia objetivo (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Coeficientes por rango")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = coef0To1500Text,
                    onValueChange = { coef0To1500Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("0 a 1500 (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coef1501To3000Text,
                    onValueChange = { coef1501To3000Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("1501 a 3000 (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coef3001To5000Text,
                    onValueChange = { coef3001To5000Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("3001 a 5000 (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coef5001To7500Text,
                    onValueChange = { coef5001To7500Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("5001 a 7500 (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coef7501To10000Text,
                    onValueChange = { coef7501To10000Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("7501 a 10000 (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coef10001PlusText,
                    onValueChange = { coef10001PlusText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("10001+ (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recalcIntervalText,
                    onValueChange = { recalcIntervalText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Recalcular cada (min)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Mercado Libre")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mlCommissionText,
                    onValueChange = { mlCommissionText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Comisión ML (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlCuotas3Text,
                    onValueChange = { mlCuotas3Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("ML 3 cuotas (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlCuotas6Text,
                    onValueChange = { mlCuotas6Text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("ML 6 cuotas (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlGainMinimumText,
                    onValueChange = { mlGainMinimumText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Ganancia mínima ML") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlShippingThresholdText,
                    onValueChange = { mlShippingThresholdText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Umbral envío ML") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlWeightText,
                    onValueChange = { mlWeightText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Peso envío ML (kg)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = {
                        val current = settings ?: return@Button
                        val updated = current.copy(
                            ivaTerminalPercent = ivaTerminalText.replace(',', '.').toDoubleOrNull() ?: current.ivaTerminalPercent,
                            monthlySalesEstimate = ventasMensualesText.toIntOrNull() ?: current.monthlySalesEstimate,
                            operativosLocalPercent = operativosLocalText.replace(',', '.').toDoubleOrNull()
                                ?: current.operativosLocalPercent,
                            posnet3CuotasPercent = posnetText.replace(',', '.').toDoubleOrNull()
                                ?: current.posnet3CuotasPercent,
                            transferenciaRetencionPercent = transferenciaText.replace(',', '.').toDoubleOrNull()
                                ?: current.transferenciaRetencionPercent,
                            gainTargetPercent = gananciaText.replace(',', '.').toDoubleOrNull()
                                ?: current.gainTargetPercent,
                            mlCommissionPercent = mlCommissionText.replace(',', '.').toDoubleOrNull()
                                ?: current.mlCommissionPercent,
                            mlCuotas3Percent = mlCuotas3Text.replace(',', '.').toDoubleOrNull()
                                ?: current.mlCuotas3Percent,
                            mlCuotas6Percent = mlCuotas6Text.replace(',', '.').toDoubleOrNull()
                                ?: current.mlCuotas6Percent,
                            mlGainMinimum = mlGainMinimumText.replace(',', '.').toDoubleOrNull()
                                ?: current.mlGainMinimum,
                            mlShippingThreshold = mlShippingThresholdText.replace(',', '.').toDoubleOrNull()
                                ?: current.mlShippingThreshold,
                            mlDefaultWeightKg = mlWeightText.replace(',', '.').toDoubleOrNull()
                                ?: current.mlDefaultWeightKg,
                            coefficient0To1500Percent = coef0To1500Text.replace(',', '.').toDoubleOrNull()
                                ?: current.coefficient0To1500Percent,
                            coefficient1501To3000Percent = coef1501To3000Text.replace(',', '.').toDoubleOrNull()
                                ?: current.coefficient1501To3000Percent,
                            coefficient3001To5000Percent = coef3001To5000Text.replace(',', '.').toDoubleOrNull()
                                ?: current.coefficient3001To5000Percent,
                            coefficient5001To7500Percent = coef5001To7500Text.replace(',', '.').toDoubleOrNull()
                                ?: current.coefficient5001To7500Percent,
                            coefficient7501To10000Percent = coef7501To10000Text.replace(',', '.').toDoubleOrNull()
                                ?: current.coefficient7501To10000Percent,
                            coefficient10001PlusPercent = coef10001PlusText.replace(',', '.').toDoubleOrNull()
                                ?: current.coefficient10001PlusPercent,
                            recalcIntervalMinutes = recalcIntervalText.toIntOrNull()
                                ?: current.recalcIntervalMinutes,
                            updatedAt = Instant.now(),
                            updatedBy = current.updatedBy
                        )
                        viewModel.saveSettings(updated)
                        PricingScheduler.enqueuePeriodic(context, updated.recalcIntervalMinutes)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar configuración")
                }
            }

            item {
                Text("Costos fijos")
                Spacer(Modifier.height(8.dp))
            }

            items(fixedCosts) { cost ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cost.name)
                        Text("${cost.amount}")
                        cost.description?.let { Text(it) }
                    }
                    Row {
                        IconButton(onClick = {
                            editingCost = cost
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { viewModel.deleteFixedCost(cost.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }

            item {
                Text("ML costos fijos")
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        editingMlFixed = null
                        showMlFixedDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Agregar costo fijo ML")
                }
            }

            items(mlFixedCostTiers) { tier ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hasta ${tier.maxPrice}")
                        Text("Costo ${tier.cost}")
                    }
                    Row {
                        IconButton(onClick = {
                            editingMlFixed = tier
                            showMlFixedDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { viewModel.deleteMlFixedCostTier(tier.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }

            item {
                Text("ML envío por peso")
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        editingMlShipping = null
                        showMlShippingDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Agregar envío ML")
                }
            }

            items(mlShippingTiers) { tier ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hasta ${tier.maxWeightKg} kg")
                        Text("Costo ${tier.cost}")
                    }
                    Row {
                        IconButton(onClick = {
                            editingMlShipping = tier
                            showMlShippingDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { viewModel.deleteMlShippingTier(tier.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        FixedCostDialog(
            initial = editingCost,
            onDismiss = { showDialog = false },
            onSave = { item ->
                viewModel.saveFixedCost(item)
                showDialog = false
            }
        )
    }

    if (showMlFixedDialog) {
        MlFixedCostDialog(
            initial = editingMlFixed,
            onDismiss = { showMlFixedDialog = false },
            onSave = { item ->
                viewModel.saveMlFixedCostTier(item)
                showMlFixedDialog = false
            }
        )
    }

    if (showMlShippingDialog) {
        MlShippingDialog(
            initial = editingMlShipping,
            onDismiss = { showMlShippingDialog = false },
            onSave = { item ->
                viewModel.saveMlShippingTier(item)
                showMlShippingDialog = false
            }
        )
    }
}

@Composable
private fun FixedCostDialog(
    initial: PricingFixedCostEntity?,
    onDismiss: () -> Unit,
    onSave: (PricingFixedCostEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var applyIva by remember { mutableStateOf(initial?.applyIva ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo costo fijo" else "Editar costo fijo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Monto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = applyIva, onCheckedChange = { applyIva = it })
                    Text("Aplicar IVA terminal")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onSave(
                        PricingFixedCostEntity(
                            id = initial?.id ?: 0,
                            name = name,
                            description = description.ifBlank { null },
                            amount = amount,
                            applyIva = applyIva
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun MlFixedCostDialog(
    initial: PricingMlFixedCostTierEntity?,
    onDismiss: () -> Unit,
    onSave: (PricingMlFixedCostTierEntity) -> Unit
) {
    var maxPriceText by remember { mutableStateOf(initial?.maxPrice?.toString() ?: "") }
    var costText by remember { mutableStateOf(initial?.cost?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo costo fijo ML" else "Editar costo fijo ML") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxPriceText,
                    onValueChange = { maxPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio máximo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Costo fijo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxPrice = maxPriceText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val cost = costText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onSave(
                        PricingMlFixedCostTierEntity(
                            id = initial?.id ?: 0,
                            maxPrice = maxPrice,
                            cost = cost
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun MlShippingDialog(
    initial: PricingMlShippingTierEntity?,
    onDismiss: () -> Unit,
    onSave: (PricingMlShippingTierEntity) -> Unit
) {
    var maxWeightText by remember { mutableStateOf(initial?.maxWeightKg?.toString() ?: "") }
    var costText by remember { mutableStateOf(initial?.cost?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo envío ML" else "Editar envío ML") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxWeightText,
                    onValueChange = { maxWeightText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Peso máximo (kg)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Costo envío") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxWeight = maxWeightText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val cost = costText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onSave(
                        PricingMlShippingTierEntity(
                            id = initial?.id ?: 0,
                            maxWeightKg = maxWeight,
                            cost = cost
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
