package com.ahad.lyricsoverlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import kotlin.concurrent.thread

class OverlayWindow(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = OverlayPrefs(context)
    private val handler = Handler(Looper.getMainLooper())
    private val evaluator = ArgbEvaluator()

    private var tv: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lines: List<LyricLine> = emptyList()
    private var lastKey: String? = null
    private var lastText: String? = null
    private var shownColor = 0xFFFFFFFF.toInt()
    private var fetchGen = 0
    private var attached = false

    fun show() {
        if (attached) return
        if (!Settings.canDrawOverlays(context)) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        tv = TextView(context).apply {
            setBackgroundColor(0x00000000)
            setPadding(12, 6, 12, 6)
            setShadowLayer(10f, 0f, 2f, 0xE6000000.toInt())
            maxWidth = context.resources.displayMetrics.widthPixels - 40
        }
        applyStyle()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = prefs.posX
            y = prefs.posY
        }
        drag()
        try {
            wm.addView(tv, params)
            attached = true
        } catch (_: Exception) {
            attached = false
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        if (attached) {
            try {
                wm.removeView(tv)
            } catch (_: Exception) {
            }
        }
        attached = false
        tv = null
        lastKey = null
        lastText = null
        lines = emptyList()
    }

    fun applyStyle() {
        val v = tv ?: return
        v.textSize = prefs.fontSizeSp
        v.typeface = prefs.typeface()
        v.setTextColor(prefs.textColor)
        shownColor = prefs.textColor
        params?.let {
            it.x = prefs.posX
            it.y = prefs.posY
            if (attached) {
                try {
                    wm.updateViewLayout(v, it)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun tick() {
        if (!attached) show()
        val song = PlayerState.song
        if (song == null) {
            setText("")
            return
        }
        val key = "${song.id}:${song.title}"
        if (key != lastKey) {
            lastKey = key
            lines = emptyList()
            setText(song.title)
            val gen = ++fetchGen
            thread(name = "lyrics-fetch") {
                val result = try {
                    LyricsRepository.load(context, song)
                } catch (_: Exception) {
                    emptyList()
                }
                handler.post {
                    if (gen != fetchGen) return@post
                    lines = result
                    if (result.isEmpty()) setText("")
                }
            }
            return
        }
        if (lines.isEmpty()) return
        val line = LrcParser.lineAt(lines, PlayerState.positionMs) ?: return
        setText(line.text)
    }

    private fun setText(text: String) {
        val v = tv ?: return
        if (text == lastText) return
        lastText = text
        if (text.isEmpty()) {
            v.animate().cancel()
            v.text = ""
            v.alpha = 0f
            return
        }
        val next = evaluator.evaluate(0.4f, prefs.textColor, palette.random()) as Int
        val anim = prefs.animation
        v.animate().cancel()
        val out = v.animate().setDuration(140).setInterpolator(DecelerateInterpolator())
        when (anim) {
            OverlayPrefs.ANIM_SLIDE -> out.alpha(0f).translationY(-16f)
            OverlayPrefs.ANIM_SCALE -> out.alpha(0f).scaleX(0.85f).scaleY(0.85f)
            else -> out.alpha(0f)
        }
        out.withEndAction {
            v.text = text
            ValueAnimator.ofObject(evaluator, shownColor, next).apply {
                duration = 240
                addUpdateListener {
                    shownColor = it.animatedValue as Int
                    v.setTextColor(shownColor)
                }
                start()
            }
            v.alpha = 0f
            when (anim) {
                OverlayPrefs.ANIM_SLIDE -> {
                    v.translationY = 18f
                    v.animate().alpha(1f).translationY(0f).setDuration(220).start()
                }
                OverlayPrefs.ANIM_SCALE -> {
                    v.scaleX = 0.88f
                    v.scaleY = 0.88f
                    v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
                }
                else -> v.animate().alpha(1f).setDuration(220).start()
            }
        }.start()
    }

    private fun drag() {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        tv?.setOnTouchListener { _, e ->
            val lp = params ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x
                    sy = lp.y
                    tx = e.rawX
                    ty = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = sx + (e.rawX - tx).toInt()
                    lp.y = sy + (e.rawY - ty).toInt()
                    try {
                        wm.updateViewLayout(tv, lp)
                    } catch (_: Exception) {
                    }
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

    companion object {
        private val palette = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFF8A80.toInt(),
            0xFFEA80FC.toInt(),
            0xFF8C9EFF.toInt(),
            0xFF84FFFF.toInt(),
            0xFFFFFF8D.toInt()
        )
    }
}
