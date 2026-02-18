package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.sync.PricingScheduler
import com.example.selliaapp.viewmodel.PricingConfigViewModel
import java.time.Instant

private enum class PricingSection(val label: String) {
    GENERAL("General"),
    COEFFICIENTS("Coeficientes"),
    MERCADO_LIBRE("Mercado Libre"),
    COSTS("Costos y tramos")
}

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

    var activeSection by remember { mutableStateOf(PricingSection.GENERAL) }
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
    var fixedCostImputationMode by remember { mutableStateOf(PricingSettingsEntity.FixedCostImputationMode.FULL_TO_ALL_PRODUCTS) }
    var showFixedCostModeHelp by remember { mutableStateOf(false) }

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
            fixedCostImputationMode = it.fixedCostImputationMode
        }
    }

    val onSaveSettings: () -> Unit = onSaveSettings@ {
        val current = settings ?: return@onSaveSettings
        val updated = current.copy(
            ivaTerminalPercent = ivaTerminalText.parseDecimal(current.ivaTerminalPercent),
            monthlySalesEstimate = ventasMensualesText.toIntOrNull() ?: current.monthlySalesEstimate,
            operativosLocalPercent = operativosLocalText.parseDecimal(current.operativosLocalPercent),
            posnet3CuotasPercent = posnetText.parseDecimal(current.posnet3CuotasPercent),
            transferenciaRetencionPercent = transferenciaText.parseDecimal(current.transferenciaRetencionPercent),
            gainTargetPercent = gananciaText.parseDecimal(current.gainTargetPercent),
            mlCommissionPercent = mlCommissionText.parseDecimal(current.mlCommissionPercent),
            mlCuotas3Percent = mlCuotas3Text.parseDecimal(current.mlCuotas3Percent),
            mlCuotas6Percent = mlCuotas6Text.parseDecimal(current.mlCuotas6Percent),
            mlGainMinimum = mlGainMinimumText.parseDecimal(current.mlGainMinimum),
            mlShippingThreshold = mlShippingThresholdText.parseDecimal(current.mlShippingThreshold),
            mlDefaultWeightKg = mlWeightText.parseDecimal(current.mlDefaultWeightKg),
            coefficient0To1500Percent = coef0To1500Text.parseDecimal(current.coefficient0To1500Percent),
            coefficient1501To3000Percent = coef1501To3000Text.parseDecimal(current.coefficient1501To3000Percent),
            coefficient3001To5000Percent = coef3001To5000Text.parseDecimal(current.coefficient3001To5000Percent),
            coefficient5001To7500Percent = coef5001To7500Text.parseDecimal(current.coefficient5001To7500Percent),
            coefficient7501To10000Percent = coef7501To10000Text.parseDecimal(current.coefficient7501To10000Percent),
            coefficient10001PlusPercent = coef10001PlusText.parseDecimal(current.coefficient10001PlusPercent),
            fixedCostImputationMode = fixedCostImputationMode,
            recalcIntervalMinutes = recalcIntervalText.toIntOrNull() ?: current.recalcIntervalMinutes,
            updatedAt = Instant.now(),
            updatedBy = current.updatedBy
        )
        viewModel.saveSettings(updated)
        PricingScheduler.enqueuePeriodic(context, updated.recalcIntervalMinutes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pricing y costos") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }
            )
        },
        floatingActionButton = {
            if (activeSection == PricingSection.COSTS) {
                FloatingActionButton(
                    onClick = {
                        editingCost = null
                        showDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar costo fijo")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Configurá precios de forma guiada",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PricingSection.entries.forEach { section ->
                        FilterChip(
                            selected = activeSection == section,
                            onClick = { activeSection = section },
                            label = { Text(section.label) }
                        )
                    }
                }
            }

            when (activeSection) {
                PricingSection.GENERAL -> {
                    item {
                        SettingsCard("Parámetros base") {
                            DecimalField("IVA terminal (%)", ivaTerminalText) { ivaTerminalText = it.cleanNumeric() }
                            IntegerField("Ventas mensuales estimadas", ventasMensualesText) { ventasMensualesText = it.cleanInteger() }
                            FixedCostImputationModeSelector(
                                selectedMode = fixedCostImputationMode,
                                onModeChange = { fixedCostImputationMode = it },
                                onHelpClick = { showFixedCostModeHelp = true }
                            )
                            DecimalField("Operativos local (%)", operativosLocalText) { operativosLocalText = it.cleanNumeric() }
                            DecimalField("Posnet 3 cuotas (%)", posnetText) { posnetText = it.cleanNumeric() }
                            DecimalField("Retención transferencia (%)", transferenciaText) { transferenciaText = it.cleanNumeric() }
                            DecimalField("Ganancia objetivo (%)", gananciaText) { gananciaText = it.cleanNumeric() }
                        }
                    }
                }

                PricingSection.COEFFICIENTS -> {
                    item {
                        SettingsCard("Coeficientes por rango") {
                            DecimalField("0 a 1.500 (%)", coef0To1500Text) { coef0To1500Text = it.cleanNumeric() }
                            DecimalField("1.501 a 3.000 (%)", coef1501To3000Text) { coef1501To3000Text = it.cleanNumeric() }
                            DecimalField("3.001 a 5.000 (%)", coef3001To5000Text) { coef3001To5000Text = it.cleanNumeric() }
                            DecimalField("5.001 a 7.500 (%)", coef5001To7500Text) { coef5001To7500Text = it.cleanNumeric() }
                            DecimalField("7.501 a 10.000 (%)", coef7501To10000Text) { coef7501To10000Text = it.cleanNumeric() }
                            DecimalField("10.001+ (%)", coef10001PlusText) { coef10001PlusText = it.cleanNumeric() }
                            IntegerField("Recalcular cada (min)", recalcIntervalText) { recalcIntervalText = it.cleanInteger() }
                        }
                    }
                }

                PricingSection.MERCADO_LIBRE -> {
                    item {
                        SettingsCard("Comisiones y envío") {
                            DecimalField("Comisión ML (%)", mlCommissionText) { mlCommissionText = it.cleanNumeric() }
                            DecimalField("ML 3 cuotas (%)", mlCuotas3Text) { mlCuotas3Text = it.cleanNumeric() }
                            DecimalField("ML 6 cuotas (%)", mlCuotas6Text) { mlCuotas6Text = it.cleanNumeric() }
                            DecimalField("Ganancia mínima ML", mlGainMinimumText) { mlGainMinimumText = it.cleanNumeric() }
                            DecimalField("Umbral envío ML", mlShippingThresholdText) { mlShippingThresholdText = it.cleanNumeric() }
                            DecimalField("Peso envío ML (kg)", mlWeightText) { mlWeightText = it.cleanNumeric() }
                        }
                    }
                }

                PricingSection.COSTS -> {
                    item {
                        SettingsCard("Costos fijos") {
                            if (fixedCosts.isEmpty()) {
                                Text("Aún no hay costos fijos configurados.")
                            }
                        }
                    }
                    items(fixedCosts) { cost ->
                        EntityRowCard(
                            title = cost.name,
                            subtitle = "Monto: ${cost.amount}",
                            note = cost.description,
                            onEdit = {
                                editingCost = cost
                                showDialog = true
                            },
                            onDelete = { viewModel.deleteFixedCost(cost.id) }
                        )
                    }

                    item {
                        SettingsCard("Tramos ML - costo fijo") {
                            Button(
                                onClick = {
                                    editingMlFixed = null
                                    showMlFixedDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Agregar tramo") }
                        }
                    }
                    items(mlFixedCostTiers) { tier ->
                        EntityRowCard(
                            title = "Hasta ${tier.maxPrice}",
                            subtitle = "Costo fijo: ${tier.cost}",
                            onEdit = {
                                editingMlFixed = tier
                                showMlFixedDialog = true
                            },
                            onDelete = { viewModel.deleteMlFixedCostTier(tier.id) }
                        )
                    }

                    item {
                        SettingsCard("Tramos ML - envío") {
                            Button(
                                onClick = {
                                    editingMlShipping = null
                                    showMlShippingDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Agregar tramo de envío") }
                        }
                    }
                    items(mlShippingTiers) { tier ->
                        EntityRowCard(
                            title = "Hasta ${tier.maxWeightKg} kg",
                            subtitle = "Costo envío: ${tier.cost}",
                            onEdit = {
                                editingMlShipping = tier
                                showMlShippingDialog = true
                            },
                            onDelete = { viewModel.deleteMlShippingTier(tier.id) }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onSaveSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Text("Guardar configuración")
                }
            }
        }
    }

    if (showDialog) {
        FixedCostDialog(
            initial = editingCost,
            onDismiss = { showDialog = false },
            onSave = {
                viewModel.saveFixedCost(it)
                showDialog = false
            }
        )
    }

    if (showMlFixedDialog) {
        MlFixedCostDialog(
            initial = editingMlFixed,
            onDismiss = { showMlFixedDialog = false },
            onSave = {
                viewModel.saveMlFixedCostTier(it)
                showMlFixedDialog = false
            }
        )
    }

    if (showMlShippingDialog) {
        MlShippingDialog(
            initial = editingMlShipping,
            onDismiss = { showMlShippingDialog = false },
            onSave = {
                viewModel.saveMlShippingTier(it)
                showMlShippingDialog = false
            }
        )
    }

    if (showFixedCostModeHelp) {
        AlertDialog(
            onDismissRequest = { showFixedCostModeHelp = false },
            title = { Text("¿Cómo se imputa el costo fijo?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("• Modo recomendado: 100% a cada producto. Toma el costo fijo unitario (costo total/ventas mensuales) y lo aplica completo en todos los productos.")
                    Text("• Modo por rango de precios: mantiene el comportamiento anterior, aplicando un porcentaje distinto según el rango de precio de compra.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showFixedCostModeHelp = false }) { Text("Entendido") }
            }
        )
    }
}


@Composable
private fun FixedCostImputationModeSelector(
    selectedMode: String,
    onModeChange: (String) -> Unit,
    onHelpClick: () -> Unit
) {
    val fullMode = PricingSettingsEntity.FixedCostImputationMode.FULL_TO_ALL_PRODUCTS
    val rangeMode = PricingSettingsEntity.FixedCostImputationMode.BY_PRICE_RANGE

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Imputación de costo fijo", fontWeight = FontWeight.Medium)
            IconButton(onClick = onHelpClick) {
                Icon(Icons.Default.Info, contentDescription = "Ayuda sobre imputación de costo fijo")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedMode == fullMode,
                    onCheckedChange = { checked ->
                        if (checked) onModeChange(fullMode)
                    }
                )
                Column {
                    Text("100% para todos los productos", fontWeight = FontWeight.SemiBold)
                    Text("Aplica el costo fijo unitario completo en cada producto.")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedMode == rangeMode,
                    onCheckedChange = { checked ->
                        if (checked) onModeChange(rangeMode)
                    }
                )
                Column {
                    Text("Por rango de precios (actual)", fontWeight = FontWeight.SemiBold)
                    Text("Imputa un porcentaje del costo fijo según el rango de precio de compra.")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

@Composable
private fun DecimalField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun IntegerField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EntityRowCard(
    title: String,
    subtitle: String,
    note: String? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle)
                if (!note.isNullOrBlank()) {
                    Text(note)
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}

private fun String.cleanNumeric(): String = filter { it.isDigit() || it == '.' || it == ',' }
private fun String.cleanInteger(): String = filter { it.isDigit() }
private fun String.parseDecimal(fallback: Double): Double = replace(',', '.').toDoubleOrNull() ?: fallback

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
                    onValueChange = { amountText = it.cleanNumeric() },
                    label = { Text("Monto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyIva, onCheckedChange = { applyIva = it })
                    Text("Aplicar IVA terminal")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.parseDecimal(0.0)
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
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
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
                    onValueChange = { maxPriceText = it.cleanNumeric() },
                    label = { Text("Precio máximo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.cleanNumeric() },
                    label = { Text("Costo fijo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PricingMlFixedCostTierEntity(
                            id = initial?.id ?: 0,
                            maxPrice = maxPriceText.parseDecimal(0.0),
                            cost = costText.parseDecimal(0.0)
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
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
                    onValueChange = { maxWeightText = it.cleanNumeric() },
                    label = { Text("Peso máximo (kg)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.cleanNumeric() },
                    label = { Text("Costo envío") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PricingMlShippingTierEntity(
                            id = initial?.id ?: 0,
                            maxWeightKg = maxWeightText.parseDecimal(0.0),
                            cost = costText.parseDecimal(0.0)
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
