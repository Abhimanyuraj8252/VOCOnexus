package com.voconexus.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CreateProject : Screen("create_project")
    object ProjectDetail : Screen("project_detail/{projectId}") {
        fun createRoute(projectId: String) = "project_detail/$projectId"
    }
    object ScriptEditor : Screen("script_editor/{projectId}") {
        fun createRoute(projectId: String) = "script_editor/$projectId"
    }
    object ScriptAnalysis : Screen("script_analysis/{projectId}") {
        fun createRoute(projectId: String) = "script_analysis/$projectId"
    }
    object Voices : Screen("voices")
    object Models : Screen("models")
    object GenerationQueue : Screen("generation_queue/{projectId}") {
        fun createRoute(projectId: String) = "generation_queue/$projectId"
    }
    object AudioLibrary : Screen("audio_library")
    object FullPlayer : Screen("full_player")
    object AudioDetails : Screen("audio_details/{chunkId}") {
        fun createRoute(chunkId: String) = "audio_details/$chunkId"
    }
    object LanguageVoiceReview : Screen("language_voice_review/{projectId}") {
        fun createRoute(projectId: String) = "language_voice_review/$projectId"
    }
    object PronunciationDictionary : Screen("pronunciation_dictionary/{projectId}") {
        fun createRoute(projectId: String) = "pronunciation_dictionary/$projectId"
    }
    object Onboarding : Screen("onboarding")
    object Privacy : Screen("privacy")
    object DeviceDashboard : Screen("device_dashboard")
    object LicenseAttribution : Screen("license_attribution")
    object Settings : Screen("settings")
    object Tools : Screen("tools")
    object SpeedPitchController : Screen("speed_pitch_controller")
}
