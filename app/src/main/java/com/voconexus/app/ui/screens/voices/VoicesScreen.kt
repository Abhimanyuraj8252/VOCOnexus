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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.voconexus.app.core.data.db.TtsVoiceEntity
import com.voconexus.app.ui.components.VocoNexusEmptyState
import com.voconexus.app.ui.components.VocoNexusTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    viewModel: VoicesViewModel,
    onBackClick: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateModels: () -> Unit,
    onNavigateAudio: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val voices by viewModel.voicesState.collectAsState()
    val selectedLang by viewModel.selectedLanguage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val context = LocalContext.current
    var playingVoiceId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            VocoNexusTopBar(
                title = "Voice Catalog",
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // === HEADER GRADIENT CARD ===
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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Neural Voices",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${voices.size} voices • ${viewModel.availableLanguages.size - 2} languages",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // === SEARCH BAR ===
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search voices, language, locale...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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

            // === PREMIUM LANGUAGE FILTER BUTTON ===
            var showLanguageSheet by remember { mutableStateOf(false) }
            val currentLangOpt = viewModel.availableLanguages.find { it.code == selectedLang } ?: viewModel.availableLanguages[0]

            Surface(
                onClick = { showLanguageSheet = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "Language",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Language Filter",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${currentLangOpt.flag} ${currentLangOpt.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showLanguageSheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showLanguageSheet = false },
                    sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "Select Language",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(viewModel.availableLanguages, key = { it.code }) { langOpt ->
                                val isSelected = selectedLang == langOpt.code
                                Surface(
                                    onClick = {
                                        viewModel.selectLanguageFilter(langOpt.code)
                                        showLanguageSheet = false
                                    },
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = langOpt.flag,
                                            fontSize = 24.sp
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = langOpt.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === VOICE COUNT ROW ===
            if (voices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${voices.size} voices found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedModelId.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Model: $selectedModelId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // === VOICE LIST ===
            if (voices.isEmpty()) {
                VocoNexusEmptyState(
                    title = "No Voices Found",
                    description = "No voices match the selected language or search. Try changing the filter.",
                    icon = Icons.Default.MicNone
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(voices, key = { it.id }) { voice ->
                        VoiceCard(
                            voice = voice,
                            isPlaying = playingVoiceId == voice.id,
                            onPlayClick = {
                                if (playingVoiceId == voice.id) {
                                    // Stop
                                    val container = (context.applicationContext as com.voconexus.app.VocoNexusApplication).container
                                    container.audioPreviewPlayer.stop()
                                    playingVoiceId = null
                                } else {
                                    playingVoiceId = voice.id
                                    viewModel.playVoicePreview(context, voice)
                                    // Auto-clear playing state after 10s
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        if (playingVoiceId == voice.id) playingVoiceId = null
                                    }, 10_000)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    voice: TtsVoiceEntity,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    val isFemale = voice.gender.contains("FEMALE", ignoreCase = true)
    val isHindi = voice.language.equals("hi", ignoreCase = true)
    val isEnglish = voice.language.startsWith("en", ignoreCase = true)

    val engineLabel = when (voice.engineId) {
        "kokoro-82m" -> "Kokoro"
        "piper-onnx" -> "Piper"
        "sherpa-onnx" -> "Sherpa"
        else -> voice.engineId.take(8)
    }

    val cardBorderColor = if (isPlaying)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPlaying) 1.01f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .border(
                width = if (isPlaying) 1.5.dp else 0.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPlaying) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
            // === AVATAR CIRCLE ===
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
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
                    text = getVoiceLanguageFlag(voice.language),
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // === VOICE INFO ===
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voice.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isFemale) Icons.Default.Female else Icons.Default.Male,
                        contentDescription = null,
                        tint = if (isFemale) Color(0xFFE91E63) else Color(0xFF1565C0),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Language badge
                    LangBadge(
                        text = "${getVoiceLanguageFlag(voice.language)} ${voice.locale.ifBlank { voice.language.uppercase() }}",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    // Engine badge
                    LangBadge(
                        text = engineLabel,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    // Multilingual badge for Hindi Kokoro
                    if (isHindi && voice.engineId == "kokoro-82m") {
                        LangBadge(
                            text = "🔀 Multi",
                            color = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                IconButton(onClick = onPlayClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = if (isPlaying) Color.White
                               else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LangBadge(text: String, color: Color, textColor: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun getVoiceLanguageFlag(language: String): String {
    return when (language.lowercase()) {
        "en"  -> "🇺🇸"
        "en-gb", "en_gb" -> "🇬🇧"
        "hi"  -> "🇮🇳"
        "fr"  -> "🇫🇷"
        "es"  -> "🇪🇸"
        "de"  -> "🇩🇪"
        "it"  -> "🇮🇹"
        "pt"  -> "🇧🇷"
        "ja"  -> "🇯🇵"
        "ko"  -> "🇰🇷"
        "zh"  -> "🇨🇳"
        "ar"  -> "🇸🇦"
        "ru"  -> "🇷🇺"
        "nl"  -> "🇳🇱"
        "pl"  -> "🇵🇱"
        "tr"  -> "🇹🇷"
        "uk"  -> "🇺🇦"
        "vi"  -> "🇻🇳"
        "el"  -> "🇬🇷"
        "sv"  -> "🇸🇪"
        "da"  -> "🇩🇰"
        "nb", "no" -> "🇳🇴"
        "fi"  -> "🇫🇮"
        "cs"  -> "🇨🇿"
        "ro"  -> "🇷🇴"
        "hu"  -> "🇭🇺"
        "sk"  -> "🇸🇰"
        "ca"  -> "🏳️"
        "sr"  -> "🇷🇸"
        "hr"  -> "🇭🇷"
        "is"  -> "🇮🇸"
        "af"  -> "🇿🇦"
        "sw"  -> "🇰🇪"
        "bn"  -> "🇧🇩"
        "gu"  -> "🇮🇳"
        "te"  -> "🇮🇳"
        "ta"  -> "🇮🇳"
        "kn"  -> "🇮🇳"
        "mr"  -> "🇮🇳"
        "pa"  -> "🇮🇳"
        else  -> "🌐"
    }
}
