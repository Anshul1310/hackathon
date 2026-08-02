package com.anshul.dcloud.network.models

data class CreateFolderRequest(
    val name: String,
    val parentFolder: String? = null
)

data class CreateFileRequest(
    val name: String,
    val size: Long = 1024,
    val mimeType: String = "text/plain",
    val parentFolder: String? = null
)

data class RenameRequest(
    val name: String
)

data class FolderDto(
    val _id: String,
    val name: String,
    val parentFolder: String? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: String? = null
)

data class FileDto(
    val _id: String,
    val name: String,
    val size: Long = 0,
    val mimeType: String = "",
    val parentFolder: String? = null,
    val path: String? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: String? = null
)

data class FolderListResponse(
    val success: Boolean,
    val folders: List<FolderDto> = emptyList()
)

data class FileListResponse(
    val success: Boolean,
    val files: List<FileDto> = emptyList()
)

data class CreateFolderResponse(
    val success: Boolean,
    val folder: FolderDto? = null,
    val message: String? = null
)

data class CreateFileResponse(
    val success: Boolean,
    val file: FileDto? = null,
    val message: String? = null
)
