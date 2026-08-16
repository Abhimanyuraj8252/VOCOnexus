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
    onNavigateSpeedPitch: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Audio & Video", "Voice", "Utilities")

    val toolsList = remember {
        listOf(
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
                id = "format_converter",
                title = "Format Converter & Compressor",
                description = "Convert any media between MP3, WAV, AAC, FLAC, MP4, MKV, AVI & compress file size.",
                icon = Icons.Default.Transform,
                category = "Audio & Video",
                tags = listOf("Converter", "Compressor", "FFmpeg"),
                isAvailable = false,
                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
            ),
            ToolItem(
                id = "audio_merger",
                title = "Audio Trimmer & Merger",
                description = "Cut, splice, and combine multiple audio or video tracks into a single seamless output file.",
                icon = Icons.Default.ContentCut,
                category = "Audio & Video",
                tags = listOf("Trim", "Merge", "Concat"),
                isAvailable = false,
                gradientColors = listOf(Color(0xFFEC4899), Color(0xFFF43F5E))
            ),
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
                id = "vocal_remover",
                title = "Vocal & Instrument Separator",
                description = "Separate vocal tracks from music instruments to create karaoke, acapella, or backing tracks.",
                icon = Icons.Default.MicOff,
                category = "Voice",
                tags = listOf("Stem Splitter", "Karaoke", "Acapella"),
                isAvailable = false,
                gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
            )
        )
    }

    val filteredTools = remember(selectedCategory) {
        if (selectedCategory == "All") toolsList
        else toolsList.filter { it.category == selectedCategory || selectedCategory == "All" }
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
                    title = if (selectedCategory == "All") "Available Tools" else "$selectedCategory Tools",
                    actionText = "${filteredTools.size} Tools"
                )
            }

            // Tools Items
            items(filteredTools, key = { it.id }) { tool ->
                ToolCardItem(
                    tool = tool,
                    onLaunch = {
                        if (tool.id == "speed_pitch") {
                            onNavigateSpeedPitch()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolCardItem(
    tool: ToolItem,
    onLaunch: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = tool.isAvailable, onClick = onLaunch),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
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
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(tool.gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tool.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = tool.gradientColors.first()
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (tool.isAvailable) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Coming Soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            // Tags row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tool.tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (tool.isAvailable) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tool.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
