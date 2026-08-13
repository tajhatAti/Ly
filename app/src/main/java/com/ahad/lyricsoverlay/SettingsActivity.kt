package com.ahad.lyricsoverlay

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ahad.lyricsoverlay.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var overlayPrefs: OverlayPrefs
    private lateinit var ui: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        ui = AppSettings.init(this)
        ui.applyNightMode()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        overlayPrefs = OverlayPrefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        bindHome()
        bindOverlay()
    }

    private fun bindHome() {
        when (ui.night) {
            AppSettings.NIGHT_LIGHT -> binding.rgNight.check(R.id.rbNightLight)
            AppSettings.NIGHT_DARK -> binding.rgNight.check(R.id.rbNightDark)
            else -> binding.rgNight.check(R.id.rbNightSystem)
        }
        binding.rgNight.setOnCheckedChangeListener { _, id ->
            ui.night = when (id) {
                R.id.rbNightLight -> AppSettings.NIGHT_LIGHT
                R.id.rbNightDark -> AppSettings.NIGHT_DARK
                else -> AppSettings.NIGHT_SYSTEM
            }
            ui.applyNightMode()
        }

        binding.rowAccents.removeAllViews()
        val pad = (8 * resources.displayMetrics.density).toInt()
        AppSettings.ACCENTS.forEach { color ->
            val v = View(this)
            val lp = LinearLayout.LayoutParams(0, (40 * resources.displayMetrics.density).toInt(), 1f)
            lp.marginEnd = pad / 2
            v.layoutParams = lp
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            v.setOnClickListener { ui.accent = color }
            binding.rowAccents.addView(v)
        }

        binding.btnCustomAccent.setOnClickListener { showColorPicker() }

        when (ui.cardStyle) {
            AppSettings.CARD_FLAT -> binding.rgCard.check(R.id.rbCardFlat)
            AppSettings.CARD_COMPACT -> binding.rgCard.check(R.id.rbCardCompact)
            else -> binding.rgCard.check(R.id.rbCardRounded)
        }
        binding.rgCard.setOnCheckedChangeListener { _, id ->
            ui.cardStyle = when (id) {
                R.id.rbCardFlat -> AppSettings.CARD_FLAT
                R.id.rbCardCompact -> AppSettings.CARD_COMPACT
                else -> AppSettings.CARD_ROUNDED
            }
        }

        when (ui.sort) {
            AppSettings.SORT_ARTIST -> binding.rgSort.check(R.id.rbSortArtist)
            AppSettings.SORT_DATE -> binding.rgSort.check(R.id.rbSortDate)
            AppSettings.SORT_DURATION -> binding.rgSort.check(R.id.rbSortDuration)
            else -> binding.rgSort.check(R.id.rbSortTitle)
        }
        binding.rgSort.setOnCheckedChangeListener { _, id ->
            ui.sort = when (id) {
                R.id.rbSortArtist -> AppSettings.SORT_ARTIST
                R.id.rbSortDate -> AppSettings.SORT_DATE
                R.id.rbSortDuration -> AppSettings.SORT_DURATION
                else -> AppSettings.SORT_TITLE
            }
        }

        if (ui.gridSpan == 3) binding.rgSpan.check(R.id.rbSpan3) else binding.rgSpan.check(R.id.rbSpan2)
        binding.rgSpan.setOnCheckedChangeListener { _, id ->
            ui.gridSpan = if (id == R.id.rbSpan3) 3 else 2
        }

        when (ui.font) {
            AppSettings.FONT_LIGHT -> binding.rgAppFont.check(R.id.rbAppLight)
            AppSettings.FONT_SERIF -> binding.rgAppFont.check(R.id.rbAppSerif)
            AppSettings.FONT_MONO -> binding.rgAppFont.check(R.id.rbAppMono)
            AppSettings.FONT_BLACK -> binding.rgAppFont.check(R.id.rbAppBlack)
            else -> binding.rgAppFont.check(R.id.rbAppSans)
        }
        binding.rgAppFont.setOnCheckedChangeListener { _, id ->
            ui.font = when (id) {
                R.id.rbAppLight -> AppSettings.FONT_LIGHT
                R.id.rbAppSerif -> AppSettings.FONT_SERIF
                R.id.rbAppMono -> AppSettings.FONT_MONO
                R.id.rbAppBlack -> AppSettings.FONT_BLACK
                else -> AppSettings.FONT_SANS
            }
        }
    }

    private fun showColorPicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }
        var r = Color.red(ui.accent)
        var g = Color.green(ui.accent)
        var b = Color.blue(ui.accent)
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48)
            setBackgroundColor(Color.rgb(r, g, b))
        }
        fun seek(initial: Int, on: (Int) -> Unit) = SeekBar(this).apply {
            max = 255
            progress = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = on(progress)
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        box.addView(preview)
        box.addView(seek(r) { r = it; preview.setBackgroundColor(Color.rgb(r, g, b)) })
        box.addView(seek(g) { g = it; preview.setBackgroundColor(Color.rgb(r, g, b)) })
        box.addView(seek(b) { b = it; preview.setBackgroundColor(Color.rgb(r, g, b)) })
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_color)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> ui.accent = Color.rgb(r, g, b) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindOverlay() {
        binding.seekSize.max = 36
        binding.seekSize.progress = (overlayPrefs.fontSizeSp - 12).toInt().coerceIn(0, 36)
        binding.tvSizeValue.text = "${overlayPrefs.fontSizeSp.toInt()} sp"
        binding.seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 12f + progress
                binding.tvSizeValue.text = "${size.toInt()} sp"
                if (fromUser) {
                    overlayPrefs.fontSizeSp = size
                    notifyOverlay()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        when (overlayPrefs.fontStyle) {
            OverlayPrefs.FONT_LIGHT -> binding.rgFont.check(R.id.rbLight)
            OverlayPrefs.FONT_SERIF -> binding.rgFont.check(R.id.rbSerif)
            OverlayPrefs.FONT_MONO -> binding.rgFont.check(R.id.rbMono)
            OverlayPrefs.FONT_CONDENSED -> binding.rgFont.check(R.id.rbCondensed)
            else -> binding.rgFont.check(R.id.rbSans)
        }
        binding.rgFont.setOnCheckedChangeListener { _, id ->
            overlayPrefs.fontStyle = when (id) {
                R.id.rbLight -> OverlayPrefs.FONT_LIGHT
                R.id.rbSerif -> OverlayPrefs.FONT_SERIF
                R.id.rbMono -> OverlayPrefs.FONT_MONO
                R.id.rbCondensed -> OverlayPrefs.FONT_CONDENSED
                else -> OverlayPrefs.FONT_SANS
            }
            notifyOverlay()
        }

        when (overlayPrefs.animation) {
            OverlayPrefs.ANIM_SCALE -> binding.rgAnim.check(R.id.rbScale)
            OverlayPrefs.ANIM_SLIDE -> binding.rgAnim.check(R.id.rbSlide)
            else -> binding.rgAnim.check(R.id.rbFade)
        }
        binding.rgAnim.setOnCheckedChangeListener { _, id ->
            overlayPrefs.animation = when (id) {
                R.id.rbScale -> OverlayPrefs.ANIM_SCALE
                R.id.rbSlide -> OverlayPrefs.ANIM_SLIDE
                else -> OverlayPrefs.ANIM_FADE
            }
            notifyOverlay()
        }

        listOf(
            binding.colorWhite to Color.WHITE,
            binding.colorPink to 0xFFFF8A80.toInt(),
            binding.colorPurple to 0xFFEA80FC.toInt(),
            binding.colorBlue to 0xFF8C9EFF.toInt(),
            binding.colorCyan to 0xFF84FFFF.toInt(),
            binding.colorGold to 0xFFFFF59D.toInt()
        ).forEach { (view, color) ->
            view.setOnClickListener {
                overlayPrefs.textColor = color
                notifyOverlay()
            }
        }

        binding.btnResetPos.setOnClickListener {
            overlayPrefs.resetPosition()
            notifyOverlay()
        }
    }

    private fun notifyOverlay() {
        sendBroadcast(Intent(OverlayPrefs.ACTION_PREFS_CHANGED).setPackage(packageName))
    }
}
