package com.anshul.dcloud.network.models

data class GoogleAuthRequest(
    val email: String,
    val name: String? = null,
    val googleId: String? = null,
    val avatar: String? = null,
    val idToken: String? = null
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val avatar: String? = null
)

data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserDto? = null,
    val message: String? = null
)
