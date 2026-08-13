package com.ahad.lyricsoverlay

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.ahad.lyricsoverlay.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: OverlayPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = OverlayPrefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.seekSize.max = 36
        binding.seekSize.progress = (prefs.fontSizeSp - 12).toInt().coerceIn(0, 36)
        binding.tvSizeValue.text = "${prefs.fontSizeSp.toInt()} sp"
        binding.seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 12f + progress
                binding.tvSizeValue.text = "${size.toInt()} sp"
                if (fromUser) {
                    prefs.fontSizeSp = size
                    notifyPrefs()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        when (prefs.fontStyle) {
            OverlayPrefs.FONT_LIGHT -> binding.rgFont.check(R.id.rbLight)
            OverlayPrefs.FONT_SERIF -> binding.rgFont.check(R.id.rbSerif)
            OverlayPrefs.FONT_MONO -> binding.rgFont.check(R.id.rbMono)
            OverlayPrefs.FONT_CONDENSED -> binding.rgFont.check(R.id.rbCondensed)
            else -> binding.rgFont.check(R.id.rbSans)
        }
        binding.rgFont.setOnCheckedChangeListener { _, id ->
            prefs.fontStyle = when (id) {
                R.id.rbLight -> OverlayPrefs.FONT_LIGHT
                R.id.rbSerif -> OverlayPrefs.FONT_SERIF
                R.id.rbMono -> OverlayPrefs.FONT_MONO
                R.id.rbCondensed -> OverlayPrefs.FONT_CONDENSED
                else -> OverlayPrefs.FONT_SANS
            }
            notifyPrefs()
        }

        when (prefs.animation) {
            OverlayPrefs.ANIM_SCALE -> binding.rgAnim.check(R.id.rbScale)
            OverlayPrefs.ANIM_SLIDE -> binding.rgAnim.check(R.id.rbSlide)
            else -> binding.rgAnim.check(R.id.rbFade)
        }
        binding.rgAnim.setOnCheckedChangeListener { _, id ->
            prefs.animation = when (id) {
                R.id.rbScale -> OverlayPrefs.ANIM_SCALE
                R.id.rbSlide -> OverlayPrefs.ANIM_SLIDE
                else -> OverlayPrefs.ANIM_FADE
            }
            notifyPrefs()
        }

        val chips = listOf(
            binding.colorWhite to Color.WHITE,
            binding.colorPink to 0xFFFF8A80.toInt(),
            binding.colorPurple to 0xFFEA80FC.toInt(),
            binding.colorBlue to 0xFF8C9EFF.toInt(),
            binding.colorCyan to 0xFF84FFFF.toInt(),
            binding.colorGold to 0xFFFFF59D.toInt()
        )
        chips.forEach { (view, color) ->
            view.setOnClickListener {
                prefs.textColor = color
                notifyPrefs()
            }
        }

        binding.btnResetPos.setOnClickListener {
            prefs.resetPosition()
            notifyPrefs()
        }
    }

    private fun notifyPrefs() {
        sendBroadcast(Intent(OverlayPrefs.ACTION_PREFS_CHANGED).setPackage(packageName))
    }
}
