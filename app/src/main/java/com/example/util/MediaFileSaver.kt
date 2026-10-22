package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Handles streaming and persistence of notification media into local app storage.
 * Organizes files into:
 *  - Photos:      /Images/IMG_timestamp.jpg
 *  - Videos:      /Videos/VID_timestamp.mp4
 *  - Voice Notes: /VoiceNotes/AUD_timestamp.m4a
 */
object MediaFileSaver {

    private const val TAG = "MediaFileSaver"

    const val DIR_IMAGES = "Images"
    const val DIR_VIDEOS = "Videos"
    const val DIR_VOICE_NOTES = "VoiceNotes"

    /**
     * Streams raw binary data directly from a Content URI into local app storage.
     * Returns the absolute path of the saved file or null if failed.
     */
    fun saveStreamFromUri(
        context: Context,
        contentUri: Uri,
        mediaType: MediaType,
        mimeType: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): String? {
        val targetDir = getDirectoryForType(context, mediaType)
        val fileName = generateFileName(mediaType, mimeType, timestamp)
        val destinationFile = File(targetDir, fileName)

        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(contentUri)
            if (inputStream == null) {
                Log.e(TAG, "ContentResolver returned null InputStream for URI: $contentUri")
                return null
            }

            FileOutputStream(destinationFile).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytes: Long = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                outputStream.flush()
                Log.d(TAG, "Successfully streamed $totalBytes bytes from $contentUri to ${destinationFile.absolutePath}")
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                destinationFile.absolutePath
            } else {
                Log.w(TAG, "Saved file is empty or missing: ${destinationFile.absolutePath}")
                destinationFile.delete()
                null
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException reading content URI $contentUri: ${se.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media stream from $contentUri: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            null
        } finally {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Saves a preview Bitmap (e.g. from EXTRA_PICTURE or EXTRA_PICTURE_ICON) into /Images/IMG_timestamp.jpg.
     * Returns the absolute path of the saved file or null if failed.
     */
    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        timestamp: Long = System.currentTimeMillis()
    ): String? {
        val imagesDir = getDirectoryForType(context, MediaType.IMAGE)
        val fileName = "IMG_$timestamp.jpg"
        val destinationFile = File(imagesDir, fileName)

        return try {
            FileOutputStream(destinationFile).use { outputStream ->
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                outputStream.flush()
                if (success && destinationFile.length() > 0) {
                    Log.d(TAG, "Successfully saved preview bitmap to ${destinationFile.absolutePath}")
                    destinationFile.absolutePath
                } else {
                    Log.w(TAG, "Bitmap compression failed or produced empty file")
                    destinationFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            null
        }
    }

    /**
     * Returns directory for the specified media type, creating it if needed.
     */
    fun getDirectoryForType(context: Context, mediaType: MediaType): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val subDirName = when (mediaType) {
            MediaType.IMAGE -> DIR_IMAGES
            MediaType.VIDEO -> DIR_VIDEOS
            MediaType.AUDIO -> DIR_VOICE_NOTES
            MediaType.NONE -> DIR_IMAGES
        }
        val dir = File(baseDir, subDirName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Generates standard file name matching requirements:
     * - Photos -> IMG_timestamp.jpg
     * - Videos -> VID_timestamp.mp4
     * - Voice Notes -> AUD_timestamp.m4a
     */
    private fun generateFileName(
        mediaType: MediaType,
        mimeType: String?,
        timestamp: Long
    ): String {
        return when (mediaType) {
            MediaType.IMAGE -> {
                val ext = when {
                    mimeType?.contains("png", ignoreCase = true) == true -> "png"
                    mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
                    else -> "jpg"
                }
                "IMG_$timestamp.$ext"
            }
            MediaType.VIDEO -> {
                val ext = when {
                    mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
                    mimeType?.contains("3gp", ignoreCase = true) == true -> "3gp"
                    else -> "mp4"
                }
                "VID_$timestamp.$ext"
            }
            MediaType.AUDIO -> {
                val ext = when {
                    mimeType?.contains("ogg", ignoreCase = true) == true -> "ogg"
                    mimeType?.contains("mp3", ignoreCase = true) == true -> "mp3"
                    mimeType?.contains("wav", ignoreCase = true) == true -> "wav"
                    else -> "m4a"
                }
                "AUD_$timestamp.$ext"
            }
            MediaType.NONE -> "FILE_$timestamp.bin"
        }
    }
}
