package com.ahad.lyricsoverlay

import android.content.ComponentName
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
        if (Settings.canDrawOverlays(this) && hasNotificationAccess()) {
            startOverlayService()
        } else {
            updateStatus()
        }
    }

    private val notificationAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this) && hasNotificationAccess()) {
            startOverlayService()
        }
        updateStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        updateStatus()

        binding.btnNotificationAccess.setOnClickListener { openNotificationAccess() }
        binding.btnStart.setOnClickListener { ensureAndStart() }
        binding.btnStop.setOnClickListener { stopOverlayService() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun ensureAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (!hasNotificationAccess()) {
            Toast.makeText(this, getString(R.string.need_notification_access), Toast.LENGTH_LONG).show()
            openNotificationAccess()
            return
        }
        startOverlayService()
    }

    private fun openNotificationAccess() {
        notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val me = ComponentName(this, MediaNotificationListener::class.java).flattenToString()
        val shortName = ComponentName(this, MediaNotificationListener::class.java).flattenToShortString()
        return enabled.split(':').any { it == me || it == shortName }
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
        startService(
            Intent(this, LyricsOverlayService::class.java).setAction(LyricsOverlayService.ACTION_STOP)
        )
        updateStatus()
    }

    private fun updateStatus() {
        binding.tvPermissionStatus.text = if (Settings.canDrawOverlays(this)) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_needed)
        }
        binding.tvNotificationStatus.text = if (hasNotificationAccess()) {
            getString(R.string.notif_access_granted)
        } else {
            getString(R.string.notif_access_needed)
        }
        binding.tvServiceStatus.text = if (LyricsOverlayService.isRunning) {
            getString(R.string.service_running)
        } else {
            getString(R.string.service_stopped)
        }
    }
}
