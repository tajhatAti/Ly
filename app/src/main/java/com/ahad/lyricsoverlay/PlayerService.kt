package com.ahad.lyricsoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class PlayerService : Service(), AudioManager.OnAudioFocusChangeListener {

    private var player: MediaPlayer? = null
    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var overlay: OverlayWindow? = null
    private var pausedByUser = false
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                try {
                    if (p.isPlaying) {
                        PlayerState.playing = true
                        PlayerState.positionMs = p.currentPosition.toLong()
                        val d = p.duration
                        if (d > 0) PlayerState.durationMs = d.toLong()
                    }
                    PlayerState.notifyChanged()
                    updatePlaybackState()
                    overlay?.tick()
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, 300)
        }
    }

    private val prefsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlay?.applyStyle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        session = MediaSessionCompat(this, "LyricsPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    pausedByUser = false
                    resume()
                }
                override fun onPause() {
                    pausedByUser = true
                    pause()
                }
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seekTo(pos)
            })
            isActive = true
        }
        startInForeground()
        overlay = OverlayWindow(this)
        overlay?.show()
        val filter = IntentFilter(OverlayPrefs.ACTION_PREFS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(prefsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(prefsReceiver, filter)
        }
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_INDEX -> playIndex(intent.getIntExtra(EXTRA_INDEX, 0))
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_PAUSE -> {
                pausedByUser = true
                pause()
            }
            ACTION_RESUME -> {
                pausedByUser = false
                resume()
            }
            ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_POS, 0L))
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(prefsReceiver)
        } catch (_: Exception) {
        }
        overlay?.hide()
        overlay = null
        abandonFocus()
        releasePlayer()
        session.release()
        PlayerState.playing = false
        PlayerState.notifyChanged()
        super.onDestroy()
    }

    private fun playIndex(index: Int) {
        val songs = pendingQueue.ifEmpty { PlayerState.queue }
        if (songs.isEmpty() || index !in songs.indices) return
        pendingQueue = songs
        PlayerState.queue = songs
        PlayerState.index = index
        val song = songs[index]
        pausedByUser = false
        requestFocus()
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            var ok = false
            try {
                mp.setDataSource(applicationContext, song.contentUri)
                ok = true
            } catch (_: Exception) {
            }
            if (!ok && song.path.isNotBlank()) {
                mp.setDataSource(song.path)
                ok = true
            }
            if (!ok) {
                PlayerState.playing = false
                PlayerState.notifyChanged()
                return
            }
            mp.setOnPreparedListener {
                try {
                    it.start()
                    PlayerState.playing = true
                    PlayerState.durationMs = it.duration.toLong().coerceAtLeast(song.durationMs)
                    PlayerState.positionMs = 0
                    PlayerState.notifyChanged()
                    updateSessionMetadata(song)
                    updatePlaybackState()
                    refreshNotification()
                    overlay?.show()
                    overlay?.tick()
                } catch (_: Exception) {
                }
            }
            mp.setOnCompletionListener { next() }
            mp.setOnErrorListener { _, _, _ ->
                PlayerState.playing = false
                PlayerState.notifyChanged()
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            PlayerState.playing = false
            PlayerState.notifyChanged()
        }
    }

    private fun toggle() {
        if (PlayerState.playing) {
            pausedByUser = true
            pause()
        } else {
            pausedByUser = false
            resume()
        }
    }

    private fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) {
        }
        PlayerState.playing = false
        PlayerState.notifyChanged()
        updatePlaybackState()
        refreshNotification()
    }

    private fun resume() {
        if (player == null) {
            val i = PlayerState.index
            if (i >= 0) playIndex(i)
            return
        }
        requestFocus()
        try {
            player?.start()
            PlayerState.playing = true
        } catch (_: Exception) {
        }
        PlayerState.notifyChanged()
        updatePlaybackState()
        refreshNotification()
    }

    private fun next() {
        val q = PlayerState.queue
        if (q.isEmpty()) return
        playIndex((PlayerState.index + 1) % q.size)
    }

    private fun previous() {
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
        val n = if (PlayerState.index <= 0) q.lastIndex else PlayerState.index - 1
        playIndex(n)
    }

    private fun seekTo(ms: Long) {
        try {
            player?.seekTo(ms.toInt())
            PlayerState.positionMs = ms
            PlayerState.notifyChanged()
            overlay?.tick()
            updatePlaybackState()
        } catch (_: Exception) {
        }
    }

    private fun requestFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(this)
            }
        } catch (_: Exception) {
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (!pausedByUser) pause()
            AudioManager.AUDIOFOCUS_GAIN -> if (!pausedByUser) resume()
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
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, loadArt(song))
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
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.player_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val song = PlayerState.song
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
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
            .addAction(android.R.drawable.ic_media_previous, "Prev", servicePi(1, ACTION_PREV))
            .addAction(playIcon, "Play", servicePi(2, ACTION_TOGGLE))
            .addAction(android.R.drawable.ic_media_next, "Next", servicePi(3, ACTION_NEXT))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun servicePi(req: Int, action: String): PendingIntent {
        return PendingIntent.getService(
            this, req,
            Intent(this, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun loadArt(song: Song): Bitmap? {
        return try {
            contentResolver.openInputStream(song.albumArtUri)?.use { BitmapFactory.decodeStream(it) }
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
        private const val CHANNEL_ID = "player"
        private const val NOTIF_ID = 88

        @Volatile
        var pendingQueue: List<Song> = emptyList()
    }
}
