package com.anshul.dcloud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGitHubOAuthClick: () -> Unit,
    onDirectGitHubSignIn: (username: String) -> Unit
) {
    var showDirectInput by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DCloud",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Secure Cloud Storage Platform",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign in with GitHub",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Authorize DCloud with your GitHub account",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = onGitHubOAuthClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E))
                        ) {
                            Text(
                                text = "Sign in with GitHub",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!showDirectInput) {
//                            TextButton(onClick = { showDirectInput = true }) {
//                                Text(text = "Use GitHub Username", fontSize = 14.sp)
//                            }
                        } else {
                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("GitHub Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (usernameInput.isNotBlank()) {
                                        onDirectGitHubSignIn(usernameInput.trim())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continue")
                            }
                        }
                    }

                    if (!errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
