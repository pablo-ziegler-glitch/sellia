package com.example.selliaapp.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.model.ExpenseRecord
import com.example.selliaapp.data.model.ExpenseStatus
import com.example.selliaapp.data.model.ExpenseTemplate
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.ui.components.SyncPendingBanner
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

@Composable
fun ExpensesHubScreen(
    repo: ExpenseRepository,
    onTemplates: () -> Unit,
    onEntries: () -> Unit,
    onCashflow: () -> Unit,
    onBack: () -> Unit
) {
    val currentMonth = remember { LocalDate.now().monthValue }
    val currentYear = remember { LocalDate.now().year }
    val budgets by repo.observeBudgets(currentMonth, currentYear)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val records by repo.observeRecords(
        name = null,
        month = currentMonth,
        year = currentYear,
        status = null
    ).collectAsStateWithLifecycle(initialValue = emptyList())
    val templates by repo.observeTemplates().collectAsStateWithLifecycle(initialValue = emptyList())

    val usageByCategory = remember(records) {
        records.groupBy { it.categorySnapshot }
            .mapValues { (_, rows) -> rows.sumOf(ExpenseRecord::amount) }
    }

    val frequentTemplates = remember(templates) {
        templates
            .sortedWith(compareByDescending<ExpenseTemplate> { it.required }.thenBy { it.name.lowercase() })
            .filter { it.required || (it.defaultAmount ?: 0.0) > 0.0 }
            .take(4)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { BackTopAppBar(title = "Gastos", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SyncPendingBanner()

            Text(
                text = "Progreso mensual por categoría",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (budgets.isEmpty()) {
                Text(
                    text = "Todavía no cargaste presupuestos para este mes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                budgets.forEach { budget ->
                    val used = usageByCategory[budget.category] ?: 0.0
                    val ratio = if (budget.amount <= 0.0) 1f else (used / budget.amount).toFloat()
                    val boundedProgress = ratio.coerceIn(0f, 1f)
                    val isWarning = ratio >= 0.8f

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(budget.category, style = MaterialTheme.typography.titleSmall)
                                if (isWarning) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = "Presupuesto al 80% o más",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = if (ratio >= 1f) "Presupuesto superado" else "Cerca del límite",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }

                            LinearProgressIndicator(
                                progress = { boundedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = if (ratio >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            val available = max(0.0, budget.amount - used)
                            Text(
                                text = "Usado $${"%.2f".format(used)} · Disponible $${"%.2f".format(available)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (frequentTemplates.isNotEmpty()) {
                Text(
                    text = "Carga rápida (frecuentes)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                frequentTemplates.forEach { template ->
                    val amount = template.defaultAmount ?: 0.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable {
                                scope.launch {
                                    repo.upsertRecord(
                                        ExpenseRecord(
                                            templateId = template.id,
                                            nameSnapshot = template.name,
                                            categorySnapshot = template.category,
                                            amount = amount,
                                            month = currentMonth,
                                            year = currentYear,
                                            status = ExpenseStatus.IMPAGO
                                        )
                                    )
                                    snackbarHostState.showSnackbar("Gasto rápido creado: ${template.name}")
                                }
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(template.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = template.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$${"%.2f".format(amount)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (amount <= 0.0) Color.Unspecified else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Button(onClick = onTemplates) { Text("ABM de Tipos de Gasto") }
            Button(onClick = onEntries) { Text("Carga/Listado de Gastos") }
            Button(onClick = onCashflow) { Text("Cashflow (Ventas + Gastos + Proveedores)") }
        }
    }
}
