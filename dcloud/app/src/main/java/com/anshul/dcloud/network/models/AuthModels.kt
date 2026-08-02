package com.anshul.dcloud.network.models

data class GitHubAuthRequest(
    val code: String? = null,
    val username: String? = null,
    val email: String? = null,
    val githubId: String? = null,
    val avatar: String? = null
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
