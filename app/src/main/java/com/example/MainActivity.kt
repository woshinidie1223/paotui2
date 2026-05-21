package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.AuthScreen
import com.example.ui.AuthViewModel
import com.example.ui.AuthViewModelFactory
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Fetch application dependency context singletons
        val errandApp = application as ErrandApplication
        val repository = errandApp.repository

        // Create ViewModels with custom Factories and keep lifecycle-safe
        val authViewModel = ViewModelProvider(this, AuthViewModelFactory(repository))[AuthViewModel::class.java]
        val mainViewModel = ViewModelProvider(this, MainViewModelFactory(repository))[MainViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val currentUser by authViewModel.currentUser.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val user = currentUser
                    if (user == null) {
                        AuthScreen(
                            authViewModel = authViewModel,
                            onAuthSuccess = { email, name ->
                                mainViewModel.setUserEmail(email)
                            }
                        )
                    } else {
                        MainScreen(
                            mainViewModel = mainViewModel,
                            userEmail = user.email,
                            userNickname = user.nickname,
                            onLogout = { authViewModel.logout() }
                        )
                    }
                }
            }
        }
    }
}
