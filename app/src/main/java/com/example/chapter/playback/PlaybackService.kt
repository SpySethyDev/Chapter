package com.example.chapter.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.chapter.MainActivity
import com.example.chapter.data.local.AppDatabase
import com.example.chapter.data.repository.BookRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var repository: BookRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var savePositionJob: Job? = null
    
    // Track multi-file chapter boundaries
    private var chapters: List<com.example.chapter.data.local.entities.Chapter> = emptyList()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("CHAPTER_DEBUG", "PlaybackService onCreate")
        val database = AppDatabase.getDatabase(this)
        repository = BookRepository(database.bookDao(), database.chapterDao(), database.bookmarkDao())

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSkipSilenceEnabled(true)
            .build()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("TOGGLE_SILENCE_SKIPPING", android.os.Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "TOGGLE_SILENCE_SKIPPING") {
                        val enabled = args.getBoolean("enabled", false)
                        exoPlayer.skipSilenceEnabled = enabled
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        )
                    }
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                    )
                }

                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    // Intercept media items to handle multi-file playlists
                    val item = mediaItems.firstOrNull()
                    val bookId = item?.mediaId?.toLongOrNull()
                    
                    if (bookId != null) {
                        val deferred = serviceScope.async {
                            val dbChapters = repository.getChaptersForBook(bookId).firstOrNull() ?: emptyList()
                            chapters = dbChapters
                            
                            if (dbChapters.any { it.filePath != null }) {
                                // Multi-file book: create a playlist
                                val playlist = dbChapters.map { chapter ->
                                    MediaItem.Builder()
                                        .setMediaId(bookId.toString())
                                        .setUri(Uri.parse(chapter.filePath))
                                        .setMediaMetadata(
                                            androidx.media3.common.MediaMetadata.Builder()
                                                .setTitle(chapter.title)
                                                .build()
                                        )
                                        .build()
                                }
                                
                                // Map global startPositionMs to specific file and local offset
                                var currentGlobalOffset = 0L
                                var targetIndex = 0
                                var targetPosition = startPositionMs
                                
                                for (i in playlist.indices) {
                                    val duration = dbChapters[i].duration
                                    if (startPositionMs < currentGlobalOffset + duration) {
                                        targetIndex = i
                                        targetPosition = startPositionMs - currentGlobalOffset
                                        break
                                    }
                                    currentGlobalOffset += duration
                                }
                                
                                MediaSession.MediaItemsWithStartPosition(playlist, targetIndex, targetPosition)
                            } else {
                                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                            }
                        }
                        return deferred.asListenableFuture()
                    }
                    
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                    )
                }
            })
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentPosition()
                    stopPositionUpdateTimer()
                } else {
                    startPositionUpdateTimer()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                saveCurrentPosition()
            }
        })
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> Deferred<T>.asListenableFuture(): ListenableFuture<T> {
        val future = com.google.common.util.concurrent.SettableFuture.create<T>()
        this.invokeOnCompletion { t ->
            if (t != null) {
                future.setException(t)
            } else {
                try {
                    future.set(this.getCompleted())
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
        }
        return future
    }

    private fun startPositionUpdateTimer() {
        savePositionJob?.cancel()
        savePositionJob = serviceScope.launch {
            while (isActive) {
                delay(5000)
                saveCurrentPosition()
            }
        }
    }

    private fun stopPositionUpdateTimer() {
        savePositionJob?.cancel()
        savePositionJob = null
    }

    private fun saveCurrentPosition() {
        val bookId = mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        
        // Calculate global position for multi-file books
        var globalPosition = 0L
        if (chapters.any { it.filePath != null }) {
            val currentIndex = exoPlayer.currentMediaItemIndex
            for (i in 0 until currentIndex) {
                if (i < chapters.size) {
                    globalPosition += chapters[i].duration
                }
            }
            globalPosition += exoPlayer.currentPosition
        } else {
            globalPosition = exoPlayer.currentPosition
        }
        
        serviceScope.launch {
            repository.updatePlaybackPosition(bookId, globalPosition)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        saveCurrentPosition()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
