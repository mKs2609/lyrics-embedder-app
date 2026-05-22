package com.lyricembedder.app

import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class LrcLibClient {
    private val client = OkHttpClient()
    private val gson = Gson()

    // Model representing the payload schema returned by the database 
    data class LyricsResponse(
        val id: Long,
        val trackName: String,
        val artistName: String,
        val albumName: String?,
        val duration: Double,
        val plainLyrics: String?,
        val syncedLyrics: String?
    )

    @Throws(IOException::class)
    fun fetchLyrics(title: String, artist: String, album: String, durationSec: Double): LyricsResponse? {
        val url = HttpUrl.Builder()
           .scheme("https")
           .host("lrclib.net")
           .addPathSegment("api")
           .addPathSegment("get") // Hits the exact metadata matching endpoint 
           .addQueryParameter("track_name", title)
           .addQueryParameter("artist_name", artist)
           .addQueryParameter("album_name", album)
           .addQueryParameter("duration", durationSec.toInt().toString())
           .build()

        val request = Request.Builder()
           .url(url)
           .header("User-Agent", "SyncedLyricsEmbedder/1.0 (https://github.com/yourprofile/repository)")
           .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string()?: return null
            return gson.fromJson(bodyString, LyricsResponse::class.java)
        }
    }
}
