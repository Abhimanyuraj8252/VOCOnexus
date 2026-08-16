package com.voconexus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.voconexus.app.core.preferences.UserPreferencesManager
import com.voconexus.app.ui.navigation.VocoNexusNavHost
import com.voconexus.app.ui.theme.VocoNexusTheme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.currentBackStackEntryAsState
import com.voconexus.app.ui.components.VocoNexusBottomNav
import com.voconexus.app.ui.navigation.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VocoNexusApplication

        setContent {
            val userPrefsManager = remember { UserPreferencesManager.getInstance(applicationContext) }
            val userPrefs by userPrefsManager.preferences.collectAsState()

            VocoNexusTheme(
                themeMode = userPrefs.themeMode,
                isHighContrast = userPrefs.isHighContrastEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    val bottomNavRoutes = listOf(
                        Screen.Home.route,
                        Screen.Voices.route,
                        Screen.Models.route,
                        Screen.Tools.route,
                        Screen.AudioLibrary.route,
                        Screen.Settings.route
                    )

                    Scaffold(
                        bottomBar = {
                            if (currentRoute in bottomNavRoutes) {
                                VocoNexusBottomNav(
                                    currentRoute = currentRoute ?: Screen.Home.route,
                                    onNavigateHome = { navController.navigate(Screen.Home.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    onNavigateVoices = { navController.navigate(Screen.Voices.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    onNavigateModels = { navController.navigate(Screen.Models.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    onNavigateTools = { navController.navigate(Screen.Tools.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    onNavigateAudio = { navController.navigate(Screen.AudioLibrary.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    onNavigateSettings = { navController.navigate(Screen.Settings.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } }
                                )
                            }
                        }
                    ) { innerPadding ->
                        VocoNexusNavHost(
                            navController = navController,
                            app = app,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
