package com.example.selliaapp.ui.navigation

import com.example.selliaapp.domain.security.AppRole

object RoleNavigationPolicy {

    fun primaryRoutesForRole(role: AppRole, usageByRoute: Map<String, Int>): List<String> {
        return listOf(
            Routes.Home.route,
            Routes.Pos.route,
            Routes.Stock.route
        )
    }
}


data class FlowUsabilityMetrics(
    val route: String,
    val averageTaskTimeMs: Long,
    val errorRate: Double
)

object FlowUsabilityEvaluator {
    fun rankByUsability(metrics: List<FlowUsabilityMetrics>): List<FlowUsabilityMetrics> {
        return metrics.sortedWith(
            compareBy<FlowUsabilityMetrics> { it.errorRate }
                .thenBy { it.averageTaskTimeMs }
                .thenBy { it.route }
        )
    }
}
