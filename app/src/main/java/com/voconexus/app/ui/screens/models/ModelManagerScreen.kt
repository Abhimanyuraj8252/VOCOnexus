package com.voconexus.app.ui.screens.models

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voconexus.app.core.security.ProviderCategory
import com.voconexus.app.core.tts.ModelStatus
import com.voconexus.app.core.tts.TtsModel
import com.voconexus.app.core.tts.device.CompatibilityLevel
import com.voconexus.app.core.tts.device.CompatibilityReport
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // State for API Key & Custom Endpoint Dialogs
    var selectedProviderForApiKey by remember { mutableStateOf<ProviderUiModel?>(null) }
    var showAddCustomEndpointDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Model & Engine Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.activeTab == 0) "API & AI Models Hub (55+ Providers)" else "Hardware Profiling & Active Engine Selection",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.activeTab == 0) {
                        IconButton(
                            onClick = { viewModel.syncAllCatalogs() },
                            enabled = !uiState.isGlobalSyncing
                        ) {
                            if (uiState.isGlobalSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "Sync All Catalogs")
                            }
                        }
                        IconButton(onClick = { showAddCustomEndpointDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Custom Endpoint")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Navigation Tabs
            TabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("API & Models Hub", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Local Engines", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // ── STICKY SEARCH & CATEGORY FILTER BAR ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (uiState.activeTab == 0) "Search 55+ providers (Ollama, OpenRouter, ElevenLabs...)"
                                else "Search local engines (Kokoro, Sherpa-ONNX, Ollama...)"
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val totalCount = if (uiState.activeTab == 0) uiState.providers.size else uiState.models.size
                        FilterChip(
                            selected = uiState.selectedCategory == null,
                            onClick = { viewModel.setSelectedCategory(null) },
                            label = { Text("All ($totalCount)") }
                        )
                        ProviderCategory.entries.forEach { category ->
                            val count = if (uiState.activeTab == 0) {
                                uiState.providers.count { it.category == category }
                            } else {
                                uiState.models.count { model ->
                                    when (category) {
                                        ProviderCategory.LOCAL_ENGINE -> model.engineId.contains("kokoro") || model.engineId.contains("onnx") || model.engineId.contains("sherpa") || model.engineId.contains("piper")
                                        ProviderCategory.TRANSLATOR_LLM -> model.engineId.contains("ollama") || model.engineId.contains("lmstudio") || model.engineId.contains("vllm")
                                        ProviderCategory.CLOUD_TTS -> !model.engineId.contains("kokoro") && !model.engineId.contains("onnx") && !model.engineId.contains("sherpa") && !model.engineId.contains("ollama") && !model.engineId.contains("lmstudio")
                                        ProviderCategory.CUSTOM -> model.engineId.contains("custom")
                                    }
                                }
                            }
                            if (count > 0 || uiState.activeTab == 0) {
                                FilterChip(
                                    selected = uiState.selectedCategory == category,
                                    onClick = { viewModel.setSelectedCategory(if (uiState.selectedCategory == category) null else category) },
                                    label = { Text("${category.displayName} ($count)") }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.activeTab == 0) {
                // ── TAB 0: API & AI MODELS HUB ──
                val filteredProviders = remember(uiState.providers, uiState.selectedCategory, uiState.searchQuery) {
                    uiState.providers.filter { provider ->
                        val matchesCategory = uiState.selectedCategory == null || provider.category == uiState.selectedCategory
                        val matchesSearch = uiState.searchQuery.isBlank() ||
                                provider.name.contains(uiState.searchQuery, ignoreCase = true) ||
                                provider.description.contains(uiState.searchQuery, ignoreCase = true)
                        matchesCategory && matchesSearch
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Global Sync Banner
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Encrypted Key Vault", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "API keys are stored securely using AES-256 GCM in Android EncryptedSharedPreferences.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { viewModel.syncAllCatalogs() },
                                    enabled = !uiState.isGlobalSyncing,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Sync All")
                                }
                            }
                        }
                    }

                    items(filteredProviders, key = { it.id }) { provider ->
                        ProviderCard(
                            provider = provider,
                            onConfigureClick = { selectedProviderForApiKey = provider },
                            onSyncClick = { viewModel.syncProvider(provider.id) },
                            onDeleteCustomClick = { viewModel.deleteCustomEndpoint(provider.id) },
                            onOpenWebsiteClick = {
                                if (provider.websiteUrl.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.websiteUrl))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            } else {
                // ── TAB 1: LOCAL ENGINES & HARDWARE PROFILING ──
                val filteredModels = remember(uiState.models, uiState.selectedCategory, uiState.searchQuery) {
                    uiState.models.filter { model ->
                        val matchesSearch = uiState.searchQuery.isBlank() ||
                                model.name.contains(uiState.searchQuery, ignoreCase = true) ||
                                model.engineId.contains(uiState.searchQuery, ignoreCase = true) ||
                                model.version.contains(uiState.searchQuery, ignoreCase = true)
                        val cat = uiState.selectedCategory
                        val matchesCategory = cat == null || when (cat) {
                            ProviderCategory.LOCAL_ENGINE -> model.engineId.contains("kokoro") || model.engineId.contains("onnx") || model.engineId.contains("sherpa") || model.engineId.contains("piper")
                            ProviderCategory.TRANSLATOR_LLM -> model.engineId.contains("ollama") || model.engineId.contains("lmstudio") || model.engineId.contains("vllm")
                            ProviderCategory.CLOUD_TTS -> !model.engineId.contains("kokoro") && !model.engineId.contains("onnx") && !model.engineId.contains("sherpa") && !model.engineId.contains("ollama") && !model.engineId.contains("lmstudio")
                            ProviderCategory.CUSTOM -> model.engineId.contains("custom")
                        }
                        matchesSearch && matchesCategory
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Device Hardware Profile Card
                    uiState.deviceProfile?.let { profile ->
                        item {
                            HardwareProfileCard(
                                ramGb = profile.totalRamMb / 1024,
                                cores = profile.cpuCores,
                                abi = profile.primaryAbi,
                                storageMb = profile.availableStorageMb
                            )
                        }
                    }

                    // 2. RTF Benchmark Result Banner
                    uiState.lastBenchmarkResult?.let { result ->
                        item {
                            BenchmarkResultCard(result = result)
                        }
                    }

                    // 3. Section Title
                    item {
                        Text(
                            text = "Installed & Available Engines (${filteredModels.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // 4. Models List
                    items(filteredModels, key = { it.id }) { model ->
                        val report = uiState.compatibilityReports[model.id]
                        ManagerModelCard(
                            model = model,
                            report = report,
                            isInstallingThis = uiState.installingModelId == model.id,
                            installProgress = uiState.installProgress,
                            isRunningBenchmark = uiState.isRunningBenchmark,
                            isActiveModel = uiState.activeModelId == model.id,
                            onInstallClick = { viewModel.installModel(model.id) },
                            onDeleteClick = { viewModel.deleteModel(model.id) },
                            onBenchmarkClick = { viewModel.runBenchmark(model.id) },
                            onSetActiveClick = { viewModel.setActiveModel(model.id) }
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // API Key & Endpoint Configuration Dialog
    selectedProviderForApiKey?.let { provider ->
        ApiKeyConfigurationDialog(
            provider = provider,
            onDismiss = { selectedProviderForApiKey = null },
            onSave = { apiKey, customBaseUrl, customModelId ->
                viewModel.saveProviderConfig(provider.id, apiKey, customBaseUrl, customModelId)
                selectedProviderForApiKey = null
            },
            onDelete = {
                viewModel.deleteApiKey(provider.id)
                selectedProviderForApiKey = null
            }
        )
    }

    // Add Custom Endpoint Dialog
    if (showAddCustomEndpointDialog) {
        AddCustomEndpointDialog(
            onDismiss = { showAddCustomEndpointDialog = false },
            onSave = { name, baseUrl, apiKey, category ->
                viewModel.saveCustomEndpoint(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    engineType = category.name
                )
                showAddCustomEndpointDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// API Hub Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProviderCard(
    provider: ProviderUiModel,
    onConfigureClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDeleteCustomClick: () -> Unit,
    onOpenWebsiteClick: () -> Unit
) {
    val categoryColor = when (provider.category) {
        ProviderCategory.LOCAL_ENGINE -> Color(0xFF3B82F6)
        ProviderCategory.CLOUD_TTS -> Color(0xFF6366F1)
        ProviderCategory.TRANSLATOR_LLM -> Color(0xFF10B981)
        ProviderCategory.CUSTOM -> Color(0xFF8B5CF6)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (provider.category) {
                                ProviderCategory.LOCAL_ENGINE -> Icons.Default.GraphicEq
                                ProviderCategory.CLOUD_TTS -> Icons.Default.RecordVoiceOver
                                ProviderCategory.TRANSLATOR_LLM -> Icons.Default.Translate
                                ProviderCategory.CUSTOM -> Icons.Default.Dns
                            },
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = provider.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // API Key Configured Badge
                if (provider.hasApiKey) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Secured Key", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (provider.requiresKey) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("No Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text("No Key Required", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Status message / sync results
            provider.syncStatusMessage?.let { status ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Status: $status",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.contains("OK") || status.contains("synced")) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (provider.websiteUrl.isNotBlank()) {
                        IconButton(onClick = onOpenWebsiteClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Get API Key", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (provider.isCustom) {
                        IconButton(onClick = onDeleteCustomClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Endpoint", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onSyncClick,
                        enabled = !provider.isSyncing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (provider.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Catalog", modifier = Modifier.size(18.dp))
                        }
                    }

                    if (provider.requiresKey) {
                        Button(
                            onClick = onConfigureClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (provider.hasApiKey) "Edit Key" else "Add Key", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyConfigurationDialog(
    provider: ProviderUiModel,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, customBaseUrl: String, customModelId: String) -> Unit,
    onDelete: () -> Unit
) {
    var apiKeyText by remember { mutableStateOf("") }
    var customBaseUrlText by remember { mutableStateOf(provider.customBaseUrl ?: "") }
    var customModelIdText by remember { mutableStateOf(provider.customModelId ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configure ${provider.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Configure settings and credentials for ${provider.name}. Stored securely using Android EncryptedSharedPreferences.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (provider.requiresKey) {
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("API Key ${if (provider.requiresKey) "*" else "(Optional)"}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("API Key (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = customBaseUrlText,
                    onValueChange = { customBaseUrlText = it },
                    label = { Text("Custom Base URL (Optional)") },
                    placeholder = { Text(provider.defaultBaseUrl.ifEmpty { "https://api.provider.com/v1" }) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = customModelIdText,
                    onValueChange = { customModelIdText = it },
                    label = { Text("Custom Model ID / Space (Optional)") },
                    placeholder = { Text("e.g. gpt-4o, facebook/m2m100_418M, or space-name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (provider.hasApiKey || (provider.customBaseUrl != null && provider.customBaseUrl.isNotBlank()) || (provider.customModelId != null && provider.customModelId.isNotBlank())) {
                    Text(
                        "Status: Configured & Encrypted",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKeyText.trim(), customBaseUrlText.trim(), customModelIdText.trim()) },
                enabled = !provider.requiresKey || apiKeyText.isNotBlank() || provider.hasApiKey || customBaseUrlText.isNotBlank() || customModelIdText.isNotBlank()
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            Row {
                if (provider.hasApiKey || (provider.customBaseUrl != null && provider.customBaseUrl.isNotBlank())) {
                    TextButton(onClick = onDelete) {
                        Text("Reset / Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun AddCustomEndpointDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String, category: ProviderCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ProviderCategory>(ProviderCategory.CLOUD_TTS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Custom AI Endpoint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Connect custom self-hosted engines (vLLM, Ollama, XTTS, OpenVoice server, local OpenAI-compatible endpoints).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Endpoint Name (e.g. My Local vLLM)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (e.g. http://192.168.1.50:8000/v1)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Bearer Token (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Text("Provider Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), baseUrl.trim(), apiKey.trim(), selectedCategory) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text("Add Endpoint")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Local Engines Components & Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HardwareProfileCard(ramGb: Int, cores: Int, abi: String, storageMb: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text("System Hardware Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${ramGb}GB RAM • $cores Cores CPU • $abi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Free Storage: ${storageMb / 1024} GB",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(result: com.voconexus.app.core.tts.device.BenchmarkResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Local RTF Benchmark Result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                Text(
                    text = "Real-Time Factor: ${String.format(Locale.US, "%.2f", result.realTimeFactor)}x • Duration: ${result.synthesisDurationMs}ms • RAM: ${result.peakMemoryMb}MB",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ManagerModelCard(
    model: TtsModel,
    report: CompatibilityReport?,
    isInstallingThis: Boolean,
    installProgress: Float,
    isRunningBenchmark: Boolean,
    isActiveModel: Boolean,
    onInstallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBenchmarkClick: () -> Unit,
    onSetActiveClick: () -> Unit
) {
    val isCloudOrSystem = model.id in setOf("edge-tts", "google-cloud-tts", "android-tts") || model.sizeBytes == 0L
    val effectiveStatus = if (isCloudOrSystem) ModelStatus.INSTALLED else model.status
    val sizeMb = if (isCloudOrSystem) "Cloud / System Engine" else "~${model.sizeBytes / (1024 * 1024)} MB"

    val gradientColors = if (isActiveModel) {
        listOf(Color(0xFF10B981), Color(0xFF059669))
    } else if (isCloudOrSystem) {
        listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
    } else {
        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    }

    val cardBorder = if (isActiveModel) {
        BorderStroke(2.dp, Color(0xFF10B981))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = cardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header Row: Icon + Name + Active/Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActiveModel) Icons.Default.CheckCircle else if (isCloudOrSystem) Icons.Default.CloudQueue else Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Engine: ${model.engineId} • v${model.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (isActiveModel) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF10B981)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Active Engine", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        val labelText = when {
                            model.id == "android-tts" -> "Built-in System"
                            isCloudOrSystem -> "Online Cloud"
                            effectiveStatus == ModelStatus.INSTALLED -> "Installed"
                            else -> "Not Installed"
                        }
                        Text(labelText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // Info Chips Row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChipInfoItem(icon = Icons.Default.SdStorage, text = sizeMb)
                ChipInfoItem(icon = Icons.Default.RecordVoiceOver, text = "${model.voicesCount} Voices")
                ChipInfoItem(icon = Icons.Default.Language, text = "${model.supportedLanguages.size} Languages")
            }

            report?.let { comp ->
                if (!isCloudOrSystem) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val compColor = if (comp.level == CompatibilityLevel.RECOMMENDED || comp.level == CompatibilityLevel.COMPATIBLE) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        Icon(Icons.Default.Speed, contentDescription = null, tint = compColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Compatibility: ${comp.level.name} (${comp.recommendedRtf})",
                            style = MaterialTheme.typography.labelSmall,
                            color = compColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isInstallingThis) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { installProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                val phaseText = if (installProgress < 0.7f) {
                    "Downloading model weights: ${(installProgress / 0.7f * 100).toInt()}%"
                } else {
                    "Finalizing model setup..."
                }
                Text(phaseText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons Row: Set Active, Benchmark, Delete, Install
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (effectiveStatus == ModelStatus.INSTALLED || isCloudOrSystem) {
                    if (!isActiveModel) {
                        Button(
                            onClick = onSetActiveClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Set Active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    if (!isCloudOrSystem) {
                        OutlinedButton(
                            onClick = onBenchmarkClick,
                            enabled = !isRunningBenchmark,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isRunningBenchmark) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Benchmark", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Model", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Button(
                        onClick = onInstallClick,
                        enabled = !isInstallingThis,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Install Model", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipInfoItem(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

