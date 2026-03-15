package com.example.selliaapp.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.example.selliaapp.pricing.PricingResult
import com.example.selliaapp.viewmodel.PricingSimulatorViewModel
import java.text.NumberFormat
import java.util.Locale

private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

private enum class SimulatorSection(val label: String) {
    RESULTADO("Resultado"),
    VARIABLES("Variables")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingSimulatorScreen(
    onBack: () -> Unit,
    viewModel: PricingSimulatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var purchasePriceText by remember { mutableStateOf("") }
    var activeSection by remember { mutableStateOf(SimulatorSection.RESULTADO) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simulador de costos") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } },
                actions = {
                    IconButton(onClick = {
                        viewModel.resetSettings()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restaurar valores reales")
                    }
                }
            )
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
                    text = "Simulador de costos y precios",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Probá con distintos precios de compra y ajustá variables sin afectar tu configuración real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = purchasePriceText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }
                        purchasePriceText = cleaned
                        val parsed = cleaned.replace(',', '.').toDoubleOrNull() ?: 0.0
                        viewModel.setPurchasePrice(parsed)
                    },
                    label = { Text("Precio de compra (adquisición)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimulatorSection.entries.forEach { section ->
                        androidx.compose.material3.FilterChip(
                            selected = activeSection == section,
                            onClick = { activeSection = section },
                            label = { Text(section.label) }
                        )
                    }
                }
            }

            when (activeSection) {
                SimulatorSection.RESULTADO -> {
                    item {
                        if (state.result != null) {
                            ResultBreakdownCard(
                                purchasePrice = state.purchasePrice,
                                settings = state.settings!!,
                                result = state.result!!
                            )
                        } else if (state.purchasePrice <= 0 && state.loaded) {
                            SimCard("Resultado") {
                                Text(
                                    "Ingresá un precio de compra para ver el desglose.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                SimulatorSection.VARIABLES -> {
                    item {
                        if (state.settings != null) {
                            SettingsOverrideCard(
                                settings = state.settings!!,
                                onUpdate = { viewModel.updateSettings(it) }
                            )
                        } else if (!state.loaded) {
                            SimCard("Cargando...") {
                                Text("Cargando configuración actual...")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ResultBreakdownCard(
    purchasePrice: Double,
    settings: PricingSettingsEntity,
    result: PricingResult
) {
    SimCard("Desglose del cálculo") {
        // ── Variables locales para el desglose (espejo del PricingCalculator) ──
        val targetMargin = settings.gainTargetPercent
        val posnetPct = settings.posnet3CuotasPercent
        val retencion = settings.transferenciaRetencionPercent
        // Nueva fórmula: margen sobre (compra + operativos), costo fijo se suma después, luego IVA producto
        val baseForMargin = purchasePrice + result.operativosAmount
        val baseWithProfit = baseForMargin * (1 + targetMargin / 100.0)
        val withFixed = baseWithProfit + result.fixedCostImputed
        val withIva = withFixed * (1 + settings.ivaProductPercent / 100.0)
        // ──────────────────────────────────────────────────────────────────────

        SectionHeader("Datos de entrada")
        ResultRow("Precio de compra", purchasePrice)
        ResultRow("Costo fijo unitario (total÷ventas est.)", result.fixedCostUnit)
        val imputationMode = settings.fixedCostImputationMode.trim().uppercase()
        val modeLabel = if (imputationMode == PricingSettingsEntity.FixedCostImputationMode.BY_PRICE_RANGE)
            "Por rango de precio" else "Total a todos los productos"
        ResultRow("Modo de imputación de costos fijos", modeLabel, isPercent = false)
        if (imputationMode == PricingSettingsEntity.FixedCostImputationMode.BY_PRICE_RANGE) {
            IvaInfoRow("Se aplicó un coeficiente de rango sobre el costo unitario según el precio de compra.")
        }
        ResultRow("Costo fijo imputado (usado en fórmula)", result.fixedCostImputed)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader("IVA y costos de terminal")
        ResultRow("IVA terminal sobre costos fijos (%)", "${settings.ivaTerminalPercent}%", isPercent = true)
        IvaInfoRow("Solo aplica a costos fijos con IVA habilitado (ej: comisión de terminal). Ya incluido en el costo fijo imputado.")
        ResultRow("IVA producto / venta (%)", "${settings.ivaProductPercent}%", isPercent = true)
        IvaInfoRow("Se aplica al subtotal (③+④) antes del recargo de cuotas. Es el IVA que el consumidor paga sobre el precio de venta.")

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader("Fórmula paso a paso")
        ResultRow("① Compra", purchasePrice)
        ResultRow("② Operativos (${settings.operativosLocalPercent}% s/compra)", result.operativosAmount)
        ResultRow("③ (①+②) × (1+${targetMargin}%) = base con margen", baseWithProfit)
        ResultRow("④ + Costo fijo imputado", result.fixedCostImputed)
        ResultRow("⑤ Subtotal (③+④)", withFixed)
        ResultRow("⑥ + IVA producto (${settings.ivaProductPercent}%)", withFixed * (settings.ivaProductPercent / 100.0))
        ResultRow("⑦ Subtotal con IVA (⑤×(1+IVA))", withIva)
        ResultRow("⑧ + Recargo Posnet 3 cuotas (${posnetPct}% s/⑦)", withIva * (posnetPct / 100.0))
        IvaInfoRow("Precios finales se redondean al escalón (≤1500→50, ≤3000→100, ≤5000→200, +5000→500)")
        ResultRow("Base de costo total (compra + costo fijo + operativos)", result.costBase)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader("Precio Lista (3 cuotas sin interés)")
        IvaInfoRow("Precio al consumidor. Incluye IVA producto y recargo Posnet.")
        HighlightRow(
            label = "Precio LISTA  (⑦+⑧, redondeado)",
            value = result.listPrice,
            badge = "IVA producto incluido · Incluye recargo Posnet"
        )
        ResultRow("  Costo cobro posnet (${settings.posnetListaCostPercent}% + IVA ${settings.ivaTerminalPercent}%)", result.listPaymentCost)
        NetGainRow("  Ganancia neta LISTA", result.listNetGain)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader("Precio Efectivo")
        IvaInfoRow("Precio al consumidor. Incluye IVA producto, sin recargo de cuotas.")
        HighlightRow(
            label = "Precio EFECTIVO  (⑦, redondeado)",
            value = result.cashPrice,
            badge = "IVA producto incluido · Sin recargo Posnet"
        )
        ResultRow("  Costo cobro al momento (${settings.cobroEnMomentoCostPercent}% + IVA ${settings.ivaTerminalPercent}%)", result.cashPaymentCost)
        NetGainRow("  Ganancia neta EFECTIVO", result.cashNetGain)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader("Precio Transferencia")
        IvaInfoRow("Precio al consumidor. Incluye IVA producto, sin recargo de cuotas.")
        HighlightRow(
            label = "Precio TRANSFERENCIA  (⑦, redondeado)",
            value = result.transferPrice,
            badge = "IVA producto incluido · Sin recargo Posnet"
        )
        ResultRow("  ↳ Retención transf. (${retencion}%)", result.transferPrice - result.transferNetPrice)
        ResultRow("  ↳ Neto que ingresa a tu cuenta", result.transferNetPrice)
        ResultRow("  Costo cobro al momento (${settings.cobroEnMomentoCostPercent}% + IVA ${settings.ivaTerminalPercent}%)", result.transferPaymentCost)
        NetGainRow("  Ganancia neta TRANSFERENCIA", result.transferNetGain)

        if (result.mlPrice != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("Precios Mercado Libre / Pago")
            IvaInfoRow("Precio publicado en ML. La plataforma ya descuenta comisión y cuotas. Sin IVA extra al comprador.")
            HighlightRow("ML sin cuotas", result.mlPrice, badge = "Sin IVA extra al comprador")
            if (result.ml3cPrice != null) {
                HighlightRow("ML 3 cuotas", result.ml3cPrice, badge = "Sin IVA extra al comprador")
            }
            if (result.ml6cPrice != null) {
                HighlightRow("ML 6 cuotas", result.ml6cPrice, badge = "Sin IVA extra al comprador")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionHeader("Resumen de ganancias netas")
        IvaInfoRow("Base de costo = compra + costo fijo imputado + operativos")
        NetGainRow("Ganancia neta LISTA", result.listNetGain)
        NetGainRow("Ganancia neta EFECTIVO", result.cashNetGain)
        NetGainRow("Ganancia neta TRANSFERENCIA", result.transferNetGain)
        if (result.mlPrice != null) {
            val mlComm = result.mlPrice * (settings.mlCommissionPercent / 100.0)
            val mlNet = result.mlPrice - mlComm
            MarginRow("Margen ML sin cuotas (aprox.)", mlNet, result.costBase)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ResultRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(currencyFormat.format(value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResultRow(label: String, value: String, isPercent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HighlightRow(label: String, value: Double, badge: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Text(
            currencyFormat.format(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun IvaInfoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun MarginRow(label: String, salePrice: Double, costBase: Double) {
    val margin = if (costBase > 0) ((salePrice - costBase) / costBase * 100) else 0.0
    val profit = salePrice - costBase
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "${currencyFormat.format(profit)} (${String.format("%.1f", margin)}%)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (margin >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NetGainRow(label: String, netGain: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            currencyFormat.format(netGain),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (netGain >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SettingsOverrideCard(
    settings: PricingSettingsEntity,
    onUpdate: (PricingSettingsEntity) -> Unit
) {
    SimCard("Variables de prueba") {
        Text(
            "Modificá estas variables para simular. No afectan tu configuración real.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SimDecimalField("Ganancia objetivo (%)", settings.gainTargetPercent) {
            onUpdate(settings.copy(gainTargetPercent = it))
        }
        SimDecimalField("Operativos local (%)", settings.operativosLocalPercent) {
            onUpdate(settings.copy(operativosLocalPercent = it))
        }
        SimDecimalField("Posnet 3 cuotas - recargo (%)", settings.posnet3CuotasPercent) {
            onUpdate(settings.copy(posnet3CuotasPercent = it))
        }
        SimDecimalField("Costo cobro lista 3 cuotas s/IVA (%)", settings.posnetListaCostPercent) {
            onUpdate(settings.copy(posnetListaCostPercent = it))
        }
        SimDecimalField("Costo cobro al momento s/IVA (%)", settings.cobroEnMomentoCostPercent) {
            onUpdate(settings.copy(cobroEnMomentoCostPercent = it))
        }
        SimDecimalField("Retención transferencia (%)", settings.transferenciaRetencionPercent) {
            onUpdate(settings.copy(transferenciaRetencionPercent = it))
        }
        SimDecimalField("IVA terminal sobre costos fijos (%)", settings.ivaTerminalPercent) {
            onUpdate(settings.copy(ivaTerminalPercent = it))
        }
        SimDecimalField("IVA producto / venta (%)", settings.ivaProductPercent) {
            onUpdate(settings.copy(ivaProductPercent = it))
        }
        SimIntField("Ventas mensuales estimadas", settings.monthlySalesEstimate) {
            onUpdate(settings.copy(monthlySalesEstimate = it))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Mercado Libre", fontWeight = FontWeight.SemiBold)

        SimDecimalField("Comisión ML (%)", settings.mlCommissionPercent) {
            onUpdate(settings.copy(mlCommissionPercent = it))
        }
        SimDecimalField("ML 3 cuotas (%)", settings.mlCuotas3Percent) {
            onUpdate(settings.copy(mlCuotas3Percent = it))
        }
        SimDecimalField("ML 6 cuotas (%)", settings.mlCuotas6Percent) {
            onUpdate(settings.copy(mlCuotas6Percent = it))
        }
        SimDecimalField("Ganancia mínima ML", settings.mlGainMinimum) {
            onUpdate(settings.copy(mlGainMinimum = it))
        }
        SimDecimalField("Peso envío ML (kg)", settings.mlDefaultWeightKg) {
            onUpdate(settings.copy(mlDefaultWeightKg = it))
        }
    }
}

@Composable
private fun SimDecimalField(label: String, currentValue: Double, onValueChange: (Double) -> Unit) {
    var text by remember(currentValue) { mutableStateOf(currentValue.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }
            text = cleaned
            cleaned.replace(',', '.').toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun SimIntField(label: String, currentValue: Int, onValueChange: (Int) -> Unit) {
    var text by remember(currentValue) { mutableStateOf(currentValue.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() }
            text = cleaned
            cleaned.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun SimCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, fontWeight = FontWeight.Medium)
            content()
        }
    }
}
