package com.anshul.dcloud.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPrefManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dcloud_prefs", Context.MODE_PRIVATE)

    fun saveAuthToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString("jwt_token", null)
    }

    fun saveUser(name: String, email: String, avatar: String?) {
        prefs.edit().apply {
            putString("user_name", name)
            putString("user_email", email)
            putString("user_avatar", avatar ?: "")
            apply()
        }
    }

    fun getUserName(): String? = prefs.getString("user_name", null)

    fun getUserEmail(): String? = prefs.getString("user_email", null)

    fun getUserAvatar(): String? = prefs.getString("user_avatar", null).takeIf { !it.isNullOrEmpty() }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
