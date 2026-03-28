package com.example.selliaapp.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("home_onboarding", Context.MODE_PRIVATE)

    fun shouldShowContextualTips(): Boolean = prefs.getBoolean(KEY_SHOW_TIPS, true)

    fun markContextualTipsShown() {
        prefs.edit().putBoolean(KEY_SHOW_TIPS, false).apply()
    }

    companion object {
        private const val KEY_SHOW_TIPS = "show_contextual_tips"
    }
}
