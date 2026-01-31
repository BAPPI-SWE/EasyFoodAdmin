package com.yumzy.admin.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

// Enhanced order data class with phone number
data class AdminOrder(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userSubLocation: String = "",
    val riderName: String? = null,
    val orderStatus: String = "",
    val totalPrice: Double = 0.0,
    val createdAt: Timestamp = Timestamp.now(),
    val items: List<OrderItemDetail> = emptyList()
)

// UPDATED: Added miniResName and partnerStatus
data class OrderItemDetail(
    val name: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val miniResName: String? = null,
    val partnerStatus: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveOrdersScreen() {
    var allOrders by remember { mutableStateOf<List<AdminOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Filter states
    var selectedStatuses by remember { mutableStateOf(setOf<String>()) }
    var selectedSubLocations by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateFilter by remember { mutableStateOf(false) }

    // Date range filter
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    // Time range filter
    var startTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Available filter options
    val allStatuses = listOf("Pending", "Accepted", "Preparing", "On the way", "Delivered", "Rejected", "Cancelled")
    val allSubLocations = remember { mutableStateOf<List<String>>(emptyList()) }

    // Real-time listener
    LaunchedEffect(key1 = Unit) {
        Firebase.firestore.collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoading = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { doc ->
                        try {
                            val itemsData = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                            val orderItems = itemsData.map { itemMap ->
                                OrderItemDetail(
                                    name = itemMap["itemName"] as? String
                                        ?: itemMap["name"] as? String
                                        ?: "Unknown Item",
                                    quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0,
                                    price = (itemMap["itemPrice"] as? Number)?.toDouble()
                                        ?: (itemMap["price"] as? Number)?.toDouble()
                                        ?: 0.0,
                                    // Mapping new fields
                                    miniResName = itemMap["miniResName"] as? String,
                                    partnerStatus = itemMap["partnerStatus"] as? String
                                )
                            }

                            AdminOrder(
                                id = doc.id,
                                restaurantName = doc.getString("restaurantName") ?: "",
                                userName = doc.getString("userName") ?: "",
                                userPhone = doc.getString("userPhone") ?: "",
                                userSubLocation = doc.getString("userSubLocation") ?: "N/A",
                                riderName = doc.getString("riderName"),
                                orderStatus = doc.getString("orderStatus") ?: "",
                                totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                                items = orderItems
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    allOrders = orders

                    // Extract unique sub-locations
                    allSubLocations.value = orders.map { it.userSubLocation }
                        .filter { it.isNotBlank() && it != "N/A" }
                        .distinct()
                        .sorted()
                }
                isLoading = false
            }
    }

    // Apply filters
    val filteredOrders = remember(allOrders, selectedStatuses, selectedSubLocations, searchQuery, startDate, endDate, startTime, endTime) {
        allOrders.filter { order ->
            // Status filter
            val statusMatch = selectedStatuses.isEmpty() || selectedStatuses.contains(order.orderStatus)

            // Sub-location filter
            val locationMatch = selectedSubLocations.isEmpty() || selectedSubLocations.contains(order.userSubLocation)

            // Search filter
            val searchMatch = if (searchQuery.isBlank()) {
                true
            } else {
                order.items.any { item ->
                    item.name.contains(searchQuery, ignoreCase = true)
                } || order.restaurantName.contains(searchQuery, ignoreCase = true)
                        || order.userName.contains(searchQuery, ignoreCase = true)
            }

            // Date range filter with time
            val dateTimeMatch = if (startDate == null && endDate == null && startTime == null && endTime == null) {
                true
            } else {
                val orderDateTime = order.createdAt.toDate()
                val calendar = Calendar.getInstance().apply { time = orderDateTime }

                val orderDate = Instant.ofEpochMilli(orderDateTime.time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                val orderHour = calendar.get(Calendar.HOUR_OF_DAY)
                val orderMinute = calendar.get(Calendar.MINUTE)

                // Date filter
                val afterStart = startDate?.let { orderDate.isAfter(it.minusDays(1)) } ?: true
                val beforeEnd = endDate?.let { orderDate.isBefore(it.plusDays(1)) } ?: true

                // Time filter
                val timeMatch = if (startTime != null || endTime != null) {
                    val orderTimeInMinutes = orderHour * 60 + orderMinute
                    val startTimeInMinutes = startTime?.let { it.first * 60 + it.second } ?: 0
                    val endTimeInMinutes = endTime?.let { it.first * 60 + it.second } ?: (23 * 60 + 59)

                    orderTimeInMinutes >= startTimeInMinutes && orderTimeInMinutes <= endTimeInMinutes
                } else {
                    true
                }

                afterStart && beforeEnd && timeMatch
            }

            statusMatch && locationMatch && searchMatch && dateTimeMatch
        }
    }

    val activeFilterCount = selectedStatuses.size + selectedSubLocations.size +
            (if (startDate != null || endDate != null) 1 else 0) +
            (if (startTime != null || endTime != null) 1 else 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Order Feed")
                        Text(
                            "${filteredOrders.size} orders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDateFilter = true }) {
                        Badge(
                            containerColor = if (startDate != null || endDate != null)
                                MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Date Filter")
                        }
                    }

                    IconButton(onClick = { showFilterDialog = true }) {
                        if (activeFilterCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text("$activeFilterCount")
                            }
                        }
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
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
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by food name, restaurant, or customer...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Active filters display
            if (selectedStatuses.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedStatuses.toList()) { status ->
                        FilterChip(
                            selected = true,
                            onClick = { selectedStatuses = selectedStatuses - status },
                            label = { Text(status) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            if (selectedSubLocations.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedSubLocations.toList()) { location ->
                        FilterChip(
                            selected = true,
                            onClick = { selectedSubLocations = selectedSubLocations - location },
                            label = { Text(location) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            if (startDate != null || endDate != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Date: ${startDate ?: "Start"} to ${endDate ?: "End"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = {
                                startDate = null
                                endDate = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear dates",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (startTime != null || endTime != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Time: ${startTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" } ?: "Start"} to ${endTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" } ?: "End"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        IconButton(
                            onClick = {
                                startTime = null
                                endTime = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear times",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (activeFilterCount > 0 || searchQuery.isNotEmpty()) {
                TextButton(
                    onClick = {
                        selectedStatuses = setOf()
                        selectedSubLocations = setOf()
                        searchQuery = ""
                        startDate = null
                        endDate = null
                        startTime = null
                        endTime = null
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear all", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all filters")
                }
            }

            Divider()

            // Orders list
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredOrders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "No results",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No orders found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders) { order ->
                        EnhancedOrderCard(order = order)
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            allStatuses = allStatuses,
            selectedStatuses = selectedStatuses,
            onStatusesChanged = { selectedStatuses = it },
            allSubLocations = allSubLocations.value,
            selectedSubLocations = selectedSubLocations,
            onSubLocationsChanged = { selectedSubLocations = it },
            onDismiss = { showFilterDialog = false }
        )
    }

    if (showDateFilter) {
        DateTimeRangeFilterDialog(
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            onStartDateSelected = { startDate = it },
            onEndDateSelected = { endDate = it },
            onStartTimeSelected = { startTime = it },
            onEndTimeSelected = { endTime = it },
            onDismiss = { showDateFilter = false }
        )
    }
}

@Composable
fun EnhancedOrderCard(order: AdminOrder) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val context = LocalContext.current

    val statusColor = when (order.orderStatus) {
        "Pending" -> Color(0xFF757575)
        "Accepted", "Preparing" -> Color(0xFF0D47A1)
        "On the way" -> Color(0xFFE65100)
        "Delivered" -> Color(0xFF1B5E20)
        "Rejected", "Cancelled" -> Color(0xFFD32F2F)
        else -> Color.Black
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        order.restaurantName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        sdf.format(order.createdAt.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Text(
                    "৳${order.totalPrice}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Items - UPDATED UI
            Text(
                "Items:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp), // Increased padding slightly for spacing
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item Name + MiniRes Name + Partner Status Badge
                    Column(modifier = Modifier.weight(1f)) {
                        // Logic from Rider App
                        val displayText = if (!item.miniResName.isNullOrBlank()) {
                            "${item.quantity}x ${item.name} (${item.miniResName})"
                        } else {
                            "${item.quantity}x ${item.name}"
                        }

                        Text(
                            displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        // Show partner status badge if available
                        if (!item.partnerStatus.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (item.partnerStatus) {
                                    "Accepted" -> Color(0xFF0D47A1).copy(alpha = 0.15f)
                                    "Ready" -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                    else -> Color.Gray.copy(alpha = 0.15f)
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = item.partnerStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (item.partnerStatus) {
                                        "Accepted" -> Color(0xFF0D47A1)
                                        "Ready" -> Color(0xFF2E7D32)
                                        else -> Color.Gray
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Item Price
                    Text(
                        "৳${item.price * item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Customer Info with Contact Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoRow(label = "Customer:", value = order.userName)
                    InfoRow(label = "Location:", value = order.userSubLocation)
                    InfoRow(label = "Rider:", value = order.riderName ?: "Not Assigned")

                    // Phone number with icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Phone",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            order.userPhone,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Contact Action Buttons
                Row {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.userPhone}"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Call Customer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        try {
                            var formattedNumber = order.userPhone.replace(Regex("[^0-9+]"), "")
                            if (formattedNumber.startsWith("01") && formattedNumber.length == 11) {
                                formattedNumber = "+880${formattedNumber.substring(1)}"
                            }
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
                                setPackage("com.whatsapp")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "WhatsApp Customer",
                            tint = Color(0xFF25D366)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Status Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = order.orderStatus,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    allStatuses: List<String>,
    selectedStatuses: Set<String>,
    onStatusesChanged: (Set<String>) -> Unit,
    allSubLocations: List<String>,
    selectedSubLocations: Set<String>,
    onSubLocationsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Order Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    items(allStatuses) { status ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStatusesChanged(
                                        if (selectedStatuses.contains(status)) {
                                            selectedStatuses - status
                                        } else {
                                            selectedStatuses + status
                                        }
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedStatuses.contains(status),
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(status)
                        }
                    }

                    if (allSubLocations.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Sub-Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        items(allSubLocations) { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSubLocationsChanged(
                                            if (selectedSubLocations.contains(location)) {
                                                selectedSubLocations - location
                                            } else {
                                                selectedSubLocations + location
                                            }
                                        )
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedSubLocations.contains(location),
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(location)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        onStatusesChanged(setOf())
                        onSubLocationsChanged(setOf())
                    }) {
                        Text("Clear All")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRangeFilterDialog(
    startDate: LocalDate?,
    endDate: LocalDate?,
    startTime: Pair<Int, Int>?,
    endTime: Pair<Int, Int>?,
    onStartDateSelected: (LocalDate?) -> Unit,
    onEndDateSelected: (LocalDate?) -> Unit,
    onStartTimeSelected: (Pair<Int, Int>?) -> Unit,
    onEndTimeSelected: (Pair<Int, Int>?) -> Unit,
    onDismiss: () -> Unit
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Date & Time Filter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Date Range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(startDate?.toString() ?: "Select Start Date")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(endDate?.toString() ?: "Select End Date")
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        onStartDateSelected(null)
                        onEndDateSelected(null)
                        onStartTimeSelected(null)
                        onEndTimeSelected(null)
                    }) {
                        Text("Clear All")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(onClick = onDismiss) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    // Date Pickers
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onStartDateSelected(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onEndDateSelected(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
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
        TimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onStartTimeSelected(Pair(timePickerState.hour, timePickerState.minute))
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
        TimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onEndTimeSelected(Pair(timePickerState.hour, timePickerState.minute))
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

@Composable
fun TimePickerDialog(
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