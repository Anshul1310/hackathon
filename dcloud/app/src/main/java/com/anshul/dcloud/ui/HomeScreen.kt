package com.anshul.dcloud.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anshul.dcloud.fragments.DeletedTabContent
import com.anshul.dcloud.fragments.StarredTabContent
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

suspend fun downloadFile(
    context: Context,
    authToken: String,
    fileId: String,
    fileName: String,
    onProgress: (percentage: Int, statusMessage: String) -> Unit = { _, _ -> }
) {
    try {
        onProgress(0, "Connecting...")
        val downloadUrl = "${RetrofitClient.BASE_URL}api/files/$fileId/download"
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(downloadUrl)
            .addHeader("Authorization", authToken)
            .build()

        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            onProgress(-1, "Server error ${response.code}")
            return
        }

        val body = response.body ?: run {
            onProgress(-1, "Empty response")
            return
        }

        val contentLength = body.contentLength()
        onProgress(0, "Downloading...")

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        body.byteStream().use { inputStream ->
                            copyWithProgress(inputStream, outputStream, contentLength, onProgress)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, fileName)
                file.outputStream().use { outputStream ->
                    body.byteStream().use { inputStream ->
                        copyWithProgress(inputStream, outputStream, contentLength, onProgress)
                    }
                }
            }
        }

        onProgress(100, "Download Complete!")
    } catch (e: Exception) {
        onProgress(-1, "Error: ${e.message}")
    }
}

suspend fun downloadFolderAsZip(
    context: Context,
    authToken: String,
    folderId: String,
    folderName: String,
    onProgress: (percentage: Int, statusMessage: String) -> Unit = { _, _ -> }
) {
    val zipFileName = "$folderName.zip"
    try {
        onProgress(0, "Connecting...")
        val downloadUrl = "${RetrofitClient.BASE_URL}api/folders/$folderId/download"
        val client = okhttp3.OkHttpClient.Builder()
            .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        val request = okhttp3.Request.Builder()
            .url(downloadUrl)
            .addHeader("Authorization", authToken)
            .build()

        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            onProgress(-1, "Server error ${response.code}")
            return
        }

        val body = response.body ?: run {
            onProgress(-1, "Empty response")
            return
        }

        val contentLength = body.contentLength()
        onProgress(0, "Downloading ZIP...")

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, zipFileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        body.byteStream().use { inputStream ->
                            copyWithProgress(inputStream, outputStream, contentLength, onProgress)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, zipFileName)
                file.outputStream().use { outputStream ->
                    body.byteStream().use { inputStream ->
                        copyWithProgress(inputStream, outputStream, contentLength, onProgress)
                    }
                }
            }
        }

        onProgress(100, "Download Complete!")
    } catch (e: Exception) {
        onProgress(-1, "Error: ${e.message}")
    }
}

private suspend fun copyWithProgress(
    inputStream: java.io.InputStream,
    outputStream: java.io.OutputStream,
    totalBytes: Long,
    onProgress: (percentage: Int, statusMessage: String) -> Unit
) {
    val buffer = ByteArray(8192)
    var bytesRead: Int
    var totalRead = 0L
    var lastReportedPct = -1

    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
        totalRead += bytesRead
        if (totalBytes > 0) {
            val pct = ((totalRead * 100) / totalBytes).toInt().coerceAtMost(99)
            if (pct != lastReportedPct) {
                lastReportedPct = pct
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onProgress(pct, "Downloading ($pct%)...")
                }
            }
        } else {
            // Unknown size — report bytes downloaded
            val readFormatted = formatBytes(totalRead)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onProgress(-2, "Downloading... $readFormatted")
            }
        }
    }
    outputStream.flush()
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

    var isDownloading by remember { mutableStateOf(false) }
    var downloadingFileName by remember { mutableStateOf("") }
    var downloadPercentage by remember { mutableIntStateOf(0) }
    var downloadStatusMessage by remember { mutableStateOf("Preparing download...") }

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
                    uploadStatusMessage = "Uploading to server (0%)..."

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
                                uploadStatusMessage = "Uploading to server ($pct%)..."
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
                            uploadStatusMessage = "Upload Complete! (100%)"
                            kotlinx.coroutines.delay(500)
                            isUploading = false
                            loadContent()
                        } else {
                            val errorMsg = response.body()?.message ?: "Upload failed"
                            uploadStatusMessage = "Error: $errorMsg"
                            kotlinx.coroutines.delay(1500)
                            isUploading = false
                        }
                    }
                } catch (e: Exception) {
                    uploadStatusMessage = "Upload Error: ${e.localizedMessage}"
                    kotlinx.coroutines.delay(1500)
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

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = if (downloadPercentage >= 0) "Downloading ($downloadPercentage%)"
                           else "Downloading..."
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = downloadingFileName,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (downloadPercentage >= 0) {
                        LinearProgressIndicator(
                            progress = { downloadPercentage / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = downloadStatusMessage,
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
                            val fileToDownload = targetFile
                            selectedFileForOptions = null
                            isDownloading = true
                            downloadingFileName = fileToDownload.name
                            downloadPercentage = 0
                            downloadStatusMessage = "Preparing download..."
                            coroutineScope.launch {
                                downloadFile(
                                    context, authToken, fileToDownload._id, fileToDownload.name
                                ) { pct, msg ->
                                    downloadPercentage = pct
                                    downloadStatusMessage = msg
                                }
                                kotlinx.coroutines.delay(800)
                                isDownloading = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Download File", fontSize = 16.sp)
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
                            val folderToDownload = targetFolder
                            selectedFolderForOptions = null
                            isDownloading = true
                            downloadingFileName = folderToDownload.name + ".zip"
                            downloadPercentage = 0
                            downloadStatusMessage = "Preparing download..."
                            coroutineScope.launch {
                                downloadFolderAsZip(
                                    context, authToken, folderToDownload._id, folderToDownload.name
                                ) { pct, msg ->
                                    downloadPercentage = pct
                                    downloadStatusMessage = msg
                                }
                                kotlinx.coroutines.delay(800)
                                isDownloading = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Download as ZIP", fontSize = 16.sp)
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


data class CategoryStat(
    val name: String,
    val bytes: Long,
    val color: Color
)

@Composable
fun ProfileTabContent(
    prefManager: SharedPrefManager,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val token = prefManager.getAuthToken() ?: ""
    val authToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

    var userName by remember { mutableStateOf(prefManager.getUserName() ?: "User") }
    var userEmail by remember { mutableStateOf(prefManager.getUserEmail() ?: "Not Available") }
    var userAvatar by remember { mutableStateOf<String?>(prefManager.getUserAvatar()) }

    var allFiles by remember { mutableStateOf<List<FileDto>>(emptyList()) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    fun loadProfileData() {
        coroutineScope.launch {
            try {
                val response = RetrofitClient.apiInterface.getFiles(authToken)
                if (response.isSuccessful && response.body() != null) {
                    allFiles = response.body()!!.files
                }
            } catch (e: Exception) {}
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isUploadingAvatar = true
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestFile)

                        val response = RetrofitClient.apiInterface.uploadAvatar(authToken, body)
                        if (response.isSuccessful && response.body()?.success == true) {
                            val newAvatar = response.body()?.user?.avatar
                            if (!newAvatar.isNullOrEmpty()) {
                                userAvatar = newAvatar
                                prefManager.saveUser(userName, userEmail, newAvatar)
                                Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Failed to upload photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProfileData()
    }

    val totalSizeBytes = allFiles.sumOf { it.size }

    val imagesBytes = allFiles.filter { it.mimeType.startsWith("image/") }.sumOf { it.size }
    val videosBytes = allFiles.filter { it.mimeType.startsWith("video/") }.sumOf { it.size }
    val audioBytes = allFiles.filter { it.mimeType.startsWith("audio/") }.sumOf { it.size }
    val docsBytes = allFiles.filter {
        it.mimeType.startsWith("application/") || it.mimeType.startsWith("text/") ||
                it.name.endsWith(".pdf") || it.name.endsWith(".doc") || it.name.endsWith(".docx") || it.name.endsWith(".txt")
    }.sumOf { it.size }
    val othersBytes = (totalSizeBytes - (imagesBytes + videosBytes + audioBytes + docsBytes)).coerceAtLeast(0L)

    val categoryStats = listOf(
        CategoryStat("Images", imagesBytes, Color(0xFF4CAF50)),
        CategoryStat("Videos", videosBytes, Color(0xFFFF9800)),
        CategoryStat("Audio", audioBytes, Color(0xFFE91E63)),
        CategoryStat("Documents", docsBytes, Color(0xFF2196F3)),
        CategoryStat("Others", othersBytes, Color(0xFF9C27B0))
    )

    val largestCategory = categoryStats.maxByOrNull { it.bytes }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Profile & Analytics",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clickable { avatarPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    val avatarUrl = userAvatar
                    if (!avatarUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = userName.take(1).uppercase(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isUploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                contentDescription = "Change Photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = userName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text = userEmail, fontSize = 14.sp, color = Color.Gray)
            Text(
                text = "Tap photo to change avatar",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Storage Breakdown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

//

                    Spacer(modifier = Modifier.height(20.dp))

                    if (totalSizeBytes > 0L && largestCategory != null && largestCategory.bytes > 0L) {
                        val percentage = ((largestCategory.bytes.toDouble() / totalSizeBytes.toDouble()) * 100).toInt()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = largestCategory.color.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(12.dp).clip(CircleShape),
                                    color = largestCategory.color
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${largestCategory.name} consume the largest part of storage (${formatBytes(largestCategory.bytes)} - $percentage%)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryStats.forEach { stat ->
                            val pct = if (totalSizeBytes > 0L) ((stat.bytes.toDouble() / totalSizeBytes.toDouble()) * 100).toInt() else 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(12.dp).clip(CircleShape),
                                    color = stat.color
                                ) {}
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text =   stat.name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${formatBytes(stat.bytes)} ($pct%)",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Sign Out", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}




