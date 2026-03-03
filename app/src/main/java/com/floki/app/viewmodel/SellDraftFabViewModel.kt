package com.floki.app.viewmodel

import androidx.lifecycle.ViewModel
import com.floki.app.repository.SellDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SellDraftFabViewModel @Inject constructor(
    private val sellDraftRepository: SellDraftRepository
) : ViewModel() {
    val hasActiveDraft: StateFlow<Boolean> = sellDraftRepository.hasActiveDraft
}
