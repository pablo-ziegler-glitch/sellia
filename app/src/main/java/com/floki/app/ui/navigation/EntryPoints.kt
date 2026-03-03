package com.floki.app.ui.navigation


import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import com.floki.app.repository.ProviderRepository
import com.floki.app.repository.ProviderInvoiceRepository
import com.floki.app.repository.ExpenseRepository

@HiltViewModel
class ProvidersEntryPoint @Inject constructor(
    val repo: ProviderRepository
): ViewModel()

@HiltViewModel
class ProviderInvoicesEntryPoint @Inject constructor(
    val repo: ProviderInvoiceRepository
): ViewModel()

@HiltViewModel
class ExpensesEntryPoint @Inject constructor(
    val repo: ExpenseRepository
): ViewModel()
