package com.anshul.dcloud.network

import com.anshul.dcloud.network.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiInterface {
    @POST("api/auth/google")
    suspend fun googleAuth(
        @Body request: GoogleAuthRequest
    ): Response<AuthResponse>

    @POST("api/folders")
    suspend fun createFolder(
        @Header("Authorization") token: String,
        @Body request: CreateFolderRequest
    ): Response<CreateFolderResponse>

    @GET("api/folders")
    suspend fun getFolders(
        @Header("Authorization") token: String,
        @Query("parentFolder") parentFolder: String? = null
    ): Response<FolderListResponse>

    @PATCH("api/folders/{id}/star")
    suspend fun toggleStarFolder(
        @Header("Authorization") token: String,
        @Path("id") folderId: String
    ): Response<CreateFolderResponse>

    @GET("api/folders/trashed")
    suspend fun getTrashedFolders(
        @Header("Authorization") token: String
    ): Response<FolderListResponse>

    @GET("api/folders/starred")
    suspend fun getStarredFolders(
        @Header("Authorization") token: String
    ): Response<FolderListResponse>

    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("parentFolder") parentFolder: RequestBody? = null
    ): Response<CreateFileResponse>

    @POST("api/files")
    suspend fun createFile(
        @Header("Authorization") token: String,
        @Body request: CreateFileRequest
    ): Response<CreateFileResponse>

    @GET("api/files")
    suspend fun getFiles(
        @Header("Authorization") token: String,
        @Query("parentFolder") parentFolder: String? = null
    ): Response<FileListResponse>

    @PATCH("api/files/{id}/star")
    suspend fun toggleStarFile(
        @Header("Authorization") token: String,
        @Path("id") fileId: String
    ): Response<CreateFileResponse>

    @GET("api/files/trashed")
    suspend fun getTrashedFiles(
        @Header("Authorization") token: String
    ): Response<FileListResponse>

    @GET("api/files/starred")
    suspend fun getStarredFiles(
        @Header("Authorization") token: String
    ): Response<FileListResponse>
}
