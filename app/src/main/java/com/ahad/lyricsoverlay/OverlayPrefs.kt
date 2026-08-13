package com.ahad.lyricsoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

class OverlayPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_SIZE, 22f)
        set(value) { prefs.edit().putFloat(KEY_SIZE, value).apply() }

    var fontStyle: String
        get() = prefs.getString(KEY_FONT, FONT_SANS) ?: FONT_SANS
        set(value) { prefs.edit().putString(KEY_FONT, value).apply() }

    var textColor: Int
        get() = prefs.getInt(KEY_COLOR, Color.WHITE)
        set(value) { prefs.edit().putInt(KEY_COLOR, value).apply() }

    var animation: String
        get() = prefs.getString(KEY_ANIM, ANIM_FADE) ?: ANIM_FADE
        set(value) { prefs.edit().putString(KEY_ANIM, value).apply() }

    var posX: Int
        get() = prefs.getInt(KEY_X, 0)
        set(value) { prefs.edit().putInt(KEY_X, value).apply() }

    var posY: Int
        get() = prefs.getInt(KEY_Y, 220)
        set(value) { prefs.edit().putInt(KEY_Y, value).apply() }

    fun typeface(): Typeface = when (fontStyle) {
        FONT_SERIF -> Typeface.SERIF
        FONT_MONO -> Typeface.MONOSPACE
        FONT_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        FONT_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        else -> Typeface.create("sans-serif", Typeface.BOLD)
    }

    fun resetPosition() {
        posX = 0
        posY = 220
    }

    companion object {
        private const val NAME = "overlay_prefs"
        private const val KEY_SIZE = "font_size"
        private const val KEY_FONT = "font_style"
        private const val KEY_COLOR = "text_color"
        private const val KEY_ANIM = "anim"
        private const val KEY_X = "pos_x"
        private const val KEY_Y = "pos_y"

        const val FONT_SANS = "sans"
        const val FONT_LIGHT = "light"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
        const val FONT_CONDENSED = "condensed"

        const val ANIM_FADE = "fade"
        const val ANIM_SCALE = "scale"
        const val ANIM_SLIDE = "slide"

        const val ACTION_PREFS_CHANGED = "com.ahad.lyricsoverlay.PREFS_CHANGED"
    }
}
