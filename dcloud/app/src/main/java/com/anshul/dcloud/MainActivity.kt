package com.anshul.dcloud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anshul.dcloud.network.RetrofitClient
import com.anshul.dcloud.network.models.GitHubAuthRequest
import com.anshul.dcloud.ui.AuthScreen
import com.anshul.dcloud.ui.HomeScreen
import com.anshul.dcloud.ui.SplashScreen
import com.anshul.dcloud.ui.theme.DcloudTheme
import com.anshul.dcloud.utils.SharedPrefManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefManager: SharedPrefManager
    private var navControllerRef: NavController? = null
    private var isAuthProcessingState = mutableStateOf(false)
    private var authErrorMessageState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefManager = SharedPrefManager(this)

        setContent {
            DcloudTheme {
                val navController = rememberNavController()
                navControllerRef = navController

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
                        val isLoading by isAuthProcessingState
                        val errorMessage by authErrorMessageState

                        AuthScreen(
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onGitHubOAuthClick = {
                                isAuthProcessingState.value = true
                                authErrorMessageState.value = null
                                openGitHubOAuthBrowser()
                            },
                            onDirectGitHubSignIn = { username ->
                                isAuthProcessingState.value = true
                                authErrorMessageState.value = null
                                authenticateWithGitHubRequest(
                                    request = GitHubAuthRequest(username = username),
                                    onComplete = { success, token, user, msg ->
                                        isAuthProcessingState.value = false
                                        if (success && token != null && user != null) {
                                            prefManager.saveAuthToken(token)
                                            prefManager.saveUser(user.name, user.email, user.avatar)
                                            navController.navigate("home") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            authErrorMessageState.value = msg ?: "Authentication failed"
                                        }
                                    }
                                )
                            }
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

        handleOAuthDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthDeepLink(intent)
    }

    private fun openGitHubOAuthBrowser() {
        val clientId = "Ov23liTcXt2TBlgDWYQg"
        val redirectUri = "dcloud://oauth"
        val oauthUrl = "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=user:email"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl))
        startActivity(intent)
    }

    private fun handleOAuthDeepLink(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "dcloud" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrEmpty()) {
                isAuthProcessingState.value = true
                authErrorMessageState.value = null
                authenticateWithGitHubRequest(
                    request = GitHubAuthRequest(code = code),
                    onComplete = { success, token, user, msg ->
                        isAuthProcessingState.value = false
                        if (success && token != null && user != null) {
                            prefManager.saveAuthToken(token)
                            prefManager.saveUser(user.name, user.email, user.avatar)
                            navControllerRef?.navigate("home") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            authErrorMessageState.value = msg ?: "GitHub OAuth failed"
                        }
                    }
                )
            }
        }
    }

    private fun authenticateWithGitHubRequest(
        request: GitHubAuthRequest,
        onComplete: (Boolean, String?, com.anshul.dcloud.network.models.UserDto?, String?) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiInterface.githubAuth(request)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    onComplete(body.success, body.token, body.user, body.message)
                } else {
                    onComplete(false, null, null, "Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                onComplete(false, null, null, "Network error: ${e.localizedMessage}")
            }
        }
    }
}