package com.example.selliaapp.viewmodel.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.CashMovementEntity
import com.example.selliaapp.data.local.entity.CashMovementType
import com.example.selliaapp.repository.CashRepository
import com.example.selliaapp.repository.CashSessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CashViewModel @Inject constructor(
    private val cashRepository: CashRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CashUiState())
    val state: StateFlow<CashUiState> = _state.asStateFlow()

    init {
        observeCashSession()
    }

    private fun observeCashSession() {
        viewModelScope.launch {
            cashRepository.observeOpenSessionSummary().collect { summary ->
                _state.update { it.copy(summary = summary) }
            }
        }
    }

    fun openCash(openingAmount: Double, note: String?) {
        viewModelScope.launch {
            val session = cashRepository.openSession(openingAmount = openingAmount, note = note)
            _state.update { it.copy(summary = it.summary?.copy(session = session)) }
        }
    }

    fun addMovement(type: String, amount: Double, note: String?) {
        val sessionId = state.value.summary?.session?.id ?: return
        viewModelScope.launch {
            cashRepository.registerMovement(
                sessionId = sessionId,
                type = type,
                amount = amount,
                note = note
            )
        }
    }

    fun auditCash(countedAmount: Double, note: String?) {
        val sessionId = state.value.summary?.session?.id ?: return
        viewModelScope.launch {
            cashRepository.registerAudit(
                sessionId = sessionId,
                countedAmount = countedAmount,
                note = note
            )
        }
    }

    fun closeCash(closingAmount: Double?, note: String?) {
        val sessionId = state.value.summary?.session?.id ?: return
        viewModelScope.launch {
            cashRepository.closeSession(
                sessionId = sessionId,
                closingAmount = closingAmount,
                note = note
            )
        }
    }

    fun registerIncome(amount: Double, note: String?) {
        addMovement(CashMovementType.INCOME, amount, note)
    }

    fun registerExpense(amount: Double, note: String?) {
        addMovement(CashMovementType.EXPENSE, -amount, note)
    }

    fun registerAdjustment(amount: Double, note: String?) {
        addMovement(CashMovementType.ADJUSTMENT, amount, note)
    }
}

data class CashUiState(
    val summary: CashSessionSummary? = null
) {
    val hasOpenSession: Boolean
        get() = summary != null

    val movements: List<CashMovementEntity>
        get() = summary?.movements.orEmpty()
}
