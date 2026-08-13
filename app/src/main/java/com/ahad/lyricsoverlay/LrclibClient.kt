package com.ahad.lyricsoverlay

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LrclibClient {

    fun fetchRaw(title: String, artist: String, durationMs: Long): String? {
        val q = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (q.isBlank()) return null
        val searchUrl =
            "https://lrclib.net/api/search?q=${URLEncoder.encode(q, "UTF-8")}"
        val arr = JSONArray(httpGet(searchUrl) ?: return null)
        if (arr.length() == 0) return null
        val picked = pickBest(arr, title, artist, durationMs) ?: return null
        val synced = picked.optString("syncedLyrics")
        if (synced.isNotBlank()) return synced
        val plain = picked.optString("plainLyrics")
        return plain.takeIf { it.isNotBlank() }
    }

    fun fetch(title: String, artist: String, durationMs: Long): Pair<String, List<LyricLine>>? {
        val raw = fetchRaw(title, artist, durationMs) ?: return null
        val lines = LrcParser.parse(raw)
        if (lines.isNotEmpty()) return "$artist — $title" to lines
        val fallback = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, t -> LyricLine(i * 4000L, t) }
            .toList()
        return if (fallback.isEmpty()) null else "$artist — $title" to fallback
    }

    private fun titleLine(obj: JSONObject): String {
        val t = obj.optString("trackName")
        val a = obj.optString("artistName")
        return listOf(a, t).filter { it.isNotBlank() }.joinToString(" — ")
    }

    private fun pickBest(
        arr: JSONArray,
        title: String,
        artist: String,
        durationMs: Long
    ): JSONObject? {
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            var score = 0
            if (o.optString("trackName").equals(title, ignoreCase = true)) score += 5
            if (o.optString("artistName").contains(artist, ignoreCase = true)) score += 3
            if (o.optString("syncedLyrics").isNotBlank()) score += 4
            val durSec = o.optInt("duration", -1)
            if (durationMs > 0 && durSec > 0) {
                val diff = kotlin.math.abs(durSec - (durationMs / 1000).toInt())
                if (diff <= 2) score += 3 else if (diff <= 8) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        return best
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LyricsOverlay/1.0 (com.ahad.lyricsoverlay)")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
