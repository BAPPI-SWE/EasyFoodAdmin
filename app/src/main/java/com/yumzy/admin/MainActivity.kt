package com.yumzy.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.admin.auth.GoogleAuthUiClient
import com.yumzy.admin.navigation.AppNavigation
import com.yumzy.admin.screens.AuthScreen
import com.yumzy.admin.ui.theme.YumzyAdminTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YumzyAdminTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(navController)
                        }
                        composable("auth") {
                            val launcher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartIntentSenderForResult()
                            ) { result ->
                                if (result.resultCode == RESULT_OK) {
                                    lifecycleScope.launch {
                                        val signInResult = googleAuthUiClient.signInWithIntent(
                                            intent = result.data ?: return@launch
                                        )
                                        if(signInResult.data != null) {
                                            checkAdminStatus(navController)
                                        } else {
                                            Toast.makeText(applicationContext, signInResult.errorMessage ?: "Sign-in failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            AuthScreen(onSignInClick = {
                                lifecycleScope.launch {
                                    val signInIntentSender = googleAuthUiClient.signIn()
                                    launcher.launch(IntentSenderRequest.Builder(signInIntentSender ?: return@launch).build())
                                }
                            })
                        }
                        composable("not_admin") {
                            AccessDeniedScreen()
                        }
                        composable("main_app") {
                            AppNavigation()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SplashScreen(navController: NavController) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        LaunchedEffect(key1 = Unit) {
            if (googleAuthUiClient.getSignedInUser() == null) {
                navController.navigate("auth") { popUpTo("splash") { inclusive = true } }
            } else {
                checkAdminStatus(navController)
            }
        }
    }

    @Composable
    private fun AccessDeniedScreen() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Access Denied. You are not an authorized admin.")
        }
    }

    private fun checkAdminStatus(navController: NavController) {
        val userEmail = Firebase.auth.currentUser?.email
        if (userEmail == null) {
            navController.navigate("not_admin") { popUpTo("splash") { inclusive = true } }
            return
        }

        Firebase.firestore.collection("admins").whereEqualTo("email", userEmail).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    navController.navigate("not_admin") { popUpTo("splash") { inclusive = true } }
                } else {
                    navController.navigate("main_app") { popUpTo("splash") { inclusive = true } }
                }
            }
            .addOnFailureListener {
                Toast.makeText(applicationContext, "Error checking admin status.", Toast.LENGTH_SHORT).show()
                navController.navigate("not_admin") { popUpTo("splash") { inclusive = true } }
            }
    }
}

