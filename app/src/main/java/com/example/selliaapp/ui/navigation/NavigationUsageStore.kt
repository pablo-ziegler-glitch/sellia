package com.example.selliaapp.ui.navigation

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.example.selliaapp.domain.security.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationUsageStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val countsState = MutableStateFlow(loadRouteCounts())

    private var pendingNavigation: PendingNavigation? = null

    fun observeRouteCounts(): StateFlow<Map<String, Int>> = countsState.asStateFlow()

    fun routeCountsFor(role: AppRole): Map<String, Int> {
        val prefix = "${role.raw}|"
        return countsState.value
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
    }

    fun markNavigationRequested(role: AppRole, route: String) {
        pendingNavigation = PendingNavigation(role = role.raw, route = route, startedAtMs = SystemClock.elapsedRealtime())
    }

    fun onRouteVisible(role: AppRole, route: String) {
        incrementCount(role = role, route = route)
        val pending = pendingNavigation
        if (pending != null && pending.route == route && pending.role == role.raw) {
            val duration = (SystemClock.elapsedRealtime() - pending.startedAtMs).coerceAtLeast(0)
            recordOutcome(role = role, route = route, elapsedMs = duration, success = true)
            pendingNavigation = null
        }
    }

    fun recordNavigationDenied(role: AppRole, route: String) {
        recordOutcome(role = role, route = route, elapsedMs = 0L, success = false)
    }

    fun usabilityMetrics(role: AppRole, routes: List<String>): List<FlowUsabilityMetrics> {
        return routes.map { route ->
            val attempts = prefs.getInt(statsKey(role, route, "attempts"), 0).coerceAtLeast(1)
            val totalTime = prefs.getLong(statsKey(role, route, "total_time"), 0L)
            val errors = prefs.getInt(statsKey(role, route, "errors"), 0)
            FlowUsabilityMetrics(
                route = route,
                averageTaskTimeMs = totalTime / attempts,
                errorRate = errors.toDouble() / attempts
            )
        }
    }

    private fun incrementCount(role: AppRole, route: String) {
        val key = routeCountKey(role = role, route = route)
        val updated = countsState.value.toMutableMap()
        updated[key] = (updated[key] ?: 0) + 1
        countsState.value = updated
        prefs.edit().putInt(key, updated[key] ?: 1).apply()
    }

    private fun recordOutcome(role: AppRole, route: String, elapsedMs: Long, success: Boolean) {
        val attemptsKey = statsKey(role, route, "attempts")
        val totalTimeKey = statsKey(role, route, "total_time")
        val errorsKey = statsKey(role, route, "errors")
        val attempts = prefs.getInt(attemptsKey, 0) + 1
        val total = prefs.getLong(totalTimeKey, 0L) + elapsedMs
        val errors = prefs.getInt(errorsKey, 0) + if (success) 0 else 1
        prefs.edit()
            .putInt(attemptsKey, attempts)
            .putLong(totalTimeKey, total)
            .putInt(errorsKey, errors)
            .apply()
    }

    private fun loadRouteCounts(): Map<String, Int> {
        return prefs.all
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .mapNotNull { (key, value) ->
                (value as? Int)?.let { key to it }
            }
            .toMap()
    }

    private fun routeCountKey(role: AppRole, route: String): String = "$KEY_PREFIX${role.raw}|$route"

    private fun statsKey(role: AppRole, route: String, metric: String): String =
        "$STATS_PREFIX${role.raw}|$route|$metric"

    private data class PendingNavigation(val role: String, val route: String, val startedAtMs: Long)

    private companion object {
        const val PREFS_NAME = "navigation_usage_store"
        const val KEY_PREFIX = "nav_count|"
        const val STATS_PREFIX = "nav_stats|"
    }
}
