package com.example.selliaapp.ui.screens.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.data.model.CashflowMonth
import com.example.selliaapp.repository.ExpenseRepository
import com.example.selliaapp.ui.components.BackTopAppBar

@Composable
fun ExpensesCashflowScreen(
    repo: ExpenseRepository,
    onBack: () -> Unit
) {
    var rows by remember { mutableStateOf<List<CashflowMonth>>(emptyList()) }

    LaunchedEffect(Unit) {
        rows = repo.getCashflowReport()
    }

    Scaffold(topBar = { BackTopAppBar(title = "Cashflow", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("${row.month}/${row.year}") },
                        supportingContent = {
                            Text(
                                "Ventas: ${"%.2f".format(row.salesTotal)}  •  " +
                                    "Gastos: ${"%.2f".format(row.expenseTotal)}  •  " +
                                    "Proveedores: ${"%.2f".format(row.providerTotal)}  •  " +
                                    "Neto: ${"%.2f".format(row.netTotal)}"
                            )
                        }
                    )
                }
            }
        }
    }
}
