package com.yumzy.easyadmin.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Data classes for restaurant analytics
data class RestaurantItemStats(
    val itemName: String,
    val totalQuantity: Int,
    val totalRevenue: Double,
    val pendingCount: Int,
    val acceptedCount: Int,
    val deliveredCount: Int
)

data class RestaurantOrderSummary(
    val totalOrders: Int,
    val totalItems: Int,
    val totalRevenue: Double,
    val pendingOrders: Int,
    val acceptedOrders: Int,
    val deliveredOrders: Int
)

data class RestaurantOrder(
    val id: String = "",
    val items: List<Map<String, Any>> = emptyList(),
    val orderStatus: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantAnalyticsScreen(
    miniResId: String,
    miniResName: String,
    navController: NavController
) {
    var itemStats by remember { mutableStateOf<List<RestaurantItemStats>>(emptyList()) }
    var orderSummary by remember { mutableStateOf(RestaurantOrderSummary(0, 0, 0.0, 0, 0, 0)) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var startTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun fetchRestaurantAnalytics() {
        isLoading = true
        coroutineScope.launch {
            try {
                val db = Firebase.firestore

                // Fetch all orders with valid statuses
                val snapshot = db.collection("orders")
                    .whereIn("orderStatus", listOf("Pending", "Accepted", "Preparing", "On the way", "Delivered"))
                    .get()
                    .await()
                val allOrders = snapshot.documents.mapNotNull { doc ->
                    try {
                        RestaurantOrder(
                            id = doc.id,
                            items = doc.get("items") as? List<Map<String, Any>> ?: emptyList(),
                            orderStatus = doc.getString("orderStatus") ?: "",
                            createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Apply date filter
                val dateFilteredOrders = if (selectedDate != null) {
                    val zoneId = ZoneId.systemDefault()
                    val startOfDay = selectedDate!!.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = selectedDate!!.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

                    allOrders.filter { order ->
                        val orderTime = order.createdAt.toDate().time
                        orderTime >= startOfDay && orderTime < endOfDay
                    }
                } else {
                    allOrders
                }

                // Apply time filter
                val timeFilteredOrders = dateFilteredOrders.filter { order ->
                    if (startTime != null || endTime != null) {
                        val orderDateTime = order.createdAt.toDate()
                        val calendar = Calendar.getInstance().apply { time = orderDateTime }
                        val orderHour = calendar.get(Calendar.HOUR_OF_DAY)
                        val orderMinute = calendar.get(Calendar.MINUTE)
                        val orderTimeInMinutes = orderHour * 60 + orderMinute

                        val startTimeInMinutes = startTime?.let { it.first * 60 + it.second } ?: 0
                        val endTimeInMinutes = endTime?.let { it.first * 60 + it.second } ?: (23 * 60 + 59)

                        orderTimeInMinutes >= startTimeInMinutes && orderTimeInMinutes <= endTimeInMinutes
                    } else true
                }

                // Filter orders that contain items from this restaurant
                val restaurantOrders = timeFilteredOrders.filter { order ->
                    order.items.any { item ->
                        val itemMiniResName = item["miniResName"] as? String ?: ""
                        itemMiniResName.equals(miniResName, ignoreCase = true)
                    }
                }

                // Apply status filter
                val statusFilteredOrders = if (selectedStatus != null) {
                    restaurantOrders.filter { it.orderStatus == selectedStatus }
                } else {
                    restaurantOrders
                }

                // Calculate item statistics
                val itemStatsMap = mutableMapOf<String, MutableMap<String, Any>>()

                statusFilteredOrders.forEach { order ->
                    order.items.forEach { item ->
                        val itemMiniResName = item["miniResName"] as? String ?: ""

                        if (itemMiniResName.equals(miniResName, ignoreCase = true)) {
                            val itemName = item["itemName"] as? String ?: "Unknown Item"
                            val quantity = (item["quantity"] as? Number)?.toInt() ?: 0
                            val price = (item["price"] as? Number)?.toDouble() ?: 0.0
                            val itemTotal = price * quantity

                            if (!itemStatsMap.containsKey(itemName)) {
                                itemStatsMap[itemName] = mutableMapOf(
                                    "totalQuantity" to 0,
                                    "totalRevenue" to 0.0,
                                    "pendingCount" to 0,
                                    "acceptedCount" to 0,
                                    "deliveredCount" to 0
                                )
                            }

                            val stats = itemStatsMap[itemName]!!
                            stats["totalQuantity"] = (stats["totalQuantity"] as Int) + quantity
                            stats["totalRevenue"] = (stats["totalRevenue"] as Double) + itemTotal

                            when (order.orderStatus) {
                                "Pending" -> stats["pendingCount"] = (stats["pendingCount"] as Int) + quantity
                                "Delivered" -> stats["deliveredCount"] = (stats["deliveredCount"] as Int) + quantity
                                "Accepted", "Preparing", "On the way" ->
                                    stats["acceptedCount"] = (stats["acceptedCount"] as Int) + quantity
                            }
                        }
                    }
                }

                itemStats = itemStatsMap.map { (itemName, stats) ->
                    RestaurantItemStats(
                        itemName = itemName,
                        totalQuantity = stats["totalQuantity"] as Int,
                        totalRevenue = stats["totalRevenue"] as Double,
                        pendingCount = stats["pendingCount"] as Int,
                        acceptedCount = stats["acceptedCount"] as Int,
                        deliveredCount = stats["deliveredCount"] as Int
                    )
                }.sortedByDescending { it.totalRevenue }

                // Calculate order summary
                val totalItems = itemStats.sumOf { it.totalQuantity }
                val totalRevenue = itemStats.sumOf { it.totalRevenue }
                val pendingOrders = statusFilteredOrders.count { it.orderStatus == "Pending" }
                val acceptedOrders = statusFilteredOrders.count {
                    it.orderStatus in listOf("Accepted", "Preparing", "On the way")
                }
                val deliveredOrders = statusFilteredOrders.count { it.orderStatus == "Delivered" }

                orderSummary = RestaurantOrderSummary(
                    totalOrders = statusFilteredOrders.size,
                    totalItems = totalItems,
                    totalRevenue = totalRevenue,
                    pendingOrders = pendingOrders,
                    acceptedOrders = acceptedOrders,
                    deliveredOrders = deliveredOrders
                )

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedDate, startTime, endTime, selectedStatus) {
        fetchRestaurantAnalytics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(miniResName)
                        Text(
                            "Restaurant Analytics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filters Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Date Filter
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        selectedDate?.format(DateTimeFormatter.ofPattern("dd MMM, yyyy"))
                            ?: "Select Date (Optional)"
                    )
                }

                // Time Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            startTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" }
                                ?: "Start Time",
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            endTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" }
                                ?: "End Time",
                            fontSize = 12.sp
                        )
                    }
                }

                // Status Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedStatus == "Pending",
                        onClick = { selectedStatus = if (selectedStatus == "Pending") null else "Pending" },
                        label = { Text("Pending") }
                    )
                    FilterChip(
                        selected = selectedStatus == "Accepted",
                        onClick = { selectedStatus = if (selectedStatus == "Accepted") null else "Accepted" },
                        label = { Text("Accepted") }
                    )
                    FilterChip(
                        selected = selectedStatus == "Delivered",
                        onClick = { selectedStatus = if (selectedStatus == "Delivered") null else "Delivered" },
                        label = { Text("Delivered") }
                    )
                }

                // Clear Filters
                if (selectedDate != null || startTime != null || endTime != null || selectedStatus != null) {
                    TextButton(
                        onClick = {
                            selectedDate = null
                            startTime = null
                            endTime = null
                            selectedStatus = null
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All Filters")
                    }
                }
            }

            HorizontalDivider()

            // Summary Stats
            RestaurantSummaryCard(orderSummary = orderSummary)

            HorizontalDivider()

            // Items List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (itemStats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = "No data",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No orders found for this restaurant",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Item Breakdown (${itemStats.size} items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    items(itemStats) { stat ->
                        RestaurantItemCard(stat)
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Pickers
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startTime?.first ?: 0,
            initialMinute = startTime?.second ?: 0,
            is24Hour = true
        )
        RestaurantTimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startTime = Pair(timePickerState.hour, timePickerState.minute)
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endTime?.first ?: 23,
            initialMinute = endTime?.second ?: 59,
            is24Hour = true
        )
        RestaurantTimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endTime = Pair(timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}



// Composable functions must be defined outside the main function
@Composable
fun RestaurantSummaryCard(orderSummary: RestaurantOrderSummary) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RestaurantSummaryStatColumn("Orders", orderSummary.totalOrders.toString(), Color(0xFF9C27B0))
                RestaurantSummaryStatColumn("Items Sold", orderSummary.totalItems.toString(), Color(0xFF2196F3))
                RestaurantSummaryStatColumn("Revenue", "৳${"%.2f".format(orderSummary.totalRevenue)}", Color(0xFF4CAF50))
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RestaurantSummaryStatColumn("Pending", orderSummary.pendingOrders.toString(), Color(0xFFFF9800))
                RestaurantSummaryStatColumn("Accepted", orderSummary.acceptedOrders.toString(), Color(0xFF2196F3))
                RestaurantSummaryStatColumn("Delivered", orderSummary.deliveredOrders.toString(), Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
fun RestaurantSummaryStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RestaurantItemCard(stat: RestaurantItemStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Item Name and Total Revenue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "৳${"%.2f".format(stat.totalRevenue)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Total Quantity: ${stat.totalQuantity}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Status Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RestaurantStatusBadge("Pending", stat.pendingCount, Color(0xFFFF9800))
                RestaurantStatusBadge("Accepted", stat.acceptedCount, Color(0xFF2196F3))
                RestaurantStatusBadge("Delivered", stat.deliveredCount, Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
fun RestaurantStatusBadge(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
fun RestaurantTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}