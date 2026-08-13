package com.ahad.lyricsoverlay

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LrclibClient {

    fun fetchRaw(title: String, artist: String, durationMs: Long): String? {
        for ((t, a) in TrackNames.variants(title, artist)) {
            getExact(t, a, durationMs)?.let { return it }
            search(t, a, durationMs)?.let { return it }
        }
        val q = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        return searchQuery(q, title, artist, durationMs)
    }

    fun fetch(title: String, artist: String, durationMs: Long): Pair<String, List<LyricLine>>? {
        val raw = fetchRaw(title, artist, durationMs) ?: return null
        val lines = toLines(raw)
        return if (lines.isEmpty()) null else "$artist — $title" to lines
    }

    fun toLines(raw: String): List<LyricLine> {
        val synced = LrcParser.parse(raw)
        if (synced.isNotEmpty()) return synced
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") }
            .mapIndexed { i, t -> LyricLine(i * 3500L, t) }
            .toList()
    }

    private fun getExact(title: String, artist: String, durationMs: Long): String? {
        if (title.isBlank()) return null
        val params = buildString {
            append("track_name=").append(enc(title))
            if (artist.isNotBlank()) append("&artist_name=").append(enc(artist))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000)
        }
        val body = httpGet("https://lrclib.net/api/get?$params") ?: return null
        return lyricsFromObject(JSONObject(body))
    }

    private fun search(title: String, artist: String, durationMs: Long): String? {
        val params = buildString {
            append("track_name=").append(enc(title))
            if (artist.isNotBlank()) append("&artist_name=").append(enc(artist))
        }
        val body = httpGet("https://lrclib.net/api/search?$params") ?: return null
        return pickFromArray(JSONArray(body), title, artist, durationMs)
    }

    private fun searchQuery(q: String, title: String, artist: String, durationMs: Long): String? {
        if (q.isBlank()) return null
        val body = httpGet("https://lrclib.net/api/search?q=${enc(q)}") ?: return null
        return pickFromArray(JSONArray(body), title, artist, durationMs)
    }

    private fun pickFromArray(arr: JSONArray, title: String, artist: String, durationMs: Long): String? {
        if (arr.length() == 0) return null
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            var score = 0
            val tn = o.optString("trackName")
            val an = o.optString("artistName")
            if (tn.equals(title, true)) score += 6
            if (tn.contains(title, true) || title.contains(tn, true)) score += 2
            if (artist.isNotBlank() && an.contains(artist, true)) score += 4
            if (o.optString("syncedLyrics").isNotBlank()) score += 5
            val durSec = o.optInt("duration", -1)
            if (durationMs > 0 && durSec > 0) {
                val diff = kotlin.math.abs(durSec - (durationMs / 1000).toInt())
                if (diff <= 2) score += 4 else if (diff <= 10) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        return best?.let { lyricsFromObject(it) }
    }

    private fun lyricsFromObject(o: JSONObject): String? {
        val synced = o.optString("syncedLyrics")
        if (synced.isNotBlank() && synced != "null") return synced
        val plain = o.optString("plainLyrics")
        if (plain.isNotBlank() && plain != "null") return plain
        return null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LyricsOverlay/2.0 (https://github.com/tajhatAti/Ly)")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else null
            stream?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
