package com.yumzy.easyadmin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yumzy.easyadmin.screens.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Orders : Screen("orders")
    object MiniRestaurants : Screen("mini_restaurants")
    object Store : Screen("store")
    object Analytics : Screen("analytics")

    // UPDATED: Route now carries startDateMillis and endDateMillis (full epoch ms range)
    // startTimeMillis and endTimeMillis are gone — time filtering is baked into the range.
    object RiderDetails : Screen("rider_details/{riderId}/{riderName}/{startDateMillis}/{endDateMillis}") {
        fun createRoute(
            riderId: String,
            riderName: String,
            startDateMillis: Long,
            endDateMillis: Long
        ): String {
            return "rider_details/$riderId/$riderName/$startDateMillis/$endDateMillis"
        }
    }

    // --- RESTAURANT ANALYTICS SCREEN ---
    object RestaurantAnalytics : Screen("restaurant_analytics/{miniResId}/{miniResName}") {
        fun createRoute(miniResId: String, miniResName: String): String {
            val encodedName = URLEncoder.encode(miniResName, StandardCharsets.UTF_8.toString())
            return "restaurant_analytics/$miniResId/$encodedName"
        }
    }

    // --- SUB-CATEGORY AND ITEMS SCREENS ---
    object MiniResSubCategories : Screen("mini_res_sub_categories/{miniResId}/{miniResName}/{parentCatId}") {
        fun createRoute(miniResId: String, miniResName: String, parentCatId: String): String {
            val encodedName = URLEncoder.encode(miniResName, StandardCharsets.UTF_8.toString())
            return "mini_res_sub_categories/$miniResId/$encodedName/$parentCatId"
        }
    }

    object MiniResItems : Screen("mini_res_items/{miniResId}/{subCategoryName}") {
        fun createRoute(miniResId: String, subCategoryName: String): String {
            val encodedSubCatName = URLEncoder.encode(subCategoryName, StandardCharsets.UTF_8.toString())
            return "mini_res_items/$miniResId/$encodedSubCatName"
        }
    }
}

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem(Screen.Orders, "Orders", Icons.Default.List),
        BottomNavItem(Screen.MiniRestaurants, "Restaurants", Icons.Default.Restaurant),
        BottomNavItem(Screen.Store, "Store", Icons.Default.ShoppingCart),
        BottomNavItem(Screen.Analytics, "Analytics", Icons.Default.Analytics)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Orders.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Orders.route) { LiveOrdersScreen() }
            composable(Screen.MiniRestaurants.route) { CategoryManagementScreen(navController = navController) }
            composable(Screen.Store.route) { StoreManagementScreen(navController = navController) }
            composable(Screen.Analytics.route) { AnalyticsScreen(navController = navController) }

            // UPDATED: Now uses startDateMillis + endDateMillis only
            composable(
                route = Screen.RiderDetails.route,
                arguments = listOf(
                    navArgument("riderId") { type = NavType.StringType },
                    navArgument("riderName") { type = NavType.StringType },
                    navArgument("startDateMillis") { type = NavType.LongType },
                    navArgument("endDateMillis") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val riderId = backStackEntry.arguments?.getString("riderId") ?: ""
                val riderName = backStackEntry.arguments?.getString("riderName") ?: ""
                val startDateMillis = backStackEntry.arguments?.getLong("startDateMillis") ?: 0L
                val endDateMillis = backStackEntry.arguments?.getLong("endDateMillis") ?: 0L

                RiderDetailsScreen(
                    riderId = riderId,
                    riderName = riderName,
                    startDateMillis = startDateMillis,
                    endDateMillis = endDateMillis,
                    navController = navController
                )
            }

            // --- RESTAURANT ANALYTICS SCREEN ---
            composable(
                route = Screen.RestaurantAnalytics.route,
                arguments = listOf(
                    navArgument("miniResId") { type = NavType.StringType },
                    navArgument("miniResName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val miniResId = backStackEntry.arguments?.getString("miniResId") ?: ""
                val encodedName = backStackEntry.arguments?.getString("miniResName") ?: ""
                val miniResName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())
                RestaurantAnalyticsScreen(
                    miniResId = miniResId,
                    miniResName = miniResName,
                    navController = navController
                )
            }

            // --- SUB-CATEGORY SCREEN ---
            composable(
                route = Screen.MiniResSubCategories.route,
                arguments = listOf(
                    navArgument("miniResId") { type = NavType.StringType },
                    navArgument("miniResName") { type = NavType.StringType },
                    navArgument("parentCatId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val miniResId = backStackEntry.arguments?.getString("miniResId") ?: ""
                val encodedName = backStackEntry.arguments?.getString("miniResName") ?: ""
                val miniResName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())
                val parentCatId = backStackEntry.arguments?.getString("parentCatId") ?: ""
                MiniRestaurantSubCategoryScreen(
                    miniResId = miniResId,
                    miniResName = miniResName,
                    parentCategoryId = parentCatId,
                    navController = navController
                )
            }

            // --- ITEMS SCREEN ---
            composable(
                route = Screen.MiniResItems.route,
                arguments = listOf(
                    navArgument("miniResId") { type = NavType.StringType },
                    navArgument("subCategoryName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val miniResId = backStackEntry.arguments?.getString("miniResId") ?: ""
                val encodedSubCatName = backStackEntry.arguments?.getString("subCategoryName") ?: ""
                val subCategoryName = URLDecoder.decode(encodedSubCatName, StandardCharsets.UTF_8.toString())
                MiniRestaurantItemListScreen(
                    miniResId = miniResId,
                    subCategoryName = subCategoryName,
                    navController = navController
                )
            }
        }
    }
}