package com.freestream.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.freestream.data.model.PlaybackEpisode
import com.freestream.data.model.PlaybackSession
import com.freestream.data.model.ResolvedStream
import com.freestream.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
class TvPlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var repository: MediaRepository

    private var session: PlaybackSession? = null
    private var currentIndex: Int = 0
    private var episodeUrl: String = ""
    private var seriesTitle: String = ""
    private var episodeTitle: String = ""
    private var seasonNumber: String = "1"
    private var episodeNumber: String = "1"
    private var posterUrl: String = ""
    private var progressTrackerJob: Job? = null
    private var userExplicitExit = false
    private var advancingToNext = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        repository = MediaRepository(applicationContext)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    userExplicitExit = true
                    finish()
                }
            }
        )

        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3500
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowFastForwardButton(true)
            setShowRewindButton(true)
        }
        setContentView(playerView)

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: return finish()
        val streamTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Episode"
        val referer = intent.getStringExtra(EXTRA_REFERER) ?: ""
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: "Mozilla/5.0"
        val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        val isHls = streamUrl.contains(".m3u8")

        episodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL) ?: ""
        seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: ""
        episodeTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE) ?: streamTitle
        seasonNumber = intent.getStringExtra(EXTRA_SEASON_NUMBER) ?: "1"
        episodeNumber = intent.getStringExtra(EXTRA_EPISODE_NUMBER) ?: "1"
        posterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""

        intent.getStringExtra(EXTRA_PLAYBACK_SESSION)?.let { json ->
            session = Json.decodeFromString(PlaybackSession.serializer(), json)
            currentIndex = session?.startIndex ?: 0
        }

        initializePlayer(streamUrl, streamTitle, referer, userAgent, isHls, startPositionMs)
    }

    private fun initializePlayer(
        url: String,
        title: String,
        referer: String,
        userAgent: String,
        isHls: Boolean,
        startPositionMs: Long
    ) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to referer,
                    "Accept" to "*/*"
                )
            )
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        val existingPlayer = player
        if (existingPlayer != null) {
            existingPlayer.setMediaItem(mediaItem)
            if (startPositionMs > 0) {
                existingPlayer.seekTo(startPositionMs)
            } else {
                existingPlayer.seekTo(0)
            }
            existingPlayer.prepare()
            existingPlayer.playWhenReady = true
            return
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(mediaItem)
                if (startPositionMs > 0) {
                    seekTo(startPositionMs)
                }
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            startProgressTracker()
                        } else if (!advancingToNext) {
                            saveCurrentProgress()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            onEpisodeEnded()
                        }
                    }
                })
            }

        playerView.player = player
    }

    private fun onEpisodeEnded() {
        saveCurrentProgress(forceFinished = true)
        if (userExplicitExit) {
            finish()
            return
        }

        val activeSession = session
        if (activeSession != null && currentIndex + 1 < activeSession.episodes.size) {
            Toast.makeText(this, "Auto-advance not available", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun playEpisodeAt(index: Int) {
        finish()
    }

    private suspend fun resolveEpisode(
        activeSession: PlaybackSession,
        episode: PlaybackEpisode
    ): ResolvedStream {
        throw UnsupportedOperationException("Episode queue playback is not supported")
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                saveCurrentProgress()
            }
        }
    }

    private fun saveCurrentProgress(forceFinished: Boolean = false) {
        val p = player ?: return
        if (episodeUrl.isEmpty()) return

        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0) return

        val actualPos = if (forceFinished) dur else pos
        lifecycleScope.launch {
            repository.saveWatchProgress(
                episodeUrl = episodeUrl,
                seriesTitle = seriesTitle,
                episodeTitle = episodeTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                posterUrl = posterUrl,
                positionMs = actualPos,
                durationMs = dur
            )
        }
    }

    private fun saveCurrentProgressBlocking(forceFinished: Boolean = false) {
        val p = player ?: return
        if (episodeUrl.isEmpty()) return

        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0) return

        val actualPos = if (forceFinished) dur else pos
        runBlocking {
            repository.saveWatchProgress(
                episodeUrl = episodeUrl,
                seriesTitle = seriesTitle,
                episodeTitle = episodeTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                posterUrl = posterUrl,
                positionMs = actualPos,
                durationMs = dur
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val p = player ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val newPos = (p.currentPosition - 10000).coerceAtLeast(0)
                p.seekTo(newPos)
                playerView.showController()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val newPos = (p.currentPosition + 10000).coerceAtMost(p.duration.coerceAtLeast(0))
                p.seekTo(newPos)
                playerView.showController()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (playerView.isControllerFullyVisible) {
                    if (p.isPlaying) p.pause() else p.play()
                } else {
                    playerView.showController()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                } else {
                    playerView.showController()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (p.isPlaying) p.pause() else p.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                p.seekTo((p.currentPosition + 30000).coerceAtMost(p.duration.coerceAtLeast(0)))
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                p.seekTo((p.currentPosition - 30000).coerceAtLeast(0))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        progressTrackerJob?.cancel()
        if (!advancingToNext) {
            saveCurrentProgress()
        }
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        progressTrackerJob?.cancel()
        if (!advancingToNext) {
            saveCurrentProgressBlocking()
        }
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_START_POSITION_MS = "extra_start_position_ms"
        const val EXTRA_EPISODE_URL = "extra_episode_url"
        const val EXTRA_SERIES_TITLE = "extra_series_title"
        const val EXTRA_EPISODE_TITLE = "extra_episode_title"
        const val EXTRA_SEASON_NUMBER = "extra_season_number"
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"
        const val EXTRA_POSTER_URL = "extra_poster_url"
        const val EXTRA_PLAYBACK_SESSION = "extra_playback_session"
        const val EXTRA_SERIES_COMPLETE = "extra_series_complete"

        fun createIntent(
            context: Context,
            resolved: ResolvedStream,
            title: String,
            startPositionMs: Long = 0L,
            episodeUrl: String = "",
            seriesTitle: String = "",
            episodeTitle: String = "",
            seasonNumber: String = "1",
            episodeNumber: String = "1",
            posterUrl: String = "",
            session: PlaybackSession? = null
        ): Intent {
            return Intent(context, TvPlayerActivity::class.java).apply {
                putExtra(EXTRA_STREAM_URL, resolved.streamUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REFERER, resolved.headers["Referer"] ?: "")
                putExtra(EXTRA_USER_AGENT, resolved.headers["User-Agent"] ?: "Mozilla/5.0")
                putExtra(EXTRA_START_POSITION_MS, startPositionMs)
                putExtra(EXTRA_EPISODE_URL, episodeUrl)
                putExtra(EXTRA_SERIES_TITLE, seriesTitle)
                putExtra(EXTRA_EPISODE_TITLE, episodeTitle)
                putExtra(EXTRA_SEASON_NUMBER, seasonNumber)
                putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)
                putExtra(EXTRA_POSTER_URL, posterUrl)
                if (session != null) {
                    putExtra(
                        EXTRA_PLAYBACK_SESSION,
                        Json.encodeToString(PlaybackSession.serializer(), session)
                    )
                }
            }
        }

        fun launch(
            context: Context,
            resolved: ResolvedStream,
            title: String,
            startPositionMs: Long = 0L,
            episodeUrl: String = "",
            seriesTitle: String = "",
            episodeTitle: String = "",
            seasonNumber: String = "1",
            episodeNumber: String = "1",
            posterUrl: String = "",
            session: PlaybackSession? = null
        ) {
            context.startActivity(
                createIntent(
                    context = context,
                    resolved = resolved,
                    title = title,
                    startPositionMs = startPositionMs,
                    episodeUrl = episodeUrl,
                    seriesTitle = seriesTitle,
                    episodeTitle = episodeTitle,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    posterUrl = posterUrl,
                    session = session
                )
            )
        }
    }
}
