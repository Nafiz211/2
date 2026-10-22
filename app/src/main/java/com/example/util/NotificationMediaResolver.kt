package com.example.util

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.core.app.NotificationCompat

enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    NONE
}

data class ResolvedMedia(
    val uri: Uri? = null,
    val mimeType: String? = null,
    val mediaType: MediaType = MediaType.NONE,
    val bitmap: Bitmap? = null,
    val messageText: String? = null,
    val senderName: String? = null
)

/**
 * Utility for deep inspection of Notification payloads and MessagingStyle attachments,
 * extracting Content URIs (content://...), MIME types, and fallback high-resolution preview bitmaps.
 */
object NotificationMediaResolver {

    private const val TAG = "NotifMediaResolver"

    /**
     * Inspects a Notification to extract media attachments.
     * Prioritizes direct Content URIs from MessagingStyle messages,
     * with fallback to expanded notification preview bitmaps (EXTRA_PICTURE / EXTRA_PICTURE_ICON).
     */
    fun resolveMedia(context: Context, notification: Notification): ResolvedMedia? {
        // 1. Deep Inspection of MessagingStyle Attachments via AndroidX NotificationCompat
        val compatMedia = extractFromMessagingStyleCompat(notification)
        if (compatMedia != null) {
            Log.d(TAG, "Resolved media via NotificationCompat.MessagingStyle: type=${compatMedia.mediaType}, uri=${compatMedia.uri}")
            return compatMedia
        }

        // 2. Direct inspection of Notification.EXTRA_MESSAGES bundle array
        val bundleMedia = extractFromRawMessageBundles(notification)
        if (bundleMedia != null) {
            Log.d(TAG, "Resolved media via raw EXTRA_MESSAGES bundles: type=${bundleMedia.mediaType}, uri=${bundleMedia.uri}")
            return bundleMedia
        }

        // 3. Check for dedicated media URIs in notification extras
        val extraUriMedia = extractFromNotificationExtrasUri(notification)
        if (extraUriMedia != null) {
            Log.d(TAG, "Resolved media via extra URI: type=${extraUriMedia.mediaType}, uri=${extraUriMedia.uri}")
            return extraUriMedia
        }

        // 4. Fallback for Apps Hiding Direct Media URIs: Extract expanded preview bitmap
        val fallbackBitmapMedia = extractExpandedPictureFallback(context, notification)
        if (fallbackBitmapMedia != null) {
            Log.d(TAG, "Resolved media via fallback preview bitmap")
            return fallbackBitmapMedia
        }

        return null
    }

    /**
     * Extracts attachments via NotificationCompat.MessagingStyle.
     */
    private fun extractFromMessagingStyleCompat(notification: Notification): ResolvedMedia? {
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?: return null

        // Inspect messages in reverse order (newest message first)
        val messages = messagingStyle.messages
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            val dataUri = msg.dataUri
            val mimeType = msg.dataMimeType

            if (dataUri != null && !mimeType.isNullOrBlank()) {
                val mediaType = categorizeMimeType(mimeType)
                if (mediaType != MediaType.NONE) {
                    return ResolvedMedia(
                        uri = dataUri,
                        mimeType = mimeType,
                        mediaType = mediaType,
                        messageText = msg.text?.toString(),
                        senderName = msg.person?.name?.toString()
                    )
                }
            }
        }
        return null
    }

    /**
     * Direct inspection of EXTRA_MESSAGES and historicMessages Parcelable arrays.
     */
    private fun extractFromRawMessageBundles(notification: Notification): ResolvedMedia? {
        val extras = notification.extras ?: return null

        val messageKeys = arrayOf(
            Notification.EXTRA_MESSAGES,
            "android.messages",
            "android.historicMessages"
        )

        for (key in messageKeys) {
            val parcelables = getParcelableArrayCompat(extras, key) ?: continue
            for (i in parcelables.indices.reversed()) {
                val item = parcelables[i]
                if (item is Bundle) {
                    val resolved = parseMessageBundle(item)
                    if (resolved != null) {
                        return resolved
                    }
                }
            }
        }
        return null
    }

    /**
     * Parses a single message Bundle for dataUri and dataMimeType.
     */
    private fun parseMessageBundle(bundle: Bundle): ResolvedMedia? {
        val mimeType = bundle.getString("type")
            ?: bundle.getString("mimeType")
            ?: bundle.getString("dataMimeType")

        val rawUri = bundle.get("uri")
            ?: bundle.get("dataUri")
            ?: bundle.get("contentUri")

        val uri: Uri? = when (rawUri) {
            is Uri -> rawUri
            is String -> if (rawUri.isNotBlank()) Uri.parse(rawUri) else null
            else -> null
        }

        if (uri != null && !mimeType.isNullOrBlank()) {
            val mediaType = categorizeMimeType(mimeType)
            if (mediaType != MediaType.NONE) {
                val text = bundle.getCharSequence("text")?.toString()
                val sender = bundle.getCharSequence("sender")?.toString()
                return ResolvedMedia(
                    uri = uri,
                    mimeType = mimeType,
                    mediaType = mediaType,
                    messageText = text,
                    senderName = sender
                )
            }
        }
        return null
    }

    /**
     * Checks extra keys for direct media content URIs (e.g., audioContentsUri).
     */
    private fun extractFromNotificationExtrasUri(notification: Notification): ResolvedMedia? {
        val extras = notification.extras ?: return null

        val uriKeys = arrayOf(
            Notification.EXTRA_AUDIO_CONTENTS_URI,
            "android.audioContentsUri",
            "android.mediaUri",
            "media_uri"
        )

        for (key in uriKeys) {
            val uri = getUriFromExtras(extras, key)
            if (uri != null) {
                val isAudio = key.contains("audio", ignoreCase = true)
                return ResolvedMedia(
                    uri = uri,
                    mimeType = if (isAudio) "audio/m4a" else "image/jpeg",
                    mediaType = if (isAudio) MediaType.AUDIO else MediaType.IMAGE
                )
            }
        }
        return null
    }

    /**
     * Fallback for platforms hiding direct media URIs:
     * Extracts expanded notification preview bitmap (EXTRA_PICTURE or EXTRA_PICTURE_ICON).
     */
    private fun extractExpandedPictureFallback(context: Context, notification: Notification): ResolvedMedia? {
        val extras = notification.extras ?: return null

        // 1. Check EXTRA_PICTURE (Bitmap)
        val pictureBitmap = extras.get(Notification.EXTRA_PICTURE) as? Bitmap
        if (pictureBitmap != null) {
            return ResolvedMedia(
                mediaType = MediaType.IMAGE,
                mimeType = "image/jpeg",
                bitmap = pictureBitmap
            )
        }

        // 2. Check EXTRA_PICTURE_ICON (Icon added in API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pictureIcon = extras.get(Notification.EXTRA_PICTURE_ICON) as? Icon
            if (pictureIcon != null) {
                val drawable = runCatching { pictureIcon.loadDrawable(context) }.getOrNull()
                val bitmap = drawable?.let { drawableToBitmap(it) }
                if (bitmap != null) {
                    return ResolvedMedia(
                        mediaType = MediaType.IMAGE,
                        mimeType = "image/jpeg",
                        bitmap = bitmap
                    )
                }
            }
        }

        // 3. Check EXTRA_LARGE_ICON_BIG (Bitmap)
        val bigLargeIcon = extras.get("android.largeIcon.big") as? Bitmap
        if (bigLargeIcon != null) {
            return ResolvedMedia(
                mediaType = MediaType.IMAGE,
                mimeType = "image/jpeg",
                bitmap = bigLargeIcon
            )
        }

        return null
    }

    /**
     * Categorizes a MIME type into IMAGE, VIDEO, or AUDIO.
     */
    fun categorizeMimeType(mimeType: String): MediaType {
        val lower = mimeType.lowercase()
        return when {
            lower.startsWith("image/") -> MediaType.IMAGE
            lower.startsWith("video/") -> MediaType.VIDEO
            lower.startsWith("audio/") -> MediaType.AUDIO
            else -> MediaType.NONE
        }
    }

    private fun getParcelableArrayCompat(extras: Bundle, key: String): Array<Parcelable>? {
        return extras.getParcelableArray(key)
    }

    private fun getUriFromExtras(extras: Bundle, key: String): Uri? {
        val value = extras.get(key) ?: return null
        return when (value) {
            is Uri -> value
            is String -> if (value.isNotBlank()) Uri.parse(value) else null
            else -> null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
