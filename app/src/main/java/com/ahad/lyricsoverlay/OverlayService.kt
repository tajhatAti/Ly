package com.ahad.lyricsoverlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var textView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var prefs: OverlayPrefs
    private val handler = Handler(Looper.getMainLooper())
    private val evaluator = ArgbEvaluator()

    private var lines: List<LyricLine> = emptyList()
    private var lastKey: String? = null
    private var lastText: String? = null
    private var shownColor: Int = 0xFFFFFFFF.toInt()
    private var fetchGen = 0

    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(),
        0xFFFF8A80.toInt(),
        0xFFEA80FC.toInt(),
        0xFF8C9EFF.toInt(),
        0xFF84FFFF.toInt(),
        0xFFB9F6CA.toInt(),
        0xFFFFFF8D.toInt()
    )

    private val onPlayer = { sync() }

    private val prefsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyStyle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = OverlayPrefs(this)
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        attach()
        PlayerState.addListener(onPlayer)
        val filter = IntentFilter(OverlayPrefs.ACTION_PREFS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(prefsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(prefsReceiver, filter)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        sync()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        PlayerState.removeListener(onPlayer)
        try {
            unregisterReceiver(prefsReceiver)
        } catch (_: Exception) {
        }
        detach()
        super.onDestroy()
    }

    private val tick = object : Runnable {
        override fun run() {
            sync()
            handler.postDelayed(this, 280)
        }
    }

    private fun attach() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        textView = TextView(this).apply {
            setBackgroundColor(0x00000000)
            setPadding(8, 4, 8, 4)
            setShadowLayer(8f, 0f, 2f, 0xCC000000.toInt())
            maxWidth = resources.displayMetrics.widthPixels - 48
        }
        applyStyle()
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.posX
            y = prefs.posY
        }
        enableDrag()
        windowManager?.addView(textView, params)
    }

    private fun enableDrag() {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        textView?.setOnTouchListener { _, event ->
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
                    windowManager?.updateViewLayout(textView, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.posX = lp.x
                    prefs.posY = lp.y
                    true
                }
                else -> false
            }
        }
    }

    private fun applyStyle() {
        val tv = textView ?: return
        tv.textSize = prefs.fontSizeSp
        tv.typeface = prefs.typeface()
        if (lastText == null) {
            tv.setTextColor(prefs.textColor)
            shownColor = prefs.textColor
        }
    }

    private fun sync() {
        val song = PlayerState.song
        if (song == null) {
            setVisibleText("")
            lastKey = null
            lines = emptyList()
            return
        }
        val key = "${song.id}|${song.title}|${song.artist}"
        if (key != lastKey) {
            lastKey = key
            lines = emptyList()
            setVisibleText("")
            val gen = ++fetchGen
            thread(name = "lyrics") {
                val result = try {
                    LyricsRepository.load(this, song)
                } catch (_: Exception) {
                    emptyList()
                }
                handler.post {
                    if (gen != fetchGen || lastKey != key) return@post
                    lines = result
                }
            }
            return
        }
        if (lines.isEmpty()) {
            setVisibleText("")
            return
        }
        val line = LrcParser.lineAt(lines, PlayerState.positionMs) ?: return
        setVisibleText(line.text)
    }

    private fun setVisibleText(text: String) {
        val tv = textView ?: return
        if (text == lastText) return
        lastText = text
        if (text.isEmpty()) {
            tv.animate().cancel()
            tv.text = ""
            tv.alpha = 0f
            return
        }
        val nextColor = nextAnimatedColor()
        animateOutThen {
            tv.text = text
            animateColor(shownColor, nextColor)
            animateIn()
        }
    }

    private fun nextAnimatedColor(): Int {
        val base = prefs.textColor
        val mix = palette.random()
        return evaluator.evaluate(0.35f, base, mix) as Int
    }

    private fun animateColor(from: Int, to: Int) {
        val tv = textView ?: return
        ValueAnimator.ofObject(evaluator, from, to).apply {
            duration = 280
            addUpdateListener {
                val c = it.animatedValue as Int
                shownColor = c
                tv.setTextColor(c)
            }
            start()
        }
    }

    private fun animateOutThen(done: () -> Unit) {
        val tv = textView ?: return
        val anim = prefs.animation
        val a = tv.animate().setDuration(160).setInterpolator(DecelerateInterpolator())
        when (anim) {
            OverlayPrefs.ANIM_SLIDE -> a.alpha(0f).translationY(-18f)
            OverlayPrefs.ANIM_SCALE -> a.alpha(0f).scaleX(0.82f).scaleY(0.82f)
            else -> a.alpha(0f)
        }
        a.withEndAction(done).start()
    }

    private fun animateIn() {
        val tv = textView ?: return
        val anim = prefs.animation
        tv.alpha = 0f
        when (anim) {
            OverlayPrefs.ANIM_SLIDE -> {
                tv.translationY = 22f
                tv.scaleX = 1f
                tv.scaleY = 1f
                tv.animate().alpha(1f).translationY(0f).setDuration(240).start()
            }
            OverlayPrefs.ANIM_SCALE -> {
                tv.translationY = 0f
                tv.scaleX = 0.86f
                tv.scaleY = 0.86f
                tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()
            }
            else -> {
                tv.translationY = 0f
                tv.scaleX = 1f
                tv.scaleY = 1f
                tv.animate().alpha(1f).setDuration(240).start()
            }
        }
    }

    private fun detach() {
        textView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        textView = null
        windowManager = null
    }

    private fun startInForeground() {
        val id = "overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(id, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, id)
            .setContentTitle(getString(R.string.overlay_running))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
    }

    companion object {
        const val ACTION_SYNC = "com.ahad.lyricsoverlay.OVERLAY_SYNC"
        const val ACTION_STOP = "com.ahad.lyricsoverlay.OVERLAY_STOP"
        private const val NOTIF_ID = 89
    }
}
