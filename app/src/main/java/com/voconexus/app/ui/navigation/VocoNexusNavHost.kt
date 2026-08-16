package com.voconexus.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.voconexus.app.VocoNexusApplication
import com.voconexus.app.ui.screens.createproject.CreateProjectScreen
import com.voconexus.app.ui.screens.createproject.CreateProjectViewModel
import com.voconexus.app.ui.screens.home.HomeScreen
import com.voconexus.app.ui.screens.home.HomeViewModel
import com.voconexus.app.ui.screens.models.ModelsScreen
import com.voconexus.app.ui.screens.models.ModelsViewModel
import com.voconexus.app.ui.screens.projectdetail.ProjectDetailScreen
import com.voconexus.app.ui.screens.projectdetail.ProjectDetailViewModel
import com.voconexus.app.ui.screens.scriptanalysis.ScriptAnalysisScreen
import com.voconexus.app.ui.screens.scriptanalysis.ScriptAnalysisViewModel
import com.voconexus.app.ui.screens.scripteditor.ScriptEditorScreen
import com.voconexus.app.ui.screens.scripteditor.ScriptEditorViewModel
import com.voconexus.app.ui.screens.settings.SettingsScreen
import com.voconexus.app.ui.screens.settings.SettingsViewModel
import com.voconexus.app.ui.screens.voices.VoicesScreen
import com.voconexus.app.ui.screens.voices.VoicesViewModel

@Composable
fun VocoNexusNavHost(
    navController: NavHostController,
    app: VocoNexusApplication,
    modifier: Modifier = Modifier
) {
    val container = app.container
    val context = androidx.compose.ui.platform.LocalContext.current
    val userPrefsManager = remember { com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context) }
    val initialRoute = remember {
        if (!userPrefsManager.preferences.value.isOnboardingCompleted) {
            Screen.Onboarding.route
        } else {
            Screen.Home.route
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = modifier
    ) {
        // Destination: Home
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    projectRepository = container.projectRepository,
                    preferencesRepository = container.preferencesRepository,
                    storageManager = container.storageManager
                )
            )

            HomeScreen(
                viewModel = viewModel,
                onCreateProjectClick = {
                    navController.navigate(Screen.CreateProject.route)
                },
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                },
                onNavigateVoices = { navController.navigate(Screen.Voices.route) },
                onNavigateModels = { navController.navigate(Screen.Models.route) },
                onNavigateAudio = { navController.navigate(Screen.AudioLibrary.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateSpeedPitch = { navController.navigate(Screen.SpeedPitchController.route) }
            )
        }

        // Destination: Create Project
        composable(Screen.CreateProject.route) {
            val viewModel: CreateProjectViewModel = viewModel(
                factory = CreateProjectViewModel.Factory(
                    projectRepository = container.projectRepository,
                    voiceRepository = container.voiceRepository,
                    userPrefsManager = container.userPreferencesManager
                )
            )

            BackHandler {
                navController.popBackStack()
            }

            CreateProjectScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onProjectCreated = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        // Destination: Project Detail
        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: ProjectDetailViewModel = viewModel(
                factory = ProjectDetailViewModel.Factory(
                    projectId = projectId,
                    projectRepository = container.projectRepository,
                    generationRepository = container.generationRepository,
                    audioAssetDao = container.database.audioAssetDao(),
                    voiceRepository = container.voiceRepository,
                    userPrefsManager = container.userPreferencesManager
                )
            )

            BackHandler {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }

            ProjectDetailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateScriptEditor = { id ->
                    navController.navigate(Screen.ScriptEditor.createRoute(id))
                },
                onNavigateGenerationQueue = { id ->
                    navController.navigate(Screen.GenerationQueue.createRoute(id))
                }
            )
        }

        // Destination: Script Editor
        composable(
            route = Screen.ScriptEditor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: ScriptEditorViewModel = viewModel(
                factory = ScriptEditorViewModel.Factory(
                    projectId = projectId,
                    projectRepository = container.projectRepository,
                    fileImportManager = container.fileImportManager,
                    preprocessingEngine = container.textPreprocessingEngine,
                    durationEstimator = container.durationEstimator
                )
            )

            ScriptEditorScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Script Analysis
        composable(
            route = Screen.ScriptAnalysis.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: ScriptAnalysisViewModel = viewModel(
                factory = ScriptAnalysisViewModel.Factory(
                    projectId = projectId,
                    projectRepository = container.projectRepository
                )
            )

            ScriptAnalysisScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onCommitSuccess = {
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId)) {
                        popUpTo(Screen.ProjectDetail.route) { inclusive = true }
                    }
                }
            )
        }

        // Destination: Voices
        composable(Screen.Voices.route) {
            val viewModel: com.voconexus.app.ui.screens.voices.VoiceBrowserViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.voices.VoiceBrowserViewModel.provideFactory(
                    ttsRepository = container.ttsRepository,
                    voicePreviewManager = container.voicePreviewManager,
                    prefsManager = userPrefsManager
                )
            )

            BackHandler {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }

            com.voconexus.app.ui.screens.voices.VoiceBrowserScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Models
        composable(Screen.Models.route) {
            val viewModel: com.voconexus.app.ui.screens.models.ModelManagerViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.models.ModelManagerViewModel.provideFactory(
                    ttsRepository = container.ttsRepository,
                    deviceEvaluator = container.deviceEvaluator,
                    prefsManager = userPrefsManager
                )
            )

            BackHandler {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }

            com.voconexus.app.ui.screens.models.ModelManagerScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Generation Queue
        composable(
            route = Screen.GenerationQueue.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: com.voconexus.app.ui.screens.generation.GenerationQueueViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.generation.GenerationQueueViewModel.provideFactory(
                    projectId = projectId,
                    generationRepository = container.generationRepository,
                    projectRepository = container.projectRepository
                )
            )

            com.voconexus.app.ui.screens.generation.GenerationQueueScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Audio Library
        composable(Screen.AudioLibrary.route) {
            val viewModel: com.voconexus.app.ui.screens.audiolibrary.AudioLibraryViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.audiolibrary.AudioLibraryViewModel.Factory(
                    audioRepository = container.audioRepository,
                    projectRepository = container.projectRepository,
                    playbackController = container.playbackController,
                    audioExporter = container.audioExporter,
                    storageManager = container.storageManager
                )
            )

            BackHandler {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }

            com.voconexus.app.ui.screens.audiolibrary.AudioLibraryScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateAudioDetails = { chunkId ->
                    navController.navigate(Screen.AudioDetails.createRoute(chunkId))
                }
            )
        }

        // Destination: Audio Details
        composable(
            route = Screen.AudioDetails.route,
            arguments = listOf(
                navArgument("chunkId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chunkId = backStackEntry.arguments?.getString("chunkId") ?: ""
            
            val chunkState = androidx.compose.runtime.produceState<com.voconexus.app.core.data.db.ChunkEntity?>(initialValue = null, key1 = chunkId) {
                value = container.database.chunkDao().getChunkById(chunkId)
            }
            val assetState = androidx.compose.runtime.produceState<com.voconexus.app.core.data.db.AudioAssetEntity?>(initialValue = null, key1 = chunkId) {
                value = container.database.audioAssetDao().getAssetForChunk(chunkId)
            }
            
            com.voconexus.app.ui.screens.audiolibrary.AudioDetailsScreen(
                chunk = chunkState.value,
                asset = assetState.value,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Full Player
        composable(Screen.FullPlayer.route) {
            com.voconexus.app.ui.screens.playback.FullPlayerScreen(
                playbackController = container.playbackController,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Onboarding
        composable(Screen.Onboarding.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val userPrefsManager = remember { com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context) }
            com.voconexus.app.ui.screens.onboarding.OnboardingScreen(
                onFinishOnboarding = {
                    userPrefsManager.setOnboardingCompleted(true)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Destination: Privacy
        composable(Screen.Privacy.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val userPrefsManager = remember { com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context) }
            val storageManager = remember { com.voconexus.app.core.storage.AudioStorageManager(context) }
            val diagExporter = remember { com.voconexus.app.core.error.DiagnosticsExporter(context, com.voconexus.app.core.storage.StorageManager(context)) }
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    userPreferencesManager = userPrefsManager,
                    storageManager = storageManager,
                    diagnosticsExporter = diagExporter
                )
            )

            com.voconexus.app.ui.screens.privacy.PrivacyScreen(
                onBackClick = { navController.popBackStack() },
                onResetDataConfirm = {
                    viewModel.resetAllData(context)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Destination: Settings
        composable(Screen.Settings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val userPrefsManager = remember { com.voconexus.app.core.preferences.UserPreferencesManager.getInstance(context) }
            val diagExporter = remember { com.voconexus.app.core.error.DiagnosticsExporter(context, com.voconexus.app.core.storage.StorageManager(context)) }
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    userPreferencesManager = userPrefsManager,
                    storageManager = container.storageManager,
                    diagnosticsExporter = diagExporter
                )
            )

            BackHandler {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }

            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateHome = { navController.navigate(Screen.Home.route) },
                onNavigateVoices = { navController.navigate(Screen.Voices.route) },
                onNavigateModels = { navController.navigate(Screen.Models.route) },
                onNavigateAudio = { navController.navigate(Screen.AudioLibrary.route) },
                onNavigatePrivacy = { navController.navigate(Screen.Privacy.route) },
                onNavigateAttribution = { navController.navigate(Screen.LicenseAttribution.route) }
            )
        }

        // Destination: Language & Voice Review
        composable(
            route = Screen.LanguageVoiceReview.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: com.voconexus.app.ui.screens.multilingual.LanguageVoiceViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.multilingual.LanguageVoiceViewModel.Factory(
                    projectId = projectId,
                    projectRepository = container.projectRepository,
                    voiceRepository = container.voiceRepository
                )
            )

            com.voconexus.app.ui.screens.multilingual.LanguageVoiceReviewScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Pronunciation Dictionary
        composable(
            route = Screen.PronunciationDictionary.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: com.voconexus.app.ui.screens.speech.PronunciationViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.speech.PronunciationViewModel.Factory(
                    projectId = projectId,
                    database = container.database
                )
            )

            com.voconexus.app.ui.screens.speech.PronunciationDictionaryScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Device & Performance Dashboard
        composable(route = Screen.DeviceDashboard.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val evaluator = remember { com.voconexus.app.core.tts.device.DeviceProfileEvaluator(context) }
            val thermalMonitor = remember { com.voconexus.app.core.tts.device.ThermalMonitor(context) }
            val storageManager = remember { com.voconexus.app.core.storage.StorageManager(context) }

            val viewModel: com.voconexus.app.ui.screens.device.DeviceDashboardViewModel = viewModel(
                factory = com.voconexus.app.ui.screens.device.DeviceDashboardViewModel.Factory(
                    evaluator = evaluator,
                    thermalMonitor = thermalMonitor,
                    storageManager = storageManager,
                    database = container.database
                )
            )

            com.voconexus.app.ui.screens.device.DeviceDashboardScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: License & Attribution
        composable(route = Screen.LicenseAttribution.route) {
            com.voconexus.app.ui.screens.models.LicenseAttributionScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Destination: Tools Hub
        composable(route = Screen.Tools.route) {
            com.voconexus.app.ui.screens.tools.ToolsScreen(
                onNavigateSpeedPitch = { navController.navigate(Screen.SpeedPitchController.route) }
            )
        }

        // Destination: Speed & Pitch Controller Tool
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
        composable(route = Screen.SpeedPitchController.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: com.voconexus.app.ui.screens.speedpitch.SpeedPitchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.voconexus.app.ui.screens.speedpitch.SpeedPitchViewModel.Factory(context)
            )
            com.voconexus.app.ui.screens.speedpitch.SpeedPitchScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
