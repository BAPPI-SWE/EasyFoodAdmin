package com.yumzy.easyadmin.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.easyadmin.navigation.Screen
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- DATA BLUEPRINTS ---
data class RiderDailyStats(
    val riderId: String,
    val riderName: String,
    val totalAssigned: Int,
    val acceptedCount: Int,
    val deliveredCount: Int
)

data class RestaurantDailyStats(
    val restaurantId: String,
    val restaurantName: String,
    val totalOrders: Int
)

data class AnalyticsOrder(
    val riderId: String? = null,
    val restaurantId: String? = null,
    val orderStatus: String? = null,
    val createdAt: Timestamp = Timestamp.now()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(navController: NavController) {
    // --- DATE RANGE STATE (replaces single selectedDate) ---
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }

    // Which picker is showing: "start", "end", or null
    var showDatePickerFor by remember { mutableStateOf<String?>(null) }
    var showTimeRangePicker by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }

    var riderStats by remember { mutableStateOf<List<RiderDailyStats>>(emptyList()) }
    var restaurantStats by remember { mutableStateOf<List<RestaurantDailyStats>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Re-fetch whenever date range or time filter changes
    LaunchedEffect(startDate, endDate, startTime, endTime) {
        isLoading = true
        val db = Firebase.firestore

        val ridersMap = try {
            db.collection("riders").get().await().documents.associate { doc ->
                doc.id to (doc.getString("name") ?: "Unknown Rider")
            }
        } catch (e: Exception) { emptyMap() }

        val restaurantsMap = try {
            db.collection("restaurants").get().await().documents.associate { doc ->
                doc.id to (doc.getString("name") ?: "Unknown Restaurant")
            }
        } catch (e: Exception) { emptyMap() }

        val zoneId = ZoneId.systemDefault()

        // Start: beginning of startDate (or startTime if set)
        val rangeStartMillis = if (startTime != null) {
            startDate.atTime(startTime!!).atZone(zoneId).toInstant().toEpochMilli()
        } else {
            startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }

        // End: end of endDate (or endTime applied to endDate if set)
        val rangeEndMillis = if (endTime != null) {
            endDate.atTime(endTime!!).atZone(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }

        val startTimestamp = Timestamp(rangeStartMillis / 1000, 0)
        val endTimestamp = Timestamp(rangeEndMillis / 1000, 0)

        val ordersForRange = try {
            db.collection("orders")
                .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
                .whereLessThan("createdAt", endTimestamp)
                .get().await()
                .documents.mapNotNull { it.toObject(AnalyticsOrder::class.java) }
        } catch (e: Exception) { emptyList() }

        val riderOrders = ordersForRange.filter { it.riderId != null }.groupBy { it.riderId!! }
        riderStats = ridersMap.map { (riderId, riderName) ->
            val orders = riderOrders[riderId] ?: emptyList()
            RiderDailyStats(
                riderId = riderId,
                riderName = riderName,
                totalAssigned = orders.size,
                acceptedCount = orders.count { it.orderStatus !in listOf("Pending", "Rejected", "Cancelled") },
                deliveredCount = orders.count { it.orderStatus == "Delivered" }
            )
        }.sortedByDescending { it.totalAssigned }

        val restaurantOrders = ordersForRange.filter { it.restaurantId != null }.groupBy { it.restaurantId!! }
        restaurantStats = restaurantsMap.map { (restaurantId, restaurantName) ->
            RestaurantDailyStats(
                restaurantId = restaurantId,
                restaurantName = restaurantName,
                totalOrders = restaurantOrders[restaurantId]?.size ?: 0
            )
        }.sortedByDescending { it.totalOrders }

        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                actions = {
                    IconButton(onClick = { showTimeRangePicker = true }) {
                        Icon(Icons.Default.AccessTime, contentDescription = "Select Time Range")
                    }
                    IconButton(onClick = { showDatePickerFor = "start" }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date Range")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd MMM, yyyy")

            // --- DATE RANGE DISPLAY ---
            if (startDate == endDate) {
                Text(
                    "Showing stats for: ${startDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                Text(
                    "From: ${startDate.format(dateFormatter)}  →  To: ${endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // --- TIME RANGE DISPLAY ---
            if (startTime != null || endTime != null) {
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                val startText = startTime?.format(timeFormatter) ?: "00:00"
                val endText = endTime?.format(timeFormatter) ?: "23:59"
                Text(
                    "Time range: $startText - $endText",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                TextButton(onClick = { startTime = null; endTime = null },
                    modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Clear Time Filter")
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Rider Stats") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Restaurant Stats") })
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTabIndex) {
                    0 -> RiderStatsContent(
                        stats = riderStats,
                        onRiderClick = { rider ->
                            val zoneId = ZoneId.systemDefault()

                            // Pass the full date range as epoch millis to RiderDetailsScreen
                            val rangeStartMillis = if (startTime != null) {
                                startDate.atTime(startTime!!).atZone(zoneId).toInstant().toEpochMilli()
                            } else {
                                startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                            }

                            val rangeEndMillis = if (endTime != null) {
                                endDate.atTime(endTime!!).atZone(zoneId).toInstant().toEpochMilli()
                            } else {
                                endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                            }

                            navController.navigate(
                                Screen.RiderDetails.createRoute(
                                    riderId = rider.riderId,
                                    riderName = rider.riderName,
                                    startDateMillis = rangeStartMillis,
                                    endDateMillis = rangeEndMillis
                                )
                            )
                        }
                    )
                    1 -> RestaurantStatsContent(restaurantStats)
                }
            }

            // --- DATE RANGE PICKERS ---
            // Step 1: Pick start date
            if (showDatePickerFor == "start") {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePickerFor = null },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                startDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                // Ensure endDate is not before startDate
                                if (endDate.isBefore(startDate)) endDate = startDate
                            }
                            showDatePickerFor = "end" // Move to end date picker
                        }) { Text("Next: End Date") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePickerFor = null }) { Text("Cancel") } }
                ) {
                    DatePicker(state = datePickerState, title = {
                        Text("Select Start Date", modifier = Modifier.padding(start = 24.dp, top = 16.dp))
                    })
                }
            }

            // Step 2: Pick end date
            if (showDatePickerFor == "end") {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            // End date must be >= start date
                            val picked = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                            return !picked.isBefore(startDate)
                        }
                    }
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePickerFor = null },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                endDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            showDatePickerFor = null
                        }) { Text("Apply") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePickerFor = "start" }) { Text("Back") }
                    }
                ) {
                    DatePicker(state = datePickerState, title = {
                        Text("Select End Date", modifier = Modifier.padding(start = 24.dp, top = 16.dp))
                    })
                }
            }

            if (showTimeRangePicker) {
                AnalyticsTimeRangePickerDialog(
                    startTime = startTime,
                    endTime = endTime,
                    onStartTimeSelected = { startTime = it },
                    onEndTimeSelected = { endTime = it },
                    onDismiss = { showTimeRangePicker = false }
                )
            }
        }
    }
}

// ---------- TIME PICKER SECTION (unchanged) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsTimeRangePickerDialog(
    startTime: LocalTime?,
    endTime: LocalTime?,
    onStartTimeSelected: (LocalTime?) -> Unit,
    onEndTimeSelected: (LocalTime?) -> Unit,
    onDismiss: () -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Time Range", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Select Start Time")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Select End Time")
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        onStartTimeSelected(null)
                        onEndTimeSelected(null)
                    }) { Text("Clear Times") }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = onDismiss) { Text("Apply") }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startTime?.hour ?: 0,
            initialMinute = startTime?.minute ?: 0,
            is24Hour = true
        )
        AAnalyticsTimePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onStartTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { TimePicker(state = timePickerState) }
    }

    if (showEndPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endTime?.hour ?: 23,
            initialMinute = endTime?.minute ?: 59,
            is24Hour = true
        )
        AAnalyticsTimePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onEndTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { TimePicker(state = timePickerState) }
    }
}

@Composable
fun AAnalyticsTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}

// ---------- LISTS AND CARDS (unchanged) ----------
@Composable
fun RiderStatsContent(stats: List<RiderDailyStats>, onRiderClick: (RiderDailyStats) -> Unit) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No rider data for this period.")
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stats) { rider ->
                RiderStatCard(stat = rider, onClick = { onRiderClick(rider) })
            }
        }
    }
}

@Composable
fun RestaurantStatsContent(stats: List<RestaurantDailyStats>) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No restaurant data for this period.")
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stats) { restaurant ->
                RestaurantStatCard(stat = restaurant)
            }
        }
    }
}

@Composable
fun RiderStatCard(stat: RiderDailyStats, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(stat.riderName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem("Assigned", stat.totalAssigned.toString())
                StatItem("Accepted", stat.acceptedCount.toString())
                StatItem("Delivered", stat.deliveredCount.toString())
            }
        }
    }
}

@Composable
fun RestaurantStatCard(stat: RestaurantDailyStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stat.restaurantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatItem("Total Orders", stat.totalOrders.toString())
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}