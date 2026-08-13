package com.ahad.lyricsoverlay

import android.app.Application

class LyricsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this).applyNightMode()
    }
}
