package com.voconexus.app.ui.screens.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voconexus.app.core.tts.catalog.DefaultModelProvider
import com.voconexus.app.core.tts.catalog.LicenseInfo

data class AttributionItem(
    val title: String,
    val category: String,
    val license: LicenseInfo
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseAttributionScreen(
    onBackClick: () -> Unit
) {
    val items = listOf(
        AttributionItem(
            title = "Kokoro 82M TTS Model",
            category = "Neural Text-To-Speech Model",
            license = DefaultModelProvider.kokoroLicense
        ),
        AttributionItem(
            title = "Piper On-Device TTS Engine",
            category = "Speech Synthesis Engine",
            license = LicenseInfo(
                licenseName = "MIT License",
                licenseUrl = "https://opensource.org/licenses/MIT",
                attributionRequired = true,
                commercialUse = "Permitted",
                redistribution = true,
                modification = true,
                notes = "Fast local speech synthesizer engine."
            )
        ),
        AttributionItem(
            title = "Jetpack Compose & Material 3",
            category = "Android UI Framework",
            license = LicenseInfo(
                licenseName = "Apache-2.0",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                attributionRequired = true,
                commercialUse = "Permitted",
                redistribution = true,
                modification = true
            )
        ),
        AttributionItem(
            title = "AndroidX Room & Media3",
            category = "Database & Audio Playback Framework",
            license = LicenseInfo(
                licenseName = "Apache-2.0",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                attributionRequired = true,
                commercialUse = "Permitted",
                redistribution = true,
                modification = true
            )
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Source & Model Attributions") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "VocoNexus is built on open-source neural TTS models and libraries. Below are the license terms and attribution requirements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(items) { item ->
                AttributionRowCard(item)
            }
        }
    }
}

@Composable
private fun AttributionRowCard(item: AttributionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("License: ${item.license.licenseName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("Commercial Use: ${item.license.commercialUse}", style = MaterialTheme.typography.bodySmall)
            Text("Attribution Required: ${if (item.license.attributionRequired) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
            if (item.license.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.license.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
