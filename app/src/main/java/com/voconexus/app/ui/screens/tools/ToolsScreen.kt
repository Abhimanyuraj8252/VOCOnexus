package com.voconexus.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voconexus.app.ui.components.VocoNexusCard
import com.voconexus.app.ui.components.VocoNexusSectionHeader

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: String,
    val tags: List<String>,
    val isAvailable: Boolean,
    val gradientColors: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateSpeedPitch: () -> Unit,
    onNavigateAudioExtractor: () -> Unit,
    onNavigateFormatConverter: () -> Unit,
    onNavigateTrimmerMerger: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Audio & Video", "Voice", "Utilities")

    // Tools List - Strictly Alphabetical Sorting (A to Z)
    val rawToolsList = remember {
        listOf(
            ToolItem(
                id = "noise_reduction",
                title = "AI Noise Reduction & Cleaner",
                description = "Remove background noise, hums, and hiss from recorded audio using AI algorithms.",
                icon = Icons.Default.GraphicEq,
                category = "Voice",
                tags = listOf("Denoiser", "AI Clean", "Enhance"),
                isAvailable = false,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669))
            ),
            ToolItem(
                id = "audio_extractor",
                title = "Audio Extractor (Video to Audio)",
                description = "Extract audio from any video (MP4, MKV, AVI, MOV) into MP3, AAC, WAV, FLAC with 0.1s instant copy & trim.",
                icon = Icons.Default.Audiotrack,
                category = "Audio & Video",
                tags = listOf("Extract", "Video2Audio", "MP3", "Lossless"),
                isAvailable = true,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669))
            ),
            ToolItem(
                id = "format_converter",
                title = "Format Converter & Compressor",
                description = "Convert any video/audio between MP4, MKV, AVI, MOV, WebM, MP3, AAC, WAV, FLAC with CRF size compression.",
                icon = Icons.Default.Transform,
                category = "Audio & Video",
                tags = listOf("Converter", "Compressor", "Estimator", "TargetMB"),
                isAvailable = true,
                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
            ),
            ToolItem(
                id = "audio_merger",
                title = "Media Trimmer & Merger",
                description = "Precision cut, splice, and combine multiple audio or video tracks into a single seamless output file.",
                icon = Icons.Default.ContentCut,
                category = "Audio & Video",
                tags = listOf("Trim", "Merge", "Concat", "AutoSplit"),
                isAvailable = true,
                gradientColors = listOf(Color(0xFFEC4899), Color(0xFFF43F5E))
            ),
            ToolItem(
                id = "speed_pitch",
                title = "Speed & Pitch Controller",
                description = "Change speed (0.1x–8.0x), pitch semitones, trim A/B, fade in/out, 5-band EQ & export to any format.",
                icon = Icons.Default.Tune,
                category = "Audio & Video",
                tags = listOf("Speed", "Pitch", "Trim", "EQ", "Export"),
                isAvailable = true,
                gradientColors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
            ),
            ToolItem(
                id = "vocal_remover",
                title = "Vocal & Instrument Separator",
                description = "Separate vocal tracks from music instruments to create karaoke, acapella, or backing tracks.",
                icon = Icons.Default.MicOff,
                category = "Voice",
                tags = listOf("Separate", "Karaoke", "Stem"),
                isAvailable = false,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFFA855F7))
            )
        )
    }

    // Alphabetical Sorting Guaranteed
    val filteredTools = remember(selectedCategory) {
        val list = if (selectedCategory == "All") rawToolsList
        else rawToolsList.filter { it.category == selectedCategory }
        list.sortedBy { it.title }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tools Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Audio & Video Processing Suite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Section Header
            item {
                VocoNexusSectionHeader(
                    title = if (selectedCategory == "All") "Available Tools (A-Z)" else "$selectedCategory Tools (A-Z)",
                    actionText = "${filteredTools.size} Tools"
                )
            }

            // Tools Items
            items(filteredTools, key = { it.id }) { tool ->
                ToolCardItem(
                    tool = tool,
                    onLaunch = {
                        when (tool.id) {
                            "speed_pitch" -> onNavigateSpeedPitch()
                            "audio_extractor" -> onNavigateAudioExtractor()
                            "format_converter" -> onNavigateFormatConverter()
                            "audio_merger" -> onNavigateTrimmerMerger()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ToolCardItem(
    tool: ToolItem,
    onLaunch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tool.isAvailable) { onLaunch() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tool.isAvailable)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (tool.isAvailable)
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    if (tool.isAvailable) tool.gradientColors
                                    else listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.2f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (tool.isAvailable)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (tool.isAvailable)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = tool.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tool.isAvailable)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (!tool.isAvailable) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Coming Soon",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (tool.isAvailable) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Tool",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (tool.isAvailable)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tool.tags.forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
