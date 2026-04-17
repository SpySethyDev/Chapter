package com.ssethhyy.chapter.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ssethhyy.chapter.data.local.entities.Book
import com.ssethhyy.chapter.ui.theme.ChapterTheme
import com.ssethhyy.chapter.ui.theme.DynamicBookTheme
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    book: Book,
    sharedKeyPrefix: String = "mini",
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(book.id) {
        viewModel.loadBook(book, startPlayback = false)
    }

    LaunchedEffect(book.coverArtPath) {
        if (book.coverArtPath != null) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(book.coverArtPath)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                coverBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }
        } else {
            coverBitmap = null
        }
    }

    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = PlayerState.Expanded,
            anchors = DraggableAnchors {
                PlayerState.Expanded at 0f
                PlayerState.Dismissed at screenHeightPx
            },
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            decayAnimationSpec = exponentialDecay()
        )
    }

    LaunchedEffect(anchoredDraggableState.currentValue) {
        if (anchoredDraggableState.currentValue == PlayerState.Dismissed) {
            onDismiss()
        } else if (anchoredDraggableState.currentValue == PlayerState.Expanded) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val nestedScrollConnection = remember(anchoredDraggableState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta < 0 && anchoredDraggableState.offset > 0f) {
                    val consumedY = anchoredDraggableState.dispatchRawDelta(delta)
                    Offset(0f, consumedY)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                return if (source == NestedScrollSource.UserInput && delta > 0) {
                    val consumedY = anchoredDraggableState.dispatchRawDelta(delta)
                    Offset(0f, consumedY)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                anchoredDraggableState.settle(available.y)
                return super.onPostFling(consumed, available)
            }
        }
    }

    val colorMode = uiState.nowPlayingColorMode

    DynamicBookTheme(
        bitmap = if (colorMode == com.ssethhyy.chapter.data.repository.SettingsRepository.ColorMode.ARTWORK) coverBitmap else null
    ) {
        val backgroundColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (anchoredDraggableState.requireOffset() / screenHeightPx)).coerceIn(0f, 0.6f)))
                .nestedScroll(nestedScrollConnection)
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Vertical
                )
                .offset {
                    IntOffset(
                        x = 0,
                        y = anchoredDraggableState
                            .requireOffset()
                            .roundToInt()
                    )
                }
        ) {
            PlayerContent(
                uiState = uiState,
                backgroundColor = backgroundColor,
                sharedKeyPrefix = sharedKeyPrefix,
                onTogglePlayPause = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.togglePlayPause()
                },
                onSeek = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.seekTo(it)
                },
                onSkipForward = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.skipForward()
                },
                onSkipBackward = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.skipBackward()
                },
                onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onQuickSleepTimer = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.addSleepTimer(15) 
                },
                onAddBookmark = { viewModel.addBookmark(it) },
                onToggleSilenceSkipping = { viewModel.toggleSilenceSkipping() },
                onSetSkipDurations = { f, b -> viewModel.setSkipDurations(f, b) },
                isChapterSkipMode = uiState.isChapterSkipMode,
                onToggleChapterSkipMode = { viewModel.toggleChapterSkipMode() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onToggleShuffle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleShuffle()
                },
                onToggleRepeatMode = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleRepeatMode()
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
}

enum class PlayerState {
    Expanded, Dismissed
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerContent(
    uiState: PlayerUiState,
    backgroundColor: Color,
    sharedKeyPrefix: String,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onQuickSleepTimer: () -> Unit,
    onAddBookmark: (String) -> Unit,
    onToggleSilenceSkipping: () -> Unit,
    onSetSkipDurations: (Long, Long) -> Unit,
    isChapterSkipMode: Boolean,
    onToggleChapterSkipMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSkipSettingsDialog by remember { mutableStateOf(false) }
    val chapterListState = rememberLazyListState()
    
    // Get system dynamic color scheme for dialogs
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val systemColorScheme = remember(context, darkTheme) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Blurred Background
        AsyncImage(
            model = uiState.book?.coverArtPath,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )

        // Gradient overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        LazyColumn(
            state = chapterListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Drag Handle (Swipe down indicator)
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left spacer for centering "Now Playing"
                        Box(modifier = Modifier.size(48.dp))
                        
                        Text(
                            "Now Playing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showSkipSettingsDialog = true },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Cover Art
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(horizontal = 8.dp)
                            .shadow(24.dp, RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        if (uiState.book != null) {
                            with(sharedTransitionScope) {
                                AsyncImage(
                                    model = uiState.book.coverArtPath,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedBounds(
                                            rememberSharedContentState(key = "${sharedKeyPrefix}_cover_${uiState.book.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
                                        )
                                        .clip(RoundedCornerShape(32.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Title and Author
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (uiState.book != null) {
                            with(sharedTransitionScope) {
                                Text(
                                    text = uiState.book.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    modifier = Modifier.sharedElement(
                                        rememberSharedContentState(key = "${sharedKeyPrefix}_title_${uiState.book.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                )
                                Text(
                                    text = uiState.book.author,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.sharedElement(
                                        rememberSharedContentState(key = "${sharedKeyPrefix}_author_${uiState.book.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress Bar
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = uiState.currentPosition.toFloat(),
                                onValueChange = { onSeek(it.toLong()) },
                                valueRange = 0f..(uiState.duration.coerceAtLeast(1L).toFloat()),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(uiState.currentPosition),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatTime(uiState.duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Controls (Focal Point)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Backward (Pill)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp)
                                .clip(RoundedCornerShape(45.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .clickable { onSkipBackward() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isChapterSkipMode) Icons.Rounded.SkipPrevious else Icons.Rounded.Replay10,
                                contentDescription = "Skip Back",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Play/Pause (Squircle)
                        val playPauseInteractionScale by animateFloatAsState(
                            targetValue = if (uiState.isPlaying) 1.05f else 1f,
                            label = "PlayPauseScale"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(110.dp)
                                .graphicsLayer {
                                    scaleX = playPauseInteractionScale
                                    scaleY = playPauseInteractionScale
                                }
                                .clip(RoundedCornerShape(40.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .combinedClickable(
                                    onClick = { onTogglePlayPause() },
                                    onLongClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSpeedDialog = true 
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Skip Forward (Pill)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp)
                                .clip(RoundedCornerShape(45.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .clickable { onSkipForward() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isChapterSkipMode) Icons.Rounded.SkipNext else Icons.Rounded.Forward30,
                                contentDescription = "Skip Forward",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Bottom Extra Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                modifier = Modifier.size(24.dp),
                                tint = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onToggleRepeatMode) {
                            val repeatIcon = when (uiState.repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                else -> Icons.Rounded.Repeat
                            }
                            Icon(
                                repeatIcon,
                                contentDescription = "Repeat",
                                modifier = Modifier.size(24.dp),
                                tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (uiState.book?.isFavorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                modifier = Modifier.size(24.dp),
                                tint = if (uiState.book?.isFavorite == true) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // These icons follow the selected color mode (Artwork or System)
                        IconButton(onClick = { showSpeedDialog = true }) {
                            Icon(
                                Icons.Rounded.Speed, 
                                contentDescription = "Speed", 
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.combinedClickable(
                                onClick = { showSleepTimerDialog = true },
                                onLongClick = onQuickSleepTimer
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Timer, 
                                contentDescription = "Timer", 
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.chapters.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No chapters found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            "This book doesn't contain chapter markers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            val distinctChapters = uiState.chapters.distinctBy { it.startTime }
            itemsIndexed(distinctChapters) { index, chapter ->
                ChapterItem(
                    title = chapter.title,
                    startTime = chapter.startTime,
                    isActive = index == uiState.currentChapterIndex,
                    onClick = { onSeek(chapter.startTime) }
                )
            }
        }
    }

    if (showSpeedDialog) {
        MaterialTheme(colorScheme = systemColorScheme) {
            PlaybackSpeedDialog(
                currentSpeed = uiState.playbackSpeed,
                onSpeedSelected = {
                    onSetSpeed(it)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
    }

    if (showSleepTimerDialog) {
        MaterialTheme(colorScheme = systemColorScheme) {
            SleepTimerDialog(
                currentTimeLeft = uiState.sleepTimerMillis,
                onTimerSelected = {
                    onSetSleepTimer(it)
                    showSleepTimerDialog = false
                },
                onDismiss = { showSleepTimerDialog = false }
            )
        }
    }

    if (showSkipSettingsDialog) {
        MaterialTheme(colorScheme = systemColorScheme) {
            SkipSettingsDialog(
                currentForward = uiState.skipForwardDuration,
                currentBackward = uiState.skipBackwardDuration,
                isSilenceSkippingEnabled = uiState.isSilenceSkippingEnabled,
                isChapterSkipMode = isChapterSkipMode,
                onToggleSilenceSkipping = onToggleSilenceSkipping,
                onToggleChapterSkipMode = onToggleChapterSkipMode,
                onDurationsSelected = { f, b ->
                    onSetSkipDurations(f, b)
                    showSkipSettingsDialog = false
                },
                onDismiss = { showSkipSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun ChapterItem(title: String, startTime: Long, isActive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isActive) Icons.Rounded.PlayCircle else Icons.Rounded.Circle,
                contentDescription = null,
                modifier = Modifier.size(if (isActive) 24.dp else 12.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatTime(startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipSettingsDialog(
    currentForward: Long,
    currentBackward: Long,
    isSilenceSkippingEnabled: Boolean,
    isChapterSkipMode: Boolean,
    onToggleSilenceSkipping: () -> Unit,
    onToggleChapterSkipMode: () -> Unit,
    onDurationsSelected: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val forwardOptions = listOf(10000L, 15000L, 30000L, 60000L)
    val backwardOptions = listOf(10000L, 15000L, 30000L, 60000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Skip Mode", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (isChapterSkipMode) "Chapter-based" else "Time-based",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isChapterSkipMode, onCheckedChange = { onToggleChapterSkipMode() })
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isChapterSkipMode) {
                    Text("Skip Forward", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        forwardOptions.forEach { duration ->
                            FilterChip(
                                selected = duration == currentForward,
                                onClick = { onDurationsSelected(duration, currentBackward) },
                                label = { Text("${duration / 1000}s") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Skip Backward", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        backwardOptions.forEach { duration ->
                            FilterChip(
                                selected = duration == currentBackward,
                                onClick = { onDurationsSelected(currentForward, duration) },
                                label = { Text("${duration / 1000}s") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Silence Skipping", style = MaterialTheme.typography.bodyLarge)
                        Text("Automatically skip silent gaps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isSilenceSkippingEnabled, onCheckedChange = { onToggleSilenceSkipping() })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = speed == currentSpeed, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("${speed}x")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SleepTimerDialog(
    currentTimeLeft: Long,
    onTimerSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTimerSelected(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = false, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("$minutes minutes")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTimerSelected(0) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentTimeLeft == 0L, onClick = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Off")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun PlayerPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            ChapterTheme {
                PlayerContent(
                    uiState = PlayerUiState(
                        book = Book(title = "Sample Book", author = "Author Name", filePath = "", id = 1, coverArtPath = null, duration = 120000),
                        isPlaying = true,
                        currentPosition = 30000,
                        duration = 120000,
                        chapters = listOf(ChapterInfo("Introduction", 0), ChapterInfo("Chapter 1", 30000))
                    ),
                    backgroundColor = Color.White,
                    sharedKeyPrefix = "mini",
                    onTogglePlayPause = {},
                    onSeek = {},
                    onSkipForward = {},
                    onSkipBackward = {},
                    onSetSpeed = {},
                    onSetSleepTimer = {},
                    onQuickSleepTimer = {},
                    onAddBookmark = {},
                    onToggleSilenceSkipping = {},
                    onSetSkipDurations = { _, _ -> },
                    isChapterSkipMode = false,
                    onToggleChapterSkipMode = {},
                    onToggleFavorite = {},
                    onToggleShuffle = {},
                    onToggleRepeatMode = {},
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedVisibility
                )
            }
        }
    }
}

