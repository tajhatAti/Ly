package com.ahad.lyricsoverlay

import android.content.Context
import java.io.File
import java.security.MessageDigest

object LyricsRepository {

    fun load(context: Context, song: Song): List<LyricLine> {
        val cached = cacheFile(context, song)
        if (cached.exists()) {
            val lines = LrclibClient.toLines(cached.readText())
            if (lines.isNotEmpty()) return lines
            cached.delete()
        }

        val online = try {
            LrclibClient.fetchRaw(song.title, song.artist, song.durationMs)
        } catch (_: Exception) {
            null
        }
        if (!online.isNullOrBlank()) {
            val lines = LrclibClient.toLines(online)
            if (lines.isNotEmpty()) {
                try {
                    cached.writeText(online)
                } catch (_: Exception) {
                }
                return lines
            }
        }

        val local = sidecarLrc(song.path)
        if (!local.isNullOrBlank()) {
            val lines = LrclibClient.toLines(local)
            if (lines.isNotEmpty()) {
                try {
                    cached.writeText(local)
                } catch (_: Exception) {
                }
                return lines
            }
        }
        return emptyList()
    }

    private fun sidecarLrc(path: String): String? {
        if (path.isBlank()) return null
        val audio = File(path)
        val parent = audio.parentFile ?: return null
        val lrc = File(parent, audio.nameWithoutExtension + ".lrc")
        return if (lrc.canRead()) {
            try {
                lrc.readText()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    private fun cacheFile(context: Context, song: Song): File {
        val dir = File(context.filesDir, "lyrics_cache").apply { mkdirs() }
        val key = md5("${song.artist.lowercase()}|${song.title.lowercase()}")
        return File(dir, "$key.lrc")
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
