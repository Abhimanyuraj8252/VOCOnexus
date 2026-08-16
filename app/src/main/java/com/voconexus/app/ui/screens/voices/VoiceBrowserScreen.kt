package com.voconexus.app.ui.screens.voices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voconexus.app.core.tts.TtsVoice
import com.voconexus.app.core.tts.VoiceGender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBrowserScreen(
    viewModel: VoiceBrowserViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

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
                            "Voice Catalog",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${uiState.filteredVoices.size} voices",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // === GRADIENT BANNER ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Neural Voices • ${viewModel.allLanguages.size - 2} Languages",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Active model: ${uiState.activeModelId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // === SEARCH BAR ===
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search voices, language, locale...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = uiState.searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            // === SCROLLABLE LANGUAGE FILTER CHIPS ===
            val langListState = rememberLazyListState()
            LazyRow(
                state = langListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(viewModel.allLanguages, key = { it.code }) { langOpt ->
                    val isSelected = uiState.selectedLanguage == langOpt.code
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "chipColor"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White
                                      else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "textColor"
                    )

                    Surface(
                        onClick = { viewModel.onLanguageSelected(langOpt.code) },
                        color = bgColor,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(36.dp),
                        shadowElevation = if (isSelected) 4.dp else 0.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = langOpt.flag, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = langOpt.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // === GENDER FILTER ROW ===
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    GenderChip("All", uiState.selectedGender == null) {
                        viewModel.onGenderSelected(null)
                    }
                }
                item {
                    GenderChip("♀ Female", uiState.selectedGender == VoiceGender.FEMALE) {
                        viewModel.onGenderSelected(VoiceGender.FEMALE)
                    }
                }
                item {
                    GenderChip("♂ Male", uiState.selectedGender == VoiceGender.MALE) {
                        viewModel.onGenderSelected(VoiceGender.MALE)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // === VOICE LIST ===
            if (uiState.filteredVoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MicNone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Voices Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Change the language filter or search query",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredVoices, key = { it.id }) { voice ->
                        val isPlaying = uiState.isPlayingPreview && uiState.playingVoiceId == voice.id
                        val isGenerating = uiState.isGeneratingPreview && uiState.playingVoiceId == voice.id

                        PremiumVoiceCard(
                            voice = voice,
                            isPlaying = isPlaying,
                            isGenerating = isGenerating,
                            onPlayPreviewClick = { viewModel.playVoicePreview(voice) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.allLanguages) { langOpt ->
                            val isSelected = uiState.selectedLanguage == langOpt.code
                            Surface(
                                onClick = {
                                    viewModel.onLanguageSelected(langOpt.code)
                                    showLanguageDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = langOpt.flag, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = langOpt.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    androidx.compose.material3.TextButton(
                        onClick = { showLanguageDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.surfaceVariant,
        label = "genderChipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "genderTextColor"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun PremiumVoiceCard(
    voice: TtsVoice,
    isPlaying: Boolean,
    isGenerating: Boolean,
    onPlayPreviewClick: () -> Unit
) {
    val isFemale = voice.gender == VoiceGender.FEMALE
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPlaying) 1.015f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    val engineLabel = when (voice.engineId) {
        "kokoro-82m"  -> "Kokoro"
        "piper-onnx"  -> "Piper"
        "sherpa-onnx" -> "Sherpa"
        else          -> voice.engineId.take(8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .border(
                width = if (isPlaying) 1.5.dp else 0.dp,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPlaying) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // === AVATAR ===
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (isFemale)
                                listOf(Color(0xFFE91E63), Color(0xFFAD1457))
                            else
                                listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                        )
                    )
            ) {
                Text(
                    text = getLangFlag(voice.language),
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // === INFO ===
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voice.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (voice.isDefault) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Default",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isFemale) Icons.Default.Female else Icons.Default.Male,
                        contentDescription = null,
                        tint = if (isFemale) Color(0xFFE91E63) else Color(0xFF1565C0),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SmallBadge(
                        text = "${getLangFlag(voice.language)} ${voice.locale.ifBlank { voice.language.uppercase() }}",
                        bg = MaterialTheme.colorScheme.secondaryContainer,
                        fg = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    SmallBadge(
                        text = when (voice.engineId) {
                            "edge-tts" -> "Edge Neural"
                            "google-cloud-tts" -> "Google Cloud"
                            "kokoro-82m" -> "Kokoro"
                            "sherpa-onnx" -> "Sherpa"
                            "piper-onnx" -> "Piper"
                            else -> voice.engineId
                        },
                        bg = MaterialTheme.colorScheme.tertiaryContainer,
                        fg = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    val isMulti = voice.name.contains("Multilingual", ignoreCase = true) ||
                        voice.name.contains("Hindi", ignoreCase = true) ||
                        voice.name.contains("Multi", ignoreCase = true) ||
                        voice.locale.contains("IN", ignoreCase = true) ||
                        voice.language == "hi"
                    if (isMulti) {
                        SmallBadge(
                            text = "Multilingual ✨",
                            bg = MaterialTheme.colorScheme.primaryContainer,
                            fg = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // === PLAY BUTTON ===
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                IconButton(
                    onClick = onPlayPreviewClick,
                    enabled = !isGenerating
                ) {
                    when {
                        isGenerating -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        isPlaying    -> Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        else         -> Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallBadge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun getLangFlag(language: String): String = when (language.lowercase()) {
    "en", "en-us"  -> "🇺🇸"
    "en-gb"        -> "🇬🇧"
    "hi"           -> "🇮🇳"
    "fr"           -> "🇫🇷"
    "es"           -> "🇪🇸"
    "de"           -> "🇩🇪"
    "it"           -> "🇮🇹"
    "pt"           -> "🇧🇷"
    "ja"           -> "🇯🇵"
    "ko"           -> "🇰🇷"
    "zh"           -> "🇨🇳"
    "ar"           -> "🇸🇦"
    "ru"           -> "🇷🇺"
    "nl"           -> "🇳🇱"
    "pl"           -> "🇵🇱"
    "tr"           -> "🇹🇷"
    "uk"           -> "🇺🇦"
    "vi"           -> "🇻🇳"
    "el"           -> "🇬🇷"
    "sv"           -> "🇸🇪"
    "da"           -> "🇩🇰"
    "nb", "no"     -> "🇳🇴"
    "fi"           -> "🇫🇮"
    "cs"           -> "🇨🇿"
    "ro"           -> "🇷🇴"
    "hu"           -> "🇭🇺"
    "sk"           -> "🇸🇰"
    "ca"           -> "🏳️"
    "sr"           -> "🇷🇸"
    "hr"           -> "🇭🇷"
    "is"           -> "🇮🇸"
    "af"           -> "🇿🇦"
    "sw"           -> "🇰🇪"
    "bn"           -> "🇧🇩"
    "gu", "te", "ta", "kn", "mr", "pa" -> "🇮🇳"
    else           -> "🌐"
}
