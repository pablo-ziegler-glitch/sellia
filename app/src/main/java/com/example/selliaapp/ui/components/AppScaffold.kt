package com.example.selliaapp.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

@Immutable
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val highlighted: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    currentDestination: NavDestination?,
    onNavigate: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    navigationItems: List<BottomNavItem>,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                navigationItems.forEach { item ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == item.route }
                        ?: false
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onNavigate(item.route) },
                        icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = if (item.highlighted) {
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        } else {
                            NavigationBarItemDefaults.colors()
                        }
                    )
                }
            }
        }
    ) { padding ->
        content(padding)
    }
}
