package com.example.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.NotificationRecord
import com.example.util.MediaType
import com.example.util.MediaFileSaver
import com.example.util.NotificationMediaResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android NotificationListenerService that captures incoming notifications,
 * inspects MessagingStyle attachments for Content URIs, extracts the raw binary streams
 * into local storage (Photos, Videos, Voice Notes), and saves records into Room DB.
 */
class MediaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "MediaNotificationListenerService connected and active.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "MediaNotificationListenerService disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val packageName = sbn.packageName ?: ""

        // Process notification in background coroutine
        serviceScope.launch {
            processAndSaveNotification(
                context = applicationContext,
                sbn = sbn,
                notification = notification,
                packageName = packageName
            )
        }
    }

    companion object {
        private const val TAG = "MediaNotifListener"

        /**
         * Core processing logic, decoupled so it can be called from onNotificationPosted
         * as well as test / simulation harnesses.
         */
        suspend fun processAndSaveNotification(
            context: Context,
            sbn: StatusBarNotification?,
            notification: Notification,
            packageName: String
        ): NotificationRecord {
            val timestamp = sbn?.postTime ?: System.currentTimeMillis()
            val extras = notification.extras ?: Bundle()

            // 1. Resolve Application Label (Friendly App Name)
            val appName = try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }

            // 2. Extract Title and Text
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(NotificationCompat.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: appName

            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            // 3. Deep Inspection of MessagingStyle Attachments & Content URIs
            val resolvedMedia = NotificationMediaResolver.resolveMedia(context, notification)

            var mediaFilePath: String? = null
            var mediaTypeStr = "NONE"
            var mediaMimeType: String? = null
            var originalUriStr: String? = null

            if (resolvedMedia != null) {
                mediaMimeType = resolvedMedia.mimeType
                originalUriStr = resolvedMedia.uri?.toString()

                // Case A: Content URI is present -> Stream directly via ContentResolver
                if (resolvedMedia.uri != null) {
                    Log.d(TAG, "Extracting stream from Content URI: ${resolvedMedia.uri}")
                    mediaFilePath = MediaFileSaver.saveStreamFromUri(
                        context = context,
                        contentUri = resolvedMedia.uri,
                        mediaType = resolvedMedia.mediaType,
                        mimeType = resolvedMedia.mimeType,
                        timestamp = timestamp
                    )
                    if (mediaFilePath != null) {
                        mediaTypeStr = resolvedMedia.mediaType.name
                    }
                }

                // Case B: Fallback to high-res Bitmap if URI streaming failed or wasn't provided
                if (mediaFilePath == null && resolvedMedia.bitmap != null) {
                    Log.d(TAG, "Extracting fallback preview bitmap for $packageName")
                    mediaFilePath = MediaFileSaver.saveBitmap(
                        context = context,
                        bitmap = resolvedMedia.bitmap,
                        timestamp = timestamp
                    )
                    if (mediaFilePath != null) {
                        mediaTypeStr = MediaType.IMAGE.name
                        if (mediaMimeType == null) mediaMimeType = "image/jpeg"
                    }
                }
            }

            // Fallback for placeholder text like "Photo", "1 photo", "Audio message", "Video"
            // if we now have the actual media file saved
            val displayText = when {
                text.isNotBlank() -> text
                mediaTypeStr == "IMAGE" -> "Photo"
                mediaTypeStr == "VIDEO" -> "Video"
                mediaTypeStr == "AUDIO" -> "Voice Note"
                else -> ""
            }

            // 4. Save Record into Room Database
            val record = NotificationRecord(
                packageName = packageName,
                appName = appName,
                title = title,
                text = displayText,
                timestamp = timestamp,
                mediaType = mediaTypeStr,
                mediaMimeType = mediaMimeType,
                originalUri = originalUriStr,
                mediaFilePath = mediaFilePath,
                isRead = false
            )

            try {
                val db = AppDatabase.getDatabase(context)
                val id = db.notificationDao().insertNotification(record)
                Log.i(TAG, "Saved notification #$id ($mediaTypeStr) with media: $mediaFilePath")
                return record.copy(id = id)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting notification into Room DB: ${e.message}", e)
                return record
            }
        }
    }
}
