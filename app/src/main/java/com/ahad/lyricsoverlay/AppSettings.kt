package com.ahad.lyricsoverlay

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _live = MutableLiveData(snapshot())
    val live: LiveData<Snap> = _live

    data class Snap(
        val grid: Boolean,
        val gridSpan: Int,
        val accent: Int,
        val night: Int,
        val cardStyle: String,
        val sort: String,
        val font: String
    )

    var grid: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(v) = put { putBoolean(KEY_GRID, v) }

    var gridSpan: Int
        get() = prefs.getInt(KEY_SPAN, 2).coerceIn(2, 3)
        set(v) = put { putInt(KEY_SPAN, v.coerceIn(2, 3)) }

    var accent: Int
        get() = prefs.getInt(KEY_ACCENT, 0xFFB388FF.toInt())
        set(v) = put { putInt(KEY_ACCENT, v) }

    var night: Int
        get() = prefs.getInt(KEY_NIGHT, NIGHT_SYSTEM)
        set(v) = put { putInt(KEY_NIGHT, v) }

    var cardStyle: String
        get() = prefs.getString(KEY_CARD, CARD_ROUNDED) ?: CARD_ROUNDED
        set(v) = put { putString(KEY_CARD, v) }

    var sort: String
        get() = prefs.getString(KEY_SORT, SORT_TITLE) ?: SORT_TITLE
        set(v) = put { putString(KEY_SORT, v) }

    var font: String
        get() = prefs.getString(KEY_FONT, FONT_SANS) ?: FONT_SANS
        set(v) = put { putString(KEY_FONT, v) }

    fun typeface(): Typeface = when (font) {
        FONT_SERIF -> Typeface.SERIF
        FONT_MONO -> Typeface.MONOSPACE
        FONT_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        FONT_BLACK -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (night) {
                NIGHT_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                NIGHT_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun snapshot(): Snap = Snap(grid, gridSpan, accent, night, cardStyle, sort, font)

    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _live.postValue(snapshot())
    }

    companion object {
        private const val NAME = "app_ui_prefs"
        private const val KEY_GRID = "grid"
        private const val KEY_SPAN = "span"
        private const val KEY_ACCENT = "accent"
        private const val KEY_NIGHT = "night"
        private const val KEY_CARD = "card"
        private const val KEY_SORT = "sort"
        private const val KEY_FONT = "app_font"

        const val CARD_FLAT = "flat"
        const val CARD_ROUNDED = "rounded"
        const val CARD_COMPACT = "compact"

        const val SORT_TITLE = "title"
        const val SORT_ARTIST = "artist"
        const val SORT_DATE = "date"
        const val SORT_DURATION = "duration"

        const val FONT_SANS = "sans"
        const val FONT_LIGHT = "light"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
        const val FONT_BLACK = "black"

        const val NIGHT_SYSTEM = 0
        const val NIGHT_LIGHT = 1
        const val NIGHT_DARK = 2

        val ACCENTS = intArrayOf(
            0xFFB388FF.toInt(),
            0xFFFF8A80.toInt(),
            0xFF80D8FF.toInt(),
            0xFF69F0AE.toInt(),
            0xFFFFD180.toInt(),
            0xFFFF80AB.toInt(),
            0xFF82B1FF.toInt(),
            0xFFEA80FC.toInt()
        )

        @Volatile private var inst: AppSettings? = null

        fun init(context: Context): AppSettings {
            return inst ?: synchronized(this) {
                inst ?: AppSettings(context).also { inst = it }
            }
        }

        fun get(): AppSettings = inst ?: error("AppSettings.init not called")
    }
}
