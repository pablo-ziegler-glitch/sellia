package com.example.selliaapp.ui.navigation

import com.example.selliaapp.domain.security.AppRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleNavigationPolicyTest {

    @Test
    fun `owner menu promotes most used routes`() {
        val result = RoleNavigationPolicy.primaryRoutesForRole(
            role = AppRole.OWNER,
            usageByRoute = mapOf(
                Routes.Cash.route to 30,
                Routes.Stock.route to 20,
                Routes.ClientsHub.route to 10,
                Routes.Pos.route to 5
            )
        )

        assertEquals(
            listOf(Routes.Home.route, Routes.Cash.route, Routes.Stock.route, Routes.ClientsHub.route),
            result
        )
    }

    @Test
    fun `cashier menu keeps three primary flows`() {
        val result = RoleNavigationPolicy.primaryRoutesForRole(
            role = AppRole.CASHIER,
            usageByRoute = mapOf(Routes.Stock.route to 12, Routes.Pos.route to 40)
        )

        assertEquals(
            listOf(Routes.Home.route, Routes.Pos.route, Routes.Stock.route),
            result
        )
    }

    @Test
    fun `usability ranking prioritizes lower error rate then time`() {
        val ranking = FlowUsabilityEvaluator.rankByUsability(
            listOf(
                FlowUsabilityMetrics(Routes.Stock.route, averageTaskTimeMs = 3_000, errorRate = 0.05),
                FlowUsabilityMetrics(Routes.Cash.route, averageTaskTimeMs = 4_000, errorRate = 0.00),
                FlowUsabilityMetrics(Routes.Pos.route, averageTaskTimeMs = 2_000, errorRate = 0.00)
            )
        )

        assertEquals(
            listOf(Routes.Pos.route, Routes.Cash.route, Routes.Stock.route),
            ranking.map { it.route }
        )
    }
}
