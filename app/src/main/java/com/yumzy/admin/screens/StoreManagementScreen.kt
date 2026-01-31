package com.yumzy.admin.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.admin.components.MultiSelectDropdown
import com.yumzy.admin.data.GroupedCategory
import com.yumzy.admin.data.MainCategory
import com.yumzy.admin.data.MiniRestaurant
import com.yumzy.admin.data.StoreSubCategory
import com.yumzy.admin.navigation.Screen
import com.yumzy.admin.utils.ImageUploadHelper
import com.yumzy.admin.utils.ImageUploadHelper.ImageType
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class for locations
data class LocationData(val subLocations: List<String> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreManagementScreen(navController: NavController) {
    var groupedCategories by remember { mutableStateOf<List<GroupedCategory>>(emptyList()) }
    var miniRestaurants by remember { mutableStateOf<List<MiniRestaurant>>(emptyList()) }
    var mainCategories by remember { mutableStateOf<List<MainCategory>>(emptyList()) }
    var allSubLocations by remember { mutableStateOf<List<String>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Categories", "Shops")
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddEditDialog by remember { mutableStateOf(false) }
    var restaurantToEdit by remember { mutableStateOf<MiniRestaurant?>(null) }

    // For category management
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<StoreSubCategory?>(null) }

    // For delete confirmation
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Any?>(null) }
    var deleteType by remember { mutableStateOf("") } // "shop" or "category"

    fun refreshData() {
        isLoading = true
        coroutineScope.launch {
            try {
                val db = Firebase.firestore
                val mainCatsResult = db.collection("store_main_categories").get().await()
                val subCatsResult = db.collection("store_sub_categories").get().await()
                val locationsResult = db.collection("locations").get().await()
                val miniResResult = db.collection("mini_restaurants").get().await()

                mainCategories = mainCatsResult.documents.mapNotNull { it.toObject(MainCategory::class.java)?.copy(id = it.id) }

                // UPDATED: Sorted by Priority
                val subCategories = subCatsResult.documents.mapNotNull { it.toObject(StoreSubCategory::class.java)?.copy(id = it.id) }
                    .sortedBy { it.priority }

                allSubLocations = locationsResult.documents.flatMap { it.toObject(LocationData::class.java)?.subLocations ?: emptyList() }.distinct()

                // UPDATED: Sorted by Priority
                miniRestaurants = miniResResult.documents.mapNotNull { it.toObject(MiniRestaurant::class.java)?.copy(id = it.id) }
                    .sortedBy { it.priority }

                val subCategoriesMap = subCategories.groupBy { it.parentCategory }
                groupedCategories = mainCategories.map { mainCat ->
                    GroupedCategory(mainCategory = mainCat, subCategories = subCategoriesMap[mainCat.id] ?: emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching data: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteShop(restaurant: MiniRestaurant) {
        coroutineScope.launch {
            try {
                // Delete image from storage
                if (restaurant.imageUrl.isNotBlank()) {
                    ImageUploadHelper.deleteImage(restaurant.imageUrl)
                }

                // Delete from Firestore
                Firebase.firestore.collection("mini_restaurants").document(restaurant.id).delete().await()

                // Also delete all items associated with this shop
                val itemsSnapshot = Firebase.firestore.collection("store_items")
                    .whereEqualTo("miniRes", restaurant.id)
                    .get().await()

                for (doc in itemsSnapshot.documents) {
                    val itemImageUrl = doc.getString("imageUrl")
                    if (!itemImageUrl.isNullOrBlank()) {
                        ImageUploadHelper.deleteImage(itemImageUrl)
                    }
                    doc.reference.delete().await()
                }

                Toast.makeText(context, "Shop deleted successfully", Toast.LENGTH_SHORT).show()
                refreshData()
            } catch (e: Exception) {
                Toast.makeText(context, "Error deleting shop: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yumzy Store Management") }) },
        floatingActionButton = {
            when (selectedTabIndex) {
                0 -> { // Categories tab
                    FloatingActionButton(onClick = {
                        categoryToEdit = null
                        showAddCategoryDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Sub-Category")
                    }
                }
                1 -> { // Shops tab
                    FloatingActionButton(onClick = {
                        restaurantToEdit = null
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Mini-Restaurant")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                when (selectedTabIndex) {
                    0 -> CategoriesManagementContent(
                        groupedCategories = groupedCategories,
                        onCategoryClick = { categoryToEdit = it },
                        onDeleteClick = { category ->
                            itemToDelete = category
                            deleteType = "category"
                            showDeleteConfirm = true
                        }
                    )
                    1 -> ShopsContent(
                        miniRestaurants = miniRestaurants,
                        onCardClick = { miniRes ->
                            navController.navigate(Screen.MiniResSubCategories.createRoute(miniRes.id, miniRes.name, miniRes.parentCategory))
                        },
                        onEditClick = { miniRes ->
                            restaurantToEdit = miniRes
                            showAddEditDialog = true
                        },
                        onDeleteClick = { miniRes ->
                            itemToDelete = miniRes
                            deleteType = "shop"
                            showDeleteConfirm = true
                        },
                        onStatusChange = { miniRes, isOpen ->
                            val newStatus = if (isOpen) "yes" else "no"
                            Firebase.firestore.collection("mini_restaurants").document(miniRes.id)
                                .update("open", newStatus)
                                .addOnSuccessListener { refreshData() }
                        }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Confirm Delete") },
                text = {
                    when (deleteType) {
                        "shop" -> {
                            val shop = itemToDelete as? MiniRestaurant
                            Text("Are you sure you want to delete '${shop?.name}'? This will also delete all items in this shop.")
                        }
                        "category" -> {
                            val category = itemToDelete as? StoreSubCategory
                            Text("Are you sure you want to delete '${category?.name}' category?")
                        }
                        else -> Text("Are you sure you want to delete this item?")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (deleteType) {
                                "shop" -> deleteShop(itemToDelete as MiniRestaurant)
                                "category" -> {
                                    // Category deletion is handled in EditCategoryDialog
                                }
                            }
                            showDeleteConfirm = false
                            itemToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        itemToDelete = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Shop Add/Edit Dialog
        if (showAddEditDialog) {
            AddEditMiniRestaurantDialog(
                restaurant = restaurantToEdit,
                mainCategories = mainCategories,
                allLocations = allSubLocations,
                onDismiss = { showAddEditDialog = false },
                onSave = { name: String, imageUri: Uri?, parentId: String, locations: List<String>, openStatus: Boolean, password: String, priority: Int ->
                    coroutineScope.launch {
                        try {
                            // Show loading
                            Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()

                            // Upload image to Firebase Storage with resizing
                            val imageUrl: String = if (imageUri != null) {
                                if (restaurantToEdit != null) {
                                    // Replace existing image
                                    ImageUploadHelper.replaceImage(
                                        context = context,
                                        oldImageUrl = restaurantToEdit?.imageUrl,
                                        newImageUri = imageUri,
                                        folder = "mini_restaurants",
                                        imageType = ImageType.RESTAURANT
                                    )
                                } else {
                                    // Upload new image
                                    ImageUploadHelper.uploadImage(
                                        context = context,
                                        uri = imageUri,
                                        folder = "mini_restaurants",
                                        imageType = ImageType.RESTAURANT
                                    )
                                }
                            } else {
                                restaurantToEdit?.imageUrl ?: ""
                            }

                            val data = hashMapOf(
                                "name" to name,
                                "imageUrl" to imageUrl,
                                "parentCategory" to parentId,
                                "availableLocations" to locations,
                                "open" to if (openStatus) "yes" else "no",
                                "password" to password, // Added Password
                                "priority" to priority  // Added Priority
                            )

                            val task = if (restaurantToEdit == null) {
                                Firebase.firestore.collection("mini_restaurants").add(data)
                            } else {
                                Firebase.firestore.collection("mini_restaurants").document(restaurantToEdit!!.id).set(data)
                            }

                            task.await()
                            Toast.makeText(context, "Shop Saved!", Toast.LENGTH_SHORT).show()
                            refreshData()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    showAddEditDialog = false
                },
                onDelete = { restaurant ->
                    deleteShop(restaurant)
                    showAddEditDialog = false
                }
            )
        }

        // Category Add/Edit Dialog
        if (showAddCategoryDialog) {
            EditCategoryDialog(
                mainCategories = mainCategories,
                allLocations = allSubLocations,
                onDismiss = { showAddCategoryDialog = false },
                onSave = { name: String, imageUri: Uri?, parentId: String, locations: List<String>, priority: Int ->
                    coroutineScope.launch {
                        try {
                            if (imageUri == null) {
                                Toast.makeText(context, "Please select an image.", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()

                            // Upload image to Firebase Storage with resizing
                            val imageUrl: String = ImageUploadHelper.uploadImage(
                                context = context,
                                uri = imageUri,
                                folder = "subcategories",
                                imageType = ImageType.SUBCATEGORY
                            )

                            val newCategory = hashMapOf(
                                "name" to name,
                                "imageUrl" to imageUrl,
                                "parentCategory" to parentId,
                                "availableLocations" to locations,
                                "priority" to priority // Added Priority
                            )

                            Firebase.firestore.collection("store_sub_categories").add(newCategory).await()
                            Toast.makeText(context, "Category Added", Toast.LENGTH_SHORT).show()
                            refreshData()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    showAddCategoryDialog = false
                }
            )
        }

        categoryToEdit?.let { category ->
            EditCategoryDialog(
                mainCategories = mainCategories,
                allLocations = allSubLocations,
                subCategory = category,
                onDismiss = { categoryToEdit = null },
                onSave = { name: String, imageUri: Uri?, parentId: String, locations: List<String>, priority: Int ->
                    coroutineScope.launch {
                        try {
                            Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()

                            // Upload image to Firebase Storage (replace old one) with resizing
                            val imageUrl: String = if (imageUri != null) {
                                ImageUploadHelper.replaceImage(
                                    context = context,
                                    oldImageUrl = category.imageUrl ?: "",
                                    newImageUri = imageUri,
                                    folder = "subcategories",
                                    imageType = ImageType.SUBCATEGORY
                                )
                            } else {
                                category.imageUrl ?: ""
                            }

                            val updatedCategory = mapOf(
                                "name" to name,
                                "imageUrl" to imageUrl,
                                "parentCategory" to parentId,
                                "availableLocations" to locations,
                                "priority" to priority // Added Priority
                            )

                            Firebase.firestore.collection("store_sub_categories").document(category.id)
                                .update(updatedCategory).await()
                            Toast.makeText(context, "Category Updated", Toast.LENGTH_SHORT).show()
                            refreshData()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    categoryToEdit = null
                },
                onDelete = {
                    coroutineScope.launch {
                        try {
                            // Delete image from storage
                            category.imageUrl?.let { ImageUploadHelper.deleteImage(it) }
                            // Delete from Firestore
                            Firebase.firestore.collection("store_sub_categories").document(category.id).delete().await()
                            Toast.makeText(context, "Category Deleted", Toast.LENGTH_SHORT).show()
                            refreshData()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    categoryToEdit = null
                }
            )
        }
    }
}

@Composable
fun CategoriesManagementContent(
    groupedCategories: List<GroupedCategory>,
    onCategoryClick: (StoreSubCategory) -> Unit,
    onDeleteClick: (StoreSubCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(groupedCategories) { group ->
            Text(
                text = group.mainCategory.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            group.subCategories.forEach { subCat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCategoryClick(subCat) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = subCat.imageUrl,
                                contentDescription = subCat.name,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = subCat.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Priority: ${subCat.priority}", // Show Priority
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${subCat.availableLocations.size} locations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            IconButton(
                                onClick = { onDeleteClick(subCat) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ShopsContent(
    miniRestaurants: List<MiniRestaurant>,
    onCardClick: (MiniRestaurant) -> Unit,
    onEditClick: (MiniRestaurant) -> Unit,
    onDeleteClick: (MiniRestaurant) -> Unit,
    onStatusChange: (MiniRestaurant, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(miniRestaurants) { restaurant ->
            MiniRestaurantAdminCard(
                restaurant = restaurant,
                onClick = { onCardClick(restaurant) },
                onStatusChange = { isOpen -> onStatusChange(restaurant, isOpen) },
                onEditClick = { onEditClick(restaurant) },
                onDeleteClick = { onDeleteClick(restaurant) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniRestaurantAdminCard(
    restaurant: MiniRestaurant,
    onClick: () -> Unit,
    onStatusChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isClosed = restaurant.open.equals("no", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = restaurant.imageUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )
            if (isClosed) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Closed", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onEditClick) {
                            Text("EDIT", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isClosed) "Closed" else "Open", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = !isClosed,
                            onCheckedChange = onStatusChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
                Column {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Priority: ${restaurant.priority}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMiniRestaurantDialog(
    restaurant: MiniRestaurant?,
    mainCategories: List<MainCategory>,
    allLocations: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Uri?, String, List<String>, Boolean, String, Int) -> Unit,
    onDelete: ((MiniRestaurant) -> Unit)? = null
) {
    var name by remember { mutableStateOf(restaurant?.name ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isOpen by remember { mutableStateOf(restaurant?.open?.equals("yes", true) ?: true) }

    // UPDATED: New State variables
    var password by remember { mutableStateOf(restaurant?.password ?: "") }
    var priority by remember { mutableStateOf(restaurant?.priority?.toString() ?: "1") }

    var expanded by remember { mutableStateOf(false) }
    var selectedParent by remember { mutableStateOf(mainCategories.find { it.id == restaurant?.parentCategory }) }
    var selectedLocations by remember { mutableStateOf(restaurant?.availableLocations ?: emptyList()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (restaurant == null) "Add Shop" else "Edit Shop", style = MaterialTheme.typography.titleLarge)

                TextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") })

                // UPDATED: Password and Priority Fields
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("For restaurant login") }
                )

                TextField(
                    value = priority,
                    onValueChange = { if (it.all { char -> char.isDigit() }) priority = it },
                    label = { Text("Priority") },
                    placeholder = { Text("Lower number shows first") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Image Picker Field
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            selectedImageUri != null -> {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            restaurant?.imageUrl?.isNotBlank() == true -> {
                                AsyncImage(
                                    model = restaurant.imageUrl,
                                    contentDescription = "Current image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Select image",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Tap to select image", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // Show upload icon overlay
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Upload",
                                modifier = Modifier.padding(8.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = selectedParent?.name ?: "Select Main Category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        mainCategories.forEach { mainCat ->
                            DropdownMenuItem(text = { Text(mainCat.name) }, onClick = { selectedParent = mainCat; expanded = false })
                        }
                    }
                }

                MultiSelectDropdown(
                    label = "Available Locations",
                    options = allLocations,
                    selectedOptions = selectedLocations,
                    onSelectionChanged = { selectedLocations = it }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shop Status:")
                    Spacer(Modifier.width(8.dp))
                    Text(if (isOpen) "Open" else "Closed")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isOpen, onCheckedChange = { isOpen = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (restaurant != null && onDelete != null) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete Shop")
                        }
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Shop?") },
                                text = { Text("Are you sure you want to delete '${restaurant.name}'? This will also delete all items in this shop.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            onDelete(restaurant)
                                            showDeleteConfirm = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = {
                            val parent = selectedParent
                            if (name.isBlank() || parent == null || selectedLocations.isEmpty()) {
                                return@Button
                            }
                            // Only require image for new restaurants
                            if (restaurant == null && selectedImageUri == null) {
                                return@Button
                            }
                            onSave(name, selectedImageUri, parent.id, selectedLocations, isOpen, password, priority.toIntOrNull() ?: 0)
                        }) { Text("Save") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryDialog(
    mainCategories: List<MainCategory>,
    allLocations: List<String>,
    subCategory: StoreSubCategory? = null,
    onDismiss: () -> Unit,
    onSave: (String, Uri?, String, List<String>, Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(subCategory?.name ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var selectedParent by remember { mutableStateOf(mainCategories.find { it.id == subCategory?.parentCategory }) }
    var selectedLocations by remember { mutableStateOf(subCategory?.availableLocations ?: emptyList()) }

    // UPDATED: Priority State
    var priority by remember { mutableStateOf(subCategory?.priority?.toString() ?: "1") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (subCategory == null) "Add Sub-Category" else "Edit Sub-Category", style = MaterialTheme.typography.titleLarge)

                TextField(value = name, onValueChange = { name = it }, label = { Text("Sub-Category Name") })

                // UPDATED: Priority Field
                TextField(
                    value = priority,
                    onValueChange = { if (it.all { char -> char.isDigit() }) priority = it },
                    label = { Text("Priority") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Image Picker Field
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            selectedImageUri != null -> {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            subCategory?.imageUrl?.isNotBlank() == true -> {
                                AsyncImage(
                                    model = subCategory.imageUrl,
                                    contentDescription = "Current image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Select image",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Tap to select image", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // Show upload icon overlay
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Upload",
                                modifier = Modifier.padding(8.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = selectedParent?.name ?: "Select Main Category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        mainCategories.forEach { mainCat ->
                            DropdownMenuItem(text = { Text(mainCat.name) }, onClick = { selectedParent = mainCat; expanded = false })
                        }
                    }
                }

                MultiSelectDropdown(
                    label = "Available Locations",
                    options = allLocations,
                    selectedOptions = selectedLocations,
                    onSelectionChanged = { selectedLocations = it }
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        if(showDeleteConfirm){
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Category?") },
                                text = { Text("Are you sure you want to delete '${subCategory?.name}'? This cannot be undone.")},
                                confirmButton = {
                                    Button(
                                        onClick = { onDelete(); showDeleteConfirm = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }}
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val currentParent = selectedParent
                        if(name.isBlank() || currentParent == null || selectedLocations.isEmpty()){
                            return@Button
                        }
                        // Only require image for new categories
                        if (subCategory == null && selectedImageUri == null) {
                            return@Button
                        }
                        onSave(name, selectedImageUri, currentParent.id, selectedLocations, priority.toIntOrNull() ?: 0)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}