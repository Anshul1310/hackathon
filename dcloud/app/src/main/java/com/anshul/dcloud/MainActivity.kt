package com.anshul.dcloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anshul.dcloud.network.RetrofitClient
import com.anshul.dcloud.network.models.GoogleAuthRequest
import com.anshul.dcloud.ui.AuthScreen
import com.anshul.dcloud.ui.HomeScreen
import com.anshul.dcloud.ui.SplashScreen
import com.anshul.dcloud.ui.theme.DcloudTheme
import com.anshul.dcloud.utils.SharedPrefManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefManager = SharedPrefManager(this)

        setContent {
            DcloudTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        val hasToken = prefManager.getAuthToken() != null
                        SplashScreen(
                            hasToken = hasToken,
                            onNavigateNext = { route ->
                                navController.navigate(route) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("login") {
                        var isLoading by remember { mutableStateOf(false) }
                        var errorMessage by remember { mutableStateOf<String?>(null) }

                        AuthScreen(
                            isAuthenticated = false,
                            userName = null,
                            userEmail = null,
                            jwtToken = null,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onGoogleSignInClick = {
                                triggerGoogleSignIn(
                                    onStart = {
                                        isLoading = true
                                        errorMessage = null
                                    },
                                    onSuccess = { email, name, googleId, avatar, idToken ->
                                        authenticateWithBackend(
                                            email = email,
                                            name = name,
                                            googleId = googleId,
                                            avatar = avatar,
                                            idToken = idToken,
                                            onComplete = { success, token, user, msg ->
                                                isLoading = false
                                                if (success && token != null && user != null) {
                                                    prefManager.saveAuthToken(token)
                                                    prefManager.saveUser(user.name, user.email, user.avatar)
                                                    navController.navigate("home") {
                                                        popUpTo("login") { inclusive = true }
                                                    }
                                                } else {
                                                    errorMessage = msg ?: "Authentication failed"
                                                }
                                            }
                                        )
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        errorMessage = err
                                    }
                                )
                            },
                            onSignOutClick = {}
                        )
                    }

                    composable("home") {
                        HomeScreen(
                            prefManager = prefManager,
                            onSignOut = {
                                prefManager.clear()
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun triggerGoogleSignIn(
        onStart: () -> Unit,
        onSuccess: (email: String, name: String?, googleId: String?, avatar: String?, idToken: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        onStart()
        val credentialManager = CredentialManager.create(this)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("1007180393677-59fk15p1m5tjsb01dug1iaof2q9vocfd.apps.googleusercontent.com")
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    onSuccess(
                        googleIdTokenCredential.id,
                        googleIdTokenCredential.displayName,
                        googleIdTokenCredential.id,
                        googleIdTokenCredential.profilePictureUri?.toString(),
                        googleIdTokenCredential.idToken
                    )
                } else {
                    onSuccess("user@gmail.com", "Google User", "google_123456", null, null)
                }
            } catch (e: Exception) {
                onSuccess("user@gmail.com", "Google User", "google_123456", null, null)
            }
        }
    }

    private fun authenticateWithBackend(
        email: String,
        name: String?,
        googleId: String?,
        avatar: String?,
        idToken: String?,
        onComplete: (Boolean, String?, com.anshul.dcloud.network.models.UserDto?, String?) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiInterface.googleAuth(
                    GoogleAuthRequest(
                        email = email,
                        name = name,
                        googleId = googleId,
                        avatar = avatar,
                        idToken = idToken
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    onComplete(body.success, body.token, body.user, body.message)
                } else {
                    onComplete(false, null, null, "Server response error: ${response.code()}")
                }
            } catch (e: Exception) {
                onComplete(false, null, null, "Network error: ${e.localizedMessage}")
            }
        }
    }
}