package com.anshul.dcloud.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anshul.dcloud.network.ProgressRequestBody
import com.anshul.dcloud.network.RetrofitClient
import com.anshul.dcloud.network.models.CreateFolderRequest
import com.anshul.dcloud.network.models.FileDto
import com.anshul.dcloud.network.models.FolderDto
import com.anshul.dcloud.network.models.RenameRequest
import com.anshul.dcloud.utils.SharedPrefManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("home_tab", "Home", Icons.Default.Home)
    object Deleted : BottomNavItem("deleted_tab", "Deleted", Icons.Default.Delete)
    object Starred : BottomNavItem("starred_tab", "Starred", Icons.Default.Star)
    object Profile : BottomNavItem("profile_tab", "Profile", Icons.Default.Person)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

fun openFileInExternalApp(context: Context, fileUrl: String?, mimeType: String?) {
    if (fileUrl.isNullOrEmpty()) {
        Toast.makeText(context, "Cannot open file: file does not exist", Toast.LENGTH_SHORT).show()
        return
    }
    val fullUrl = if (fileUrl.startsWith("/")) "http://127.0.0.1:5000$fileUrl" else fileUrl
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(fullUrl), mimeType ?: "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(browserIntent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }
}

fun shareFileUrl(context: Context, fileName: String, fileUrl: String?) {
    if (fileUrl.isNullOrEmpty()) {
        Toast.makeText(context, "Cannot share: file URL unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val fullUrl = if (fileUrl.startsWith("/")) "http://127.0.0.1:5000$fileUrl" else fileUrl
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "Check out $fileName on DCloud: $fullUrl")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share file link via")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}

@Composable
fun HomeScreen(
    prefManager: SharedPrefManager,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                val items = listOf(
                    BottomNavItem.Home,
                    BottomNavItem.Deleted,
                    BottomNavItem.Starred,
                    BottomNavItem.Profile
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item,
                        onClick = { selectedTab = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                BottomNavItem.Home -> HomeTabContent(prefManager = prefManager)
                BottomNavItem.Deleted -> DeletedTabContent(prefManager = prefManager)
                BottomNavItem.Starred -> StarredTabContent(prefManager = prefManager)
                BottomNavItem.Profile -> ProfileTabContent(prefManager = prefManager, onSignOut = onSignOut)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeTabContent(prefManager: SharedPrefManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val token = prefManager.getAuthToken() ?: ""
    val authToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

    val folderStack = remember { mutableStateListOf<FolderDto>() }
    val currentParentId = folderStack.lastOrNull()?._id

    var folders by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    var files by remember { mutableStateOf<List<FileDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedSortOption by remember { mutableStateOf("name_asc") }
    var showSortMenu by remember { mutableStateOf(false) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadingFileName by remember { mutableStateOf("") }
    var uploadingFileSize by remember { mutableStateOf("") }
    var uploadPercentage by remember { mutableIntStateOf(0) }
    var uploadStatusMessage by remember { mutableStateOf("Preparing upload...") }

    var showCreateOptionsModal by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var selectedFolderForOptions by remember { mutableStateOf<FolderDto?>(null) }
    var selectedFileForOptions by remember { mutableStateOf<FileDto?>(null) }

    var renamingFolder by remember { mutableStateOf<FolderDto?>(null) }
    var renamingFile by remember { mutableStateOf<FileDto?>(null) }
    var renameInputName by remember { mutableStateOf("") }

    fun loadContent() {
        coroutineScope.launch {
            isLoading = true
            try {
                val fRes = RetrofitClient.apiInterface.getFolders(authToken, currentParentId, selectedSortOption)
                if (fRes.isSuccessful && fRes.body() != null) {
                    folders = fRes.body()!!.folders
                }
                val fileRes = RetrofitClient.apiInterface.getFiles(authToken, currentParentId, selectedSortOption)
                if (fileRes.isSuccessful && fileRes.body() != null) {
                    files = fileRes.body()!!.files
                }
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isUploading = true
                uploadPercentage = 0
                uploadStatusMessage = "Reading file..."
                try {
                    var fileName = "file"
                    var fileSize: Long = 0

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }

                    uploadingFileName = fileName
                    uploadingFileSize = formatBytes(fileSize)
                    uploadStatusMessage = "Uploading to S3 Bucket..."

                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val progressRequestBody = ProgressRequestBody(
                            contentType = mimeType.toMediaTypeOrNull(),
                            contentBytes = bytes,
                            onProgressUpdate = { pct, _, _ ->
                                uploadPercentage = pct
                            }
                        )

                        val body = MultipartBody.Part.createFormData("file", fileName, progressRequestBody)
                        val parentBody = currentParentId?.toRequestBody("text/plain".toMediaTypeOrNull())

                        val response = RetrofitClient.apiInterface.uploadFile(
                            token = authToken,
                            file = body,
                            parentFolder = parentBody
                        )

                        if (response.isSuccessful && response.body()?.success == true) {
                            uploadPercentage = 100
                            uploadStatusMessage = "Upload Complete!"
                        } else {
                            val errorMsg = response.body()?.message ?: "Upload failed"
                            uploadStatusMessage = "Error: $errorMsg"
                        }
                        loadContent()
                    }
                } catch (e: Exception) {
                    uploadStatusMessage = "Upload Error: ${e.localizedMessage}"
                } finally {
                    isUploading = false
                }
            }
        }
    }

    LaunchedEffect(currentParentId, selectedSortOption) {
        loadContent()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateOptionsModal = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (folderStack.isNotEmpty()) {
                    IconButton(
                        onClick = { folderStack.removeAt(folderStack.size - 1) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (folderStack.isEmpty()) "My Storage" else folderStack.last().name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (folderStack.isNotEmpty()) {
                        val pathString = "Root / " + folderStack.joinToString(" / ") { it.name }
                        Text(
                            text = pathString,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = { selectedSortOption = "name_asc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z-A)") },
                            onClick = { selectedSortOption = "name_desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Smallest)") },
                            onClick = { selectedSortOption = "size_asc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Largest)") },
                            onClick = { selectedSortOption = "size_desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date (Newest)") },
                            onClick = { selectedSortOption = "date_desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date (Oldest)") },
                            onClick = { selectedSortOption = "date_asc"; showSortMenu = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (folderStack.isEmpty()) {
                val totalSizeBytes = files.sumOf { it.size }
                val maxQuotaBytes = 250 * 1024 * 1024L
                val progressFraction = (totalSizeBytes.toDouble() / maxQuotaBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
                val usedString = formatBytes(totalSizeBytes)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Storage Usage (250 MB Quota)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$usedString of 250 MB used (${(progressFraction * 100).toInt()}%)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                text = if (folderStack.isEmpty()) "Root Contents" else "Folder Contents",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (folders.isEmpty() && files.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = "This directory is empty. Tap + to add items.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { folderStack.add(folder) },
                                    onLongClick = { selectedFolderForOptions = folder }
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = folder.name, fontWeight = FontWeight.Medium)
                                    Text(text = "Folder", fontSize = 12.sp, color = Color.Gray)
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                RetrofitClient.apiInterface.toggleStarFolder(authToken, folder._id)
                                                loadContent()
                                            } catch (e: Exception) {}
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (folder.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Star",
                                        tint = if (folder.isFavorite) Color(0xFFFFB300) else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    items(files) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        openFileInExternalApp(context, file.path, file.mimeType)
                                    },
                                    onLongClick = {
                                        selectedFileForOptions = file
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = file.name, fontWeight = FontWeight.Medium)
                                    Text(text = formatBytes(file.size), fontSize = 12.sp, color = Color.Gray)
                                }
                                IconButton(
                                    onClick = {
                                        shareFileUrl(context, file.name, file.path)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                RetrofitClient.apiInterface.toggleStarFile(authToken, file._id)
                                                loadContent()
                                            } catch (e: Exception) {}
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (file.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Star",
                                        tint = if (file.isFavorite) Color(0xFFFFB300) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isUploading) {
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text(text = "Uploading File ($uploadPercentage%)") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uploadingFileName,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (uploadingFileSize.isNotEmpty()) {
                        Text(
                            text = uploadingFileSize,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { uploadPercentage / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uploadStatusMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (selectedFileForOptions != null) {
        val targetFile = selectedFileForOptions!!
        ModalBottomSheet(
            onDismissRequest = { selectedFileForOptions = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(text = targetFile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = formatBytes(targetFile.size), fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFileForOptions = null
                            openFileInExternalApp(context, targetFile.path, targetFile.mimeType)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Open File", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val f = targetFile
                            selectedFileForOptions = null
                            renamingFile = f
                            renameInputName = f.name
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Rename File", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFileForOptions = null
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.apiInterface.toggleStarFile(authToken, targetFile._id)
                                    loadContent()
                                } catch (e: Exception) {}
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (targetFile.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (targetFile.isFavorite) "Remove from Starred" else "Add to Starred",
                        fontSize = 16.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFileForOptions = null
                            shareFileUrl(context, targetFile.name, targetFile.path)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Share File Link", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFileForOptions = null
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.apiInterface.trashFile(authToken, targetFile._id)
                                    loadContent()
                                } catch (e: Exception) {}
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Move to Trash", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (selectedFolderForOptions != null) {
        val targetFolder = selectedFolderForOptions!!
        ModalBottomSheet(
            onDismissRequest = { selectedFolderForOptions = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(text = targetFolder.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val fol = targetFolder
                            selectedFolderForOptions = null
                            renamingFolder = fol
                            renameInputName = fol.name
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Rename Folder", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFolderForOptions = null
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.apiInterface.toggleStarFolder(authToken, targetFolder._id)
                                    loadContent()
                                } catch (e: Exception) {}
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (targetFolder.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (targetFolder.isFavorite) "Remove from Starred" else "Add to Starred",
                        fontSize = 16.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFolderForOptions = null
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.apiInterface.trashFolder(authToken, targetFolder._id)
                                    loadContent()
                                } catch (e: Exception) {}
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Move to Trash", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (renamingFolder != null) {
        val targetFolder = renamingFolder!!
        AlertDialog(
            onDismissRequest = { renamingFolder = null },
            title = { Text(text = "Rename Folder") },
            text = {
                OutlinedTextField(
                    value = renameInputName,
                    onValueChange = { renameInputName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputName.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.apiInterface.renameFolder(
                                        token = authToken,
                                        folderId = targetFolder._id,
                                        request = RenameRequest(renameInputName)
                                    )
                                    renamingFolder = null
                                    loadContent()
                                } catch (e: Exception) {}
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (renamingFile != null) {
        val targetFile = renamingFile!!
        AlertDialog(
            onDismissRequest = { renamingFile = null },
            title = { Text(text = "Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInputName,
                    onValueChange = { renameInputName = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputName.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.apiInterface.renameFile(
                                        token = authToken,
                                        fileId = targetFile._id,
                                        request = RenameRequest(renameInputName)
                                    )
                                    renamingFile = null
                                    loadContent()
                                } catch (e: Exception) {}
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateOptionsModal) {
        ModalBottomSheet(
            onDismissRequest = { showCreateOptionsModal = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(text = "Create New", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showCreateOptionsModal = false
                            showCreateFolderDialog = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Create Folder", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showCreateOptionsModal = false
                            filePickerLauncher.launch("*/*")
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Upload File", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(text = "New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    val res = RetrofitClient.apiInterface.createFolder(
                                        token = authToken,
                                        request = CreateFolderRequest(
                                            name = newFolderName,
                                            parentFolder = currentParentId
                                        )
                                    )
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        newFolderName = ""
                                        showCreateFolderDialog = false
                                        loadContent()
                                    }
                                } catch (e: Exception) {
                                }
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DeletedTabContent(prefManager: SharedPrefManager) {
    val coroutineScope = rememberCoroutineScope()
    val token = prefManager.getAuthToken() ?: ""
    val authToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

    var trashedFolders by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    var trashedFiles by remember { mutableStateOf<List<FileDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadTrashedContent() {
        coroutineScope.launch {
            isLoading = true
            try {
                val fRes = RetrofitClient.apiInterface.getTrashedFolders(authToken)
                if (fRes.isSuccessful && fRes.body() != null) {
                    trashedFolders = fRes.body()!!.folders
                }
                val fileRes = RetrofitClient.apiInterface.getTrashedFiles(authToken)
                if (fileRes.isSuccessful && fileRes.body() != null) {
                    trashedFiles = fileRes.body()!!.files
                }
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadTrashedContent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Trash Bin",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (trashedFolders.isEmpty() && trashedFiles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Trash Bin is Empty",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Text(
                    text = "Deleted files will appear here",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trashedFolders) { folder ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = folder.name, fontWeight = FontWeight.Medium)
                                Text(text = "Trashed Folder", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            RetrofitClient.apiInterface.restoreFolder(authToken, folder._id)
                                            loadTrashedContent()
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Restore",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            RetrofitClient.apiInterface.deleteFolderPermanently(authToken, folder._id)
                                            loadTrashedContent()
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                items(trashedFiles) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = file.name, fontWeight = FontWeight.Medium)
                                Text(text = formatBytes(file.size), fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            RetrofitClient.apiInterface.restoreFile(authToken, file._id)
                                            loadTrashedContent()
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Restore",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            RetrofitClient.apiInterface.deleteFilePermanently(authToken, file._id)
                                            loadTrashedContent()
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StarredTabContent(prefManager: SharedPrefManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val token = prefManager.getAuthToken() ?: ""
    val authToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

    var starredFolders by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    var starredFiles by remember { mutableStateOf<List<FileDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadStarredContent() {
        coroutineScope.launch {
            isLoading = true
            try {
                val fRes = RetrofitClient.apiInterface.getStarredFolders(authToken)
                if (fRes.isSuccessful && fRes.body() != null) {
                    starredFolders = fRes.body()!!.folders
                }
                val fileRes = RetrofitClient.apiInterface.getStarredFiles(authToken)
                if (fileRes.isSuccessful && fileRes.body() != null) {
                    starredFiles = fileRes.body()!!.files
                }
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStarredContent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Starred Items",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (starredFolders.isEmpty() && starredFiles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFFFFB300)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Starred Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Text(
                    text = "Star important files or folders for quick access",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(starredFolders) { folder ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = folder.name, fontWeight = FontWeight.Medium)
                                Text(text = "Folder", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            RetrofitClient.apiInterface.toggleStarFolder(authToken, folder._id)
                                            loadStarredContent()
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Unstar",
                                    tint = Color(0xFFFFB300)
                                )
                            }
                        }
                    }
                }

                items(starredFiles) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openFileInExternalApp(context, file.path, file.mimeType)
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = file.name, fontWeight = FontWeight.Medium)
                                Text(text = formatBytes(file.size), fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = {
                                    shareFileUrl(context, file.name, file.path)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            RetrofitClient.apiInterface.toggleStarFile(authToken, file._id)
                                            loadStarredContent()
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Unstar",
                                    tint = Color(0xFFFFB300)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileTabContent(
    prefManager: SharedPrefManager,
    onSignOut: () -> Unit
) {
    val name = prefManager.getUserName() ?: "User"
    val email = prefManager.getUserEmail() ?: "Not Available"
    val jwtToken = prefManager.getAuthToken() ?: "None"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier = Modifier.size(80.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = email, fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "JWT Session Token", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = jwtToken,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Sign Out", color = Color.White, fontSize = 16.sp)
        }
    }
}
