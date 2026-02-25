package com.example.selliaapp.ui.navigation

import com.example.selliaapp.domain.security.AppRole

object RoleNavigationPolicy {

    fun primaryRoutesForRole(role: AppRole, usageByRoute: Map<String, Int>): List<String> {
        return when (role) {
            AppRole.ADMIN, AppRole.OWNER, AppRole.MANAGER -> {
                listOf(Routes.Home.route) + pickTopRoutes(
                    usageByRoute = usageByRoute,
                    candidates = listOf(
                        Routes.Pos.route,
                        Routes.ClientsHub.route,
                        Routes.Stock.route,
                        Routes.Cash.route
                    ),
                    max = 3
                )
            }

            AppRole.CASHIER -> {
                listOf(Routes.Home.route) + pickTopRoutes(
                    usageByRoute = usageByRoute,
                    candidates = listOf(
                        Routes.Pos.route,
                        Routes.Cash.route,
                        Routes.Stock.route
                    ),
                    max = 2
                )
            }

            AppRole.VIEWER -> listOf(Routes.Home.route, Routes.PublicProductCatalog.route)
        }
    }

    private fun pickTopRoutes(
        usageByRoute: Map<String, Int>,
        candidates: List<String>,
        max: Int
    ): List<String> {
        return candidates
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<String>> { usageByRoute[it.value] ?: 0 }
                    .thenBy { it.index }
            )
            .take(max)
            .map { it.value }
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
