package com.anshul.dcloud.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anshul.dcloud.network.RetrofitClient
import com.anshul.dcloud.network.models.FileDto
import com.anshul.dcloud.network.models.FolderDto
import com.anshul.dcloud.ui.formatBytes
import com.anshul.dcloud.utils.SharedPrefManager
import kotlinx.coroutines.launch

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
