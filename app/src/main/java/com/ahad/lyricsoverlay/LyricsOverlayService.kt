package com.ahad.lyricsoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.core.app.NotificationCompat

class LyricsOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var lineView: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lineIndex = 0

    private val lyrics = listOf(
        "Floating lyrics over every app",
        "WindowManager TYPE_APPLICATION_OVERLAY",
        "Fade, scale, and color on each line",
        "Foreground service keeps it alive",
        "Drag me anywhere on the screen"
    )

    private val lineColors = intArrayOf(
        0xFFFF6B9D.toInt(),
        0xFF7C5CFF.toInt(),
        0xFF00E5C7.toInt(),
        0xFFFFC857.toInt(),
        0xFF4FC3F7.toInt()
    )

    private val tick = object : Runnable {
        override fun run() {
            showNextLine()
            handler.postDelayed(this, LINE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startInForeground()
        attachOverlay()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        detachOverlay()
        isRunning = false
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = "lyrics_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LyricsOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun attachOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_lyrics, null)
        lineView = overlayView?.findViewById(R.id.tvLyricLine)
        overlayView?.findViewById<View>(R.id.btnClose)?.setOnClickListener { stopSelf() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 180
        }

        enableDrag()
        windowManager?.addView(overlayView, params)
        showNextLine()
    }

    private fun enableDrag() {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (event.rawX - touchX).toInt()
                    lp.y = startY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(overlayView, lp)
                    true
                }
                else -> false
            }
        }
    }

    private fun showNextLine() {
        val tv = lineView ?: return
        val text = lyrics[lineIndex % lyrics.size]
        val color = lineColors[lineIndex % lineColors.size]
        lineIndex++

        tv.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                tv.text = text
                tv.setTextColor(color)
                tv.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun detachOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        lineView = null
        windowManager = null
    }

    companion object {
        const val ACTION_START = "com.ahad.lyricsoverlay.START"
        const val ACTION_STOP = "com.ahad.lyricsoverlay.STOP"
        private const val NOTIFICATION_ID = 42
        private const val LINE_INTERVAL_MS = 2800L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
