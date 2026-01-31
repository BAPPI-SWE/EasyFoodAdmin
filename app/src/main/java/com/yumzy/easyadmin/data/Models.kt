package com.yumzy.easyadmin.data

// This file is the single source of truth for our shared data blueprints.

data class MainCategory(
    val id: String = "",
    val name: String = ""
)

data class StoreSubCategory(
    val id: String = "",
    val name: String = "",
    val parentCategory: String = "",
    val imageUrl: String? = null,
    val availableLocations: List<String> = emptyList(),
    val priority: Int = 0
)

data class GroupedCategory(
    val mainCategory: MainCategory,
    val subCategories: List<StoreSubCategory>
)

data class MiniRestaurant(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val open: String = "yes", // "yes" or "no"
    val parentCategory: String = "",
    val availableLocations: List<String> = emptyList(),
    val password: String = "",
    val priority: Int = 0
)

// UPDATED: Added priority to StoreItem
data class StoreItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val itemDescription: String = "",
    val subCategory: String = "",
    val miniRes: String = "", // The ID of the mini restaurant
    val additionalDeliveryCharge: Double = 0.0,
    val additionalServiceCharge: Double = 0.0,
    val stock: String = "yes", // "yes" or "no"
    val priority: Int = 0 // Added Priority
)