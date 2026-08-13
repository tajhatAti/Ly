package com.ahad.lyricsoverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ahad.lyricsoverlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(this, getString(R.string.overlay_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* overlay still works without notifications on older devices */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermission()
        updateStatus()

        binding.btnStart.setOnClickListener { ensureOverlayAndStart() }
        binding.btnStop.setOnClickListener { stopOverlayService() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureOverlayAndStart() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, LyricsOverlayService::class.java).apply {
            action = LyricsOverlayService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
        Toast.makeText(this, getString(R.string.overlay_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, LyricsOverlayService::class.java).apply {
            action = LyricsOverlayService.ACTION_STOP
        }
        startService(intent)
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        binding.tvPermissionStatus.text = if (overlayOk) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_needed)
        }
        binding.tvServiceStatus.text = if (LyricsOverlayService.isRunning) {
            getString(R.string.service_running)
        } else {
            getString(R.string.service_stopped)
        }
    }
}
