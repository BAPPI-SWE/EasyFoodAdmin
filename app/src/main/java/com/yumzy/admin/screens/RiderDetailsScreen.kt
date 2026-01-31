package com.yumzy.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RiderOrderItem(
    val itemName: String = "",
    val quantity: Int = 0,
    val miniResName: String = "N/A",
    val price: Double = 0.0,
    val partnerStatus: String? = null
)

data class RiderOrderDetail(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userSubLocation: String = "",
    val totalPrice: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val orderStatus: String = "",
    val payment: String = "Online", // NEW: Payment field
    val items: List<RiderOrderItem> = emptyList()
)

data class DailyTotals(
    val totalItems: Int = 0,
    val totalGoodsValue: Double = 0.0,
    val totalDeliveryCharge: Double = 0.0,
    val totalServiceCharge: Double = 0.0,
    val totalOnlineCollected: Double = 0.0 // NEW: Statistic for online payments
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderDetailsScreen(
    riderId: String,
    riderName: String,
    dateMillis: Long,
    startTimeMillis: Long = -1L,
    endTimeMillis: Long = -1L,
    navController: NavController
) {
    var orders by remember { mutableStateOf<List<RiderOrderDetail>>(emptyList()) }
    var dailyTotals by remember { mutableStateOf(DailyTotals()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    LaunchedEffect(key1 = Unit) {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val actualStartMillis = if (startTimeMillis > 0) startTimeMillis else startOfDay
        val actualEndMillis = if (endTimeMillis > 0) endTimeMillis else endOfDay

        val startTimestamp = Timestamp(actualStartMillis / 1000, 0)
        val endTimestamp = Timestamp(actualEndMillis / 1000, 0)

        val acceptedOrders = Firebase.firestore.collection("orders")
            .whereEqualTo("riderId", riderId)
            .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
            .whereLessThan("createdAt", endTimestamp)
            .get()
            .await()
            .documents.mapNotNull { doc ->
                val status = doc.getString("orderStatus") ?: ""
                if (status in listOf("Pending", "Rejected", "Cancelled")) {
                    return@mapNotNull null
                }
                val itemsData = doc.get("items") as? List<Map<String, Any>> ?: emptyList()

                val mappedItems = itemsData.map { itemMap ->
                    RiderOrderItem(
                        itemName = itemMap["itemName"] as? String ?: "Unknown Item",
                        quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 0,
                        miniResName = itemMap["miniResName"] as? String ?: "",
                        price = (itemMap["itemPrice"] as? Number)?.toDouble()
                            ?: (itemMap["price"] as? Number)?.toDouble() ?: 0.0,
                        partnerStatus = itemMap["partnerStatus"] as? String
                    )
                }

                RiderOrderDetail(
                    id = doc.id,
                    restaurantName = doc.getString("restaurantName") ?: "N/A",
                    userName = doc.getString("userName") ?: "N/A",
                    userPhone = doc.getString("userPhone") ?: "N/A",
                    userSubLocation = doc.getString("userSubLocation") ?: "No address detail",
                    totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                    deliveryCharge = doc.getDouble("deliveryCharge") ?: 0.0,
                    serviceCharge = doc.getDouble("serviceCharge") ?: 0.0,
                    orderStatus = status,
                    payment = doc.getString("payment") ?: "Online", // NEW: Fetching payment
                    items = mappedItems
                )
            }

        orders = acceptedOrders

        // --- CALCULATION LOGIC UPDATED ---
        val itemsCount = acceptedOrders.sumOf { order -> order.items.sumOf { it.quantity } }
        val deliveryChargeSum = acceptedOrders.sumOf { it.deliveryCharge }
        val serviceChargeSum = acceptedOrders.sumOf { it.serviceCharge }
        val goodsValueSum = acceptedOrders.sumOf { it.totalPrice - it.deliveryCharge - it.serviceCharge }

        // NEW: Calculate Online Collected (Any order that is NOT COD)
        val onlineCollectedSum = acceptedOrders
            .filter { it.payment != "COD" }
            .sumOf { it.totalPrice }

        dailyTotals = DailyTotals(
            totalItems = itemsCount,
            totalGoodsValue = goodsValueSum,
            totalDeliveryCharge = deliveryChargeSum,
            totalServiceCharge = serviceChargeSum,
            totalOnlineCollected = onlineCollectedSum
        )

        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(riderName)
                        if (startTimeMillis > 0 || endTimeMillis > 0) {
                            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                            val startText = if (startTimeMillis > 0) Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter) else "00:00"
                            val endText = if (endTimeMillis > 0) Instant.ofEpochMilli(endTimeMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter) else "23:59"
                            Text("Time: $startText - $endText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    val formatter = DateTimeFormatter.ofPattern("dd MMMM, yyyy")
                    Text("Accepted Orders on ${selectedDate.format(formatter)}", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryCard(totals = dailyTotals)
                    Spacer(modifier = Modifier.height(16.dp))
                    if(orders.isNotEmpty()){
                        Text("Order List (${orders.size})", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (orders.isEmpty()){
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center){
                            Text("No orders found for this rider on this day.")
                        }
                    }
                } else {
                    items(orders) { order ->
                        OrderDetailCard(order = order)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(totals: DailyTotals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryRow("Total Items Handled", "${totals.totalItems}")
            HorizontalDivider()
            SummaryRow("Total Goods Value", "৳${"%.2f".format(totals.totalGoodsValue)}")
            HorizontalDivider()
            SummaryRow("Total Delivery Charge", "৳${"%.2f".format(totals.totalDeliveryCharge)}")
            HorizontalDivider()
            SummaryRow("Total Service Charge", "৳${"%.2f".format(totals.totalServiceCharge)}")
            HorizontalDivider()
            // NEW: Online Collected Row
            SummaryRow("Total Online Collected", "৳${"%.2f".format(totals.totalOnlineCollected)}", highlight = true)
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun OrderDetailCard(order: RiderOrderDetail) {
    // State to control the visibility of the payment detail dialog
    var showPaymentDialog by remember { mutableStateOf(false) }

    val statusColor = when (order.orderStatus) {
        "Delivered" -> Color(0xFF1B5E20)
        "On the way" -> Color(0xFF0D47A1)
        "Accepted", "Preparing" -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // --- Payment Detail Dialog ---
    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Payment Information") },
            text = {
                Column {
                    Text("Method: ${order.payment}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Order ID: ${order.id}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ID: ${order.id.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))

                    // --- Clickable Payment Badge ---
                    Surface(
                        color = if(order.payment == "COD") Color.Red.copy(alpha = 0.1f) else Color.Blue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { showPaymentDialog = true } // Trigger the dialog
                    ) {
                        Text(
                            // Logic: Show "COD" if it's COD, otherwise just show "Online" on the card
                            text = if (order.payment == "COD") "COD" else "Online",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if(order.payment == "COD") Color.Red else Color.Blue
                        )
                    }
                }

                Text(
                    text = order.orderStatus,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(statusColor, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(text = order.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = "Phone", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(text = order.userPhone, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = "Location", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(text = order.userSubLocation, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Items:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val displayText = if (!item.miniResName.isNullOrBlank() && item.miniResName != "N/A") {
                                "${item.quantity}x ${item.itemName} (${item.miniResName})"
                            } else {
                                "${item.quantity}x ${item.itemName}"
                            }

                            Text(text = displayText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

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

                        Text(
                            text = "৳${"%.1f".format(item.price * item.quantity)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Optional: Small label indicating the card payment type
                Text(
                    text = if(order.payment == "COD") "Cash Collection" else "Paid Online",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )

                Row {
                    Text("Total Bill: ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "৳${"%.2f".format(order.totalPrice)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}