package com.ahad.lyricsoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class PlayerService : Service(), AudioManager.OnAudioFocusChangeListener {

    inner class LocalBinder : Binder() {
        fun service(): PlayerService = this@PlayerService
    }

    private val binder = LocalBinder()
    private var player: MediaPlayer? = null
    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && PlayerState.playing) {
                try {
                    PlayerState.positionMs = p.currentPosition.toLong()
                    PlayerState.durationMs = p.duration.toLong().coerceAtLeast(0)
                    PlayerState.notifyChanged()
                    updatePlaybackState()
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 400)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        session = MediaSessionCompat(this, "LyricsPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onStop() = stopSelf()
            })
            isActive = true
        }
        startInForeground()
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_INDEX -> {
                val list = intent.getParcelableArrayListExtra<android.os.Bundle>(EXTRA_QUEUE)
                // queue already set via companion before start
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                playIndex(index)
            }
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_POS, 0L))
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        abandonFocus()
        releasePlayer()
        session.release()
        PlayerState.playing = false
        PlayerState.notifyChanged()
        super.onDestroy()
    }

    fun playQueue(songs: List<Song>, index: Int) {
        pendingQueue = songs
        playIndex(index)
    }

    fun playIndex(index: Int) {
        val songs = pendingQueue.ifEmpty { PlayerState.queue }
        if (songs.isEmpty() || index !in songs.indices) return
        pendingQueue = songs
        PlayerState.queue = songs
        PlayerState.index = index
        val song = songs[index]
        if (!requestFocus()) return
        releasePlayer()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, song.contentUri)
                setOnPreparedListener {
                    start()
                    PlayerState.playing = true
                    PlayerState.durationMs = duration.toLong().coerceAtLeast(0)
                    PlayerState.positionMs = 0
                    PlayerState.notifyChanged()
                    updateSessionMetadata(song)
                    updatePlaybackState()
                    refreshNotification()
                    maybeStartOverlay()
                }
                setOnCompletionListener { next() }
                setOnErrorListener { _, _, _ ->
                    PlayerState.playing = false
                    PlayerState.notifyChanged()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            PlayerState.playing = false
            PlayerState.notifyChanged()
        }
    }

    fun toggle() {
        if (PlayerState.playing) pause() else resume()
    }

    fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) {
        }
        PlayerState.playing = false
        PlayerState.notifyChanged()
        updatePlaybackState()
        refreshNotification()
    }

    fun resume() {
        if (player == null) {
            val i = PlayerState.index
            if (i >= 0) playIndex(i)
            return
        }
        if (!requestFocus()) return
        try {
            player?.start()
            PlayerState.playing = true
        } catch (_: Exception) {
        }
        PlayerState.notifyChanged()
        updatePlaybackState()
        refreshNotification()
    }

    fun next() {
        val q = PlayerState.queue
        if (q.isEmpty()) return
        playIndex((PlayerState.index + 1) % q.size)
    }

    fun previous() {
        val q = PlayerState.queue
        if (q.isEmpty()) return
        val pos = try {
            player?.currentPosition ?: 0
        } catch (_: Exception) {
            0
        }
        if (pos > 4000) {
            seekTo(0)
            return
        }
        val next = if (PlayerState.index <= 0) q.lastIndex else PlayerState.index - 1
        playIndex(next)
    }

    fun seekTo(ms: Long) {
        try {
            player?.seekTo(ms.toInt())
            PlayerState.positionMs = ms
            PlayerState.notifyChanged()
            updatePlaybackState()
        } catch (_: Exception) {
        }
    }

    private fun maybeStartOverlay() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            val i = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SYNC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        }
    }

    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_GAIN -> resume()
        }
    }

    private fun releasePlayer() {
        try {
            player?.reset()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private fun updateSessionMetadata(song: Song) {
        val art = loadArt(song)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .build()
        )
    }

    private fun updatePlaybackState() {
        val state = if (PlayerState.playing) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, PlayerState.positionMs, 1f)
                .build()
        )
    }

    private fun startInForeground() {
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.player_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val song = PlayerState.song
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val prev = servicePi(1, ACTION_PREV)
        val toggle = servicePi(2, ACTION_TOGGLE)
        val next = servicePi(3, ACTION_NEXT)
        val playIcon = if (PlayerState.playing) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist ?: "")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(song?.let { loadArt(it) })
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prev)
            .addAction(playIcon, "Play", toggle)
            .addAction(android.R.drawable.ic_media_next, "Next", next)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOnlyAlertOnce(true)
            .setOngoing(PlayerState.playing)
            .build()
    }

    private fun servicePi(req: Int, action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            req,
            Intent(this, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun loadArt(song: Song): Bitmap? {
        return try {
            contentResolver.openInputStream(song.albumArtUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ACTION_PLAY_INDEX = "com.ahad.lyricsoverlay.PLAY_INDEX"
        const val ACTION_TOGGLE = "com.ahad.lyricsoverlay.TOGGLE"
        const val ACTION_NEXT = "com.ahad.lyricsoverlay.NEXT"
        const val ACTION_PREV = "com.ahad.lyricsoverlay.PREV"
        const val ACTION_PAUSE = "com.ahad.lyricsoverlay.PAUSE"
        const val ACTION_RESUME = "com.ahad.lyricsoverlay.RESUME"
        const val ACTION_SEEK = "com.ahad.lyricsoverlay.SEEK"
        const val ACTION_STOP = "com.ahad.lyricsoverlay.STOP"
        const val EXTRA_INDEX = "index"
        const val EXTRA_POS = "pos"
        const val EXTRA_QUEUE = "queue"
        private const val CHANNEL_ID = "player"
        private const val NOTIF_ID = 88

        @Volatile
        var pendingQueue: List<Song> = emptyList()
    }
}
