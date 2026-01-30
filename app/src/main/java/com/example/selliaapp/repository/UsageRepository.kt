package com.example.selliaapp.repository

import com.example.selliaapp.data.model.usage.UsageDashboardSnapshot
import java.time.LocalDate

interface UsageRepository {
    suspend fun getUsageDashboard(from: LocalDate, to: LocalDate): UsageDashboardSnapshot
}
