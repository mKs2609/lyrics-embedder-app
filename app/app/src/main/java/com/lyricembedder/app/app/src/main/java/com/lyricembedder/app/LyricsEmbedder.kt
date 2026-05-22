package com.lyricembedder.app

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

class LyricsEmbedder(private val context: Context) {

    fun embedLyrics(sourceUri: Uri, syncedLyrics: String, plainLyrics: String): Boolean {
        // Step 1: Clone file to app-private cache to bypass SAF path limitations
        val cacheFile = File(context.cacheDir, "processing_target.flac")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }?: return false
        } catch (e: Exception) {
            return false
        }

        // Step 2: Modify metadata tags inside sandbox
        try {
            val audioFile = AudioFileIO.read(cacheFile)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // Map payloads directly to Vorbis Comment standard keys
            tag.setField(FieldKey.LYRICS, syncedLyrics)
            tag.setField(FieldKey.UNSYNCEDLYRICS, plainLyrics)
            
            audioFile.commit()
        } catch (e: Exception) {
            cacheFile.delete()
            return false
        }

        // Step 3: Stream updated binary data back into storage using Truncate mode
        try {
            // Opening the channel with "rwt" mode zeroes the target size first
            context.contentResolver.openOutputStream(sourceUri, "rwt")?.use { output ->
                cacheFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }?: throw Exception("Piping write access failed")
        } catch (e: Exception) {
            return false
        } finally {
            cacheFile.delete() // Clean cache footprint
        }

        // Step 4: Force system to reindex Vorbis Comment structure [2]
        refreshMediaStore(sourceUri)
        return true
    }

    private fun refreshMediaStore(uri: Uri) {
        val path = getRealPathFromURI(uri)?: return
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/flac")) { _, _ -> } // [2]
    }

    private fun getRealPathFromURI(contentUri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        context.contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            if (cursor.moveToFirst()) {
                return cursor.getString(columnIndex)
            }
        }
        return null
    }
}
