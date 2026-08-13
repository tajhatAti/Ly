package com.ahad.lyricsoverlay

import android.content.Context
import java.io.File
import java.security.MessageDigest

object LyricsRepository {

    fun load(context: Context, song: Song): List<LyricLine> {
        val cached = cacheFile(context, song)
        if (cached.exists()) {
            val lines = LrcParser.parse(cached.readText())
            if (lines.isNotEmpty()) return lines
        }

        val online = try {
            LrclibClient.fetchRaw(song.title, song.artist, song.durationMs)
        } catch (_: Exception) {
            null
        }
        if (!online.isNullOrBlank()) {
            try {
                cached.writeText(online)
            } catch (_: Exception) {
            }
            val lines = LrcParser.parse(online)
            if (lines.isNotEmpty()) return lines
        }

        val local = sidecarLrc(song.path)
        if (local != null) {
            try {
                cached.writeText(local)
            } catch (_: Exception) {
            }
            return LrcParser.parse(local)
        }
        return emptyList()
    }

    private fun sidecarLrc(path: String): String? {
        if (path.isBlank()) return null
        val audio = File(path)
        val lrc = File(audio.parentFile ?: return null, audio.nameWithoutExtension + ".lrc")
        return if (lrc.exists()) {
            try {
                lrc.readText()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    private fun cacheFile(context: Context, song: Song): File {
        val dir = File(context.filesDir, "lyrics_cache").apply { mkdirs() }
        val key = md5("${song.artist}|${song.title}|${song.durationMs}")
        return File(dir, "$key.lrc")
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
