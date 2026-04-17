package com.ssethhyy.chapter.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssethhyy.chapter.data.repository.SettingsRepository
import com.ssethhyy.chapter.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val folders by settingsViewModel.libraryFolders.collectAsState(initial = emptySet())
    val appFont by settingsViewModel.appFont.collectAsState()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                // Persist permission
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                settingsViewModel.addFolder(it.toString())
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Library Folders Section
            item {
                SettingsSection(title = "Library Folders") {
                    if (folders.isEmpty()) {
                        Text(
                            "No folders added yet.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        folders.forEach { folderUri ->
                            ListItem(
                                headlineContent = { Text(folderUri.substringAfterLast("%3A")) },
                                leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                trailingContent = {
                                    IconButton(onClick = { settingsViewModel.removeFolder(folderUri) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove")
                                    }
                                }
                            )
                        }
                    }
                    Button(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Folder")
                    }
                    
                    TextButton(
                        onClick = { settingsViewModel.clearLibrary() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Books from Library")
                    }
                }
            }

            // Navigation Section
            item {
                SettingsSection(title = "Navigation") {
                    ListItem(
                        headlineContent = { Text("Skip Mode") },
                        supportingContent = {
                            Text(if (uiState.isChapterSkipMode) "Chapter-based (Next/Prev)" else "Time-based (+/- 30s)")
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.isChapterSkipMode,
                                onCheckedChange = { playerViewModel.toggleChapterSkipMode() }
                            )
                        }
                    )
                }
            }

            // Skip Durations Section
            if (!uiState.isChapterSkipMode) {
                item {
                    SettingsSection(title = "Skip Durations") {
                        DurationPicker(
                            label = "Forward",
                            currentValue = uiState.skipForwardDuration,
                            onValueSelected = { playerViewModel.setSkipDurations(it, uiState.skipBackwardDuration) }
                        )
                        DurationPicker(
                            label = "Backward",
                            currentValue = uiState.skipBackwardDuration,
                            onValueSelected = { playerViewModel.setSkipDurations(uiState.skipForwardDuration, it) }
                        )
                    }
                }
            }

            // Appearance Section
            item {
                SettingsSection(title = "Appearance") {
                    Text(
                        "App Font",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsRepository.AppFont.values().forEach { font ->
                            val isSelected = appFont == font
                            Surface(
                                onClick = { settingsViewModel.setAppFont(font) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Aa",
                                        style = when(font) {
                                            SettingsRepository.AppFont.FIGTREE -> MaterialTheme.typography.titleLarge.copy(fontFamily = com.ssethhyy.chapter.ui.theme.FigtreeFontFamily)
                                            SettingsRepository.AppFont.MONTSERRAT -> MaterialTheme.typography.titleLarge.copy(fontFamily = com.ssethhyy.chapter.ui.theme.MontserratFontFamily)
                                            SettingsRepository.AppFont.GOOGLE_SANS -> MaterialTheme.typography.titleLarge.copy(fontFamily = com.ssethhyy.chapter.ui.theme.GoogleSansFontFamily)
                                            SettingsRepository.AppFont.SYSTEM -> MaterialTheme.typography.titleLarge
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = font.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    ListItem(
                        headlineContent = { Text("Now Playing Theme") },
                        supportingContent = {
                            Text(if (uiState.nowPlayingColorMode == com.ssethhyy.chapter.data.repository.SettingsRepository.ColorMode.ARTWORK) "Adaptive (Artwork)" else "System Theme")
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.nowPlayingColorMode == com.ssethhyy.chapter.data.repository.SettingsRepository.ColorMode.ARTWORK,
                                onCheckedChange = {
                                    playerViewModel.setNowPlayingColorMode(
                                        if (it) com.ssethhyy.chapter.data.repository.SettingsRepository.ColorMode.ARTWORK
                                        else com.ssethhyy.chapter.data.repository.SettingsRepository.ColorMode.SYSTEM
                                    )
                                }
                            )
                        }
                    )
                }
            }

            // Audio Section
            item {
                SettingsSection(title = "Audio") {
                    ListItem(
                        headlineContent = { Text("Silence Skipping") },
                        supportingContent = { Text("Automatically skip long pauses in audio") },
                        trailingContent = {
                            Switch(
                                checked = uiState.isSilenceSkippingEnabled,
                                onCheckedChange = { playerViewModel.toggleSilenceSkipping() }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPicker(
    label: String,
    currentValue: Long,
    onValueSelected: (Long) -> Unit
) {
    val options = listOf(10000L, 15000L, 30000L, 45000L, 60000L)
    Column(modifier = Modifier.padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { duration ->
                FilterChip(
                    selected = duration == currentValue,
                    onClick = { onValueSelected(duration) },
                    label = { Text("${duration / 1000}s") }
                )
            }
        }
    }
}

