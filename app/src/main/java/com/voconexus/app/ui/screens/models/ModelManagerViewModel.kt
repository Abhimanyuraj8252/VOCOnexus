package com.voconexus.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voconexus.app.core.data.repository.TtsRepository
import com.voconexus.app.core.network.DynamicCatalogFetcher
import com.voconexus.app.core.preferences.UserPreferencesManager
import com.voconexus.app.core.security.ApiVaultManager
import com.voconexus.app.core.security.CustomEndpointConfig
import com.voconexus.app.core.security.ProviderCategory
import com.voconexus.app.core.security.ProviderMetadata
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.device.BenchmarkResult
import com.voconexus.app.core.tts.device.CompatibilityReport
import com.voconexus.app.core.tts.device.DeviceProfile
import com.voconexus.app.core.tts.device.DeviceProfileEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProviderUiModel(
    val id: String,
    val name: String,
    val category: ProviderCategory,
    val description: String,
    val requiresKey: Boolean = true,
    val defaultBaseUrl: String = "",
    val websiteUrl: String = "",
    val hasApiKey: Boolean = false,
    val isCustom: Boolean = false,
    val customBaseUrl: String? = null,
    val customModelId: String? = null,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val modelsCount: Int = 0,
    val voicesCount: Int = 0
)

data class ModelManagerUiState(
    val models: List<TtsModel> = emptyList(),
    val deviceProfile: DeviceProfile? = null,
    val compatibilityReports: Map<String, CompatibilityReport> = emptyMap(),
    val isInstalling: Boolean = false,
    val installingModelId: String? = null,
    val installProgress: Float = 0f,
    val isRunningBenchmark: Boolean = false,
    val lastBenchmarkResult: BenchmarkResult? = null,
    val activeModelId: String = "kokoro-v1.0",
    val errorMessage: String? = null,
    
    // API & AI Models Hub State
    val providers: List<ProviderUiModel> = emptyList(),
    val selectedCategory: ProviderCategory? = null,
    val searchQuery: String = "",
    val isGlobalSyncing: Boolean = false,
    val activeTab: Int = 0 // 0 = API Hub, 1 = Local Engines & Hardware
)

class ModelManagerViewModel(
    private val ttsRepository: TtsRepository,
    private val deviceEvaluator: DeviceProfileEvaluator,
    private val prefsManager: UserPreferencesManager? = null,
    private val apiVaultManager: ApiVaultManager? = null,
    private val dynamicCatalogFetcher: DynamicCatalogFetcher? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    private val _syncingProviders = MutableStateFlow<Set<String>>(emptySet())
    private val _providerStatuses = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        loadData()
        observeVaultAndCatalogs()

        // Load active model from prefs
        prefsManager?.preferences?.value?.selectedModelId?.let { savedId ->
            if (savedId.isNotBlank()) {
                _uiState.value = _uiState.value.copy(activeModelId = savedId)
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            val profile = ttsRepository.getDeviceProfile()
            ttsRepository.getAllModels().collect { models ->
                val reports = models.associate { model ->
                    model.id to deviceEvaluator.classifyCompatibility(profile, model.minRamMb)
                }

                _uiState.value = _uiState.value.copy(
                    models = models,
                    deviceProfile = profile,
                    compatibilityReports = reports
                )
            }
        }
    }

    private fun observeVaultAndCatalogs() {
        if (apiVaultManager == null) {
            updateProvidersList(emptySet(), emptyList())
            return
        }

        viewModelScope.launch {
            combine(
                apiVaultManager.configuredProvidersFlow,
                apiVaultManager.customEndpointsFlow,
                _syncingProviders,
                _providerStatuses
            ) { configured, customList, syncingSet, statusMap ->
                buildProviderUiModels(configured, customList, syncingSet, statusMap)
            }.collect { providers ->
                _uiState.value = _uiState.value.copy(providers = providers)
            }
        }
    }

    private fun buildProviderUiModels(
        configured: Set<String>,
        customList: List<CustomEndpointConfig>,
        syncingSet: Set<String>,
        statusMap: Map<String, String>
    ): List<ProviderUiModel> {
        val list = mutableListOf<ProviderUiModel>()

        // Built-in Providers
        ApiVaultManager.BUILT_IN_PROVIDERS.forEach { meta ->
            val hasKey = if (!meta.requiresKey) true else configured.contains(meta.id)
            val isSyncing = syncingSet.contains(meta.id)
            val status = statusMap[meta.id]
            val customBaseUrl = apiVaultManager?.getCustomBaseUrl(meta.id)
            val customModelId = apiVaultManager?.getCustomModelId(meta.id)

            list.add(
                ProviderUiModel(
                    id = meta.id,
                    name = meta.name,
                    category = meta.category,
                    description = meta.description,
                    requiresKey = meta.requiresKey,
                    defaultBaseUrl = meta.defaultBaseUrl,
                    websiteUrl = meta.websiteUrl,
                    hasApiKey = hasKey,
                    isCustom = false,
                    customBaseUrl = customBaseUrl,
                    customModelId = customModelId,
                    isSyncing = isSyncing,
                    syncStatusMessage = status
                )
            )
        }

        // Custom Endpoints
        customList.forEach { custom ->
            val category = try {
                ProviderCategory.valueOf(custom.engineType)
            } catch (e: Exception) {
                ProviderCategory.CUSTOM
            }

            val isSyncing = syncingSet.contains(custom.id)
            val status = statusMap[custom.id]

            list.add(
                ProviderUiModel(
                    id = custom.id,
                    name = custom.name,
                    category = category,
                    description = "Custom endpoint at ${custom.baseUrl}",
                    requiresKey = custom.apiKey.isNotBlank(),
                    defaultBaseUrl = custom.baseUrl,
                    hasApiKey = custom.apiKey.isNotBlank(),
                    isCustom = true,
                    customBaseUrl = custom.baseUrl,
                    isSyncing = isSyncing,
                    syncStatusMessage = status
                )
            )
        }

        return list
    }

    private fun updateProvidersList(configured: Set<String>, customList: List<CustomEndpointConfig>) {
        val providers = buildProviderUiModels(configured, customList, emptySet(), emptyMap())
        _uiState.value = _uiState.value.copy(providers = providers)
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch {
            apiVaultManager?.saveApiKey(providerId, apiKey)
            syncProvider(providerId)
        }
    }

    fun saveProviderConfig(
        providerId: String,
        apiKey: String,
        customBaseUrl: String = "",
        customModelId: String = ""
    ) {
        viewModelScope.launch {
            apiVaultManager?.saveProviderConfig(providerId, apiKey, customBaseUrl, customModelId)
            syncProvider(providerId)
        }
    }

    fun deleteApiKey(providerId: String) {
        viewModelScope.launch {
            apiVaultManager?.deleteApiKey(providerId)
            val currentStatuses = _providerStatuses.value.toMutableMap()
            currentStatuses.remove(providerId)
            _providerStatuses.value = currentStatuses
        }
    }

    fun saveCustomEndpoint(
        id: String = System.currentTimeMillis().toString(),
        name: String,
        baseUrl: String,
        apiKey: String = "",
        authHeaderName: String = "Authorization",
        engineType: String = "CLOUD_TTS"
    ) {
        viewModelScope.launch {
            val endpoint = CustomEndpointConfig(
                id = id,
                name = name,
                baseUrl = baseUrl,
                apiKey = apiKey,
                authHeaderName = authHeaderName,
                engineType = engineType
            )
            apiVaultManager?.saveCustomEndpoint(endpoint)
            dynamicCatalogFetcher?.verifyAndSyncCustomEndpoint(endpoint)?.let { result ->
                val statusMap = _providerStatuses.value.toMutableMap()
                statusMap[id] = result.message
                _providerStatuses.value = statusMap
            }
        }
    }

    fun deleteCustomEndpoint(endpointId: String) {
        viewModelScope.launch {
            apiVaultManager?.deleteCustomEndpoint(endpointId)
        }
    }

    fun syncProvider(providerId: String) {
        if (dynamicCatalogFetcher == null) return
        viewModelScope.launch {
            _syncingProviders.value = _syncingProviders.value + providerId
            try {
                val custom = apiVaultManager?.getCustomEndpoints()?.find { it.id == providerId }
                val result = if (custom != null) {
                    dynamicCatalogFetcher.verifyAndSyncCustomEndpoint(custom)
                } else {
                    dynamicCatalogFetcher.verifyAndSyncProvider(providerId)
                }

                val statusMap = _providerStatuses.value.toMutableMap()
                statusMap[providerId] = result.message
                _providerStatuses.value = statusMap
            } catch (e: Exception) {
                val statusMap = _providerStatuses.value.toMutableMap()
                statusMap[providerId] = e.message ?: "Sync error"
                _providerStatuses.value = statusMap
            } finally {
                _syncingProviders.value = _syncingProviders.value - providerId
            }
        }
    }

    fun syncAllCatalogs() {
        if (dynamicCatalogFetcher == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGlobalSyncing = true)
            try {
                val results = dynamicCatalogFetcher.syncAllConfiguredProviders()
                val statusMap = _providerStatuses.value.toMutableMap()
                results.forEach { res ->
                    statusMap[res.providerId] = res.message
                }
                _providerStatuses.value = statusMap
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Sync failed")
            } finally {
                _uiState.value = _uiState.value.copy(isGlobalSyncing = false)
            }
        }
    }

    fun setSelectedCategory(category: ProviderCategory?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setActiveTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tabIndex)
    }

    fun installModel(modelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isInstalling = true,
                installingModelId = modelId,
                installProgress = 0f,
                errorMessage = null
            )

            try {
                ttsRepository.installModel(modelId) { progress ->
                    _uiState.value = _uiState.value.copy(installProgress = progress)
                }
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    installingModelId = null,
                    installProgress = 1.0f
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    installingModelId = null,
                    errorMessage = e.message ?: "Failed to install model"
                )
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            try {
                ttsRepository.deleteModel(modelId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Failed to delete model")
            }
        }
    }

    fun runBenchmark(modelId: String, voiceId: String = "af_heart") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunningBenchmark = true)
            try {
                val result = ttsRepository.runBenchmark(modelId, voiceId)
                _uiState.value = _uiState.value.copy(
                    isRunningBenchmark = false,
                    lastBenchmarkResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningBenchmark = false,
                    errorMessage = e.message ?: "Benchmark failed"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setActiveModel(modelId: String) {
        val model = _uiState.value.models.find { it.id == modelId }
        prefsManager?.setSelectedModel(modelId, model?.engineId)
        _uiState.value = _uiState.value.copy(activeModelId = modelId)
    }

    companion object {
        fun provideFactory(
            ttsRepository: TtsRepository,
            deviceEvaluator: DeviceProfileEvaluator,
            prefsManager: UserPreferencesManager? = null,
            apiVaultManager: ApiVaultManager? = null,
            dynamicCatalogFetcher: DynamicCatalogFetcher? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ModelManagerViewModel(
                    ttsRepository = ttsRepository,
                    deviceEvaluator = deviceEvaluator,
                    prefsManager = prefsManager,
                    apiVaultManager = apiVaultManager,
                    dynamicCatalogFetcher = dynamicCatalogFetcher
                ) as T
            }
        }
    }
}
