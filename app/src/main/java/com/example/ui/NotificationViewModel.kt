package com.example.ui

import android.app.Application
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.NotificationRecord
import com.example.data.NotificationRepository
import com.example.service.MediaNotificationListenerService
import com.example.util.MediaFileSaver
import com.example.util.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NotificationRepository
    val allNotifications: StateFlow<List<NotificationRecord>>

    val selectedFilter = MutableStateFlow("ALL") // "ALL", "IMAGE", "VIDEO", "AUDIO"
    val searchQuery = MutableStateFlow("")
    val isListenerPermissionGranted = MutableStateFlow(false)

    // Audio Player State for Voice Notes
    val currentlyPlayingPath = MutableStateFlow<String?>(null)
    val isPlayingAudio = MutableStateFlow(false)
    val audioProgress = MutableStateFlow(0f)
    private var mediaPlayer: MediaPlayer? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying && player.duration > 0) {
                    audioProgress.value = player.currentPosition.toFloat() / player.duration.toFloat()
                    progressHandler.postDelayed(this, 100)
                }
            }
        }
    }

    init {
        val dao = AppDatabase.getDatabase(application).notificationDao()
        repository = NotificationRepository(dao)

        allNotifications = repository.allNotifications
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        checkPermission()
    }

    val filteredNotifications: StateFlow<List<NotificationRecord>> = combine(
        allNotifications,
        selectedFilter,
        searchQuery
    ) { list, filter, query ->
        list.filter { item ->
            val matchesFilter = when (filter) {
                "IMAGE" -> item.mediaType == "IMAGE"
                "VIDEO" -> item.mediaType == "VIDEO"
                "AUDIO" -> item.mediaType == "AUDIO"
                else -> true
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                item.title.contains(query, ignoreCase = true) ||
                        item.text.contains(query, ignoreCase = true) ||
                        item.appName.contains(query, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun checkPermission() {
        val context = getApplication<Application>()
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        isListenerPermissionGranted.value = enabledPackages.contains(context.packageName)
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            stopAudio()
            repository.clearAll()
        }
    }

    // --- Audio Playback Control ---

    fun toggleAudioPlayback(filePath: String) {
        if (currentlyPlayingPath.value == filePath && isPlayingAudio.value) {
            pauseAudio()
        } else {
            playAudio(filePath)
        }
    }

    private fun playAudio(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        stopAudio()
        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    isPlayingAudio.value = false
                    audioProgress.value = 1f
                    progressHandler.removeCallbacks(progressRunnable)
                }
                start()
            }
            mediaPlayer = player
            currentlyPlayingPath.value = filePath
            isPlayingAudio.value = true
            progressHandler.post(progressRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isPlayingAudio.value = false
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun stopAudio() {
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingAudio.value = false
        currentlyPlayingPath.value = null
        audioProgress.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }

    // --- Simulator Helpers for Testing ---

    fun simulateIncomingPhotoNotification(useContentUri: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val timestamp = System.currentTimeMillis()

            // Generate an attractive sample photo bitmap
            val bitmap = createSampleBitmap("Photo from Alex", "Sunset at Malibu Beach", 0xFF0D9488.toInt(), 0xFF14B8A6.toInt())

            val builder = NotificationCompat.Builder(context, "media_channel")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Alex Rivera")
                .setContentText("Photo")

            if (useContentUri) {
                // Save bitmap to a test file in cache and obtain file Uri to simulate Content URI streaming
                val tempFile = File(context.cacheDir, "sim_photo_$timestamp.jpg")
                FileOutputStream(tempFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                val contentUri = Uri.fromFile(tempFile)

                // Simulate MessagingStyle with dataUri and dataMimeType
                val person = Person.Builder().setName("Alex Rivera").build()
                val messagingStyle = NotificationCompat.MessagingStyle(person)
                    .addMessage(
                        NotificationCompat.MessagingStyle.Message("Check out this view!", timestamp, person)
                            .setData("image/jpeg", contentUri)
                    )
                builder.setStyle(messagingStyle)
            } else {
                // Simulate platform hiding direct URIs: only providing expanded preview bitmap (EXTRA_PICTURE)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText("1 photo received")
                )
            }

            val notification = builder.build()
            MediaNotificationListenerService.processAndSaveNotification(
                context = context,
                sbn = null,
                notification = notification,
                packageName = "com.whatsapp"
            )
        }
    }

    fun simulateIncomingVoiceNoteNotification() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val timestamp = System.currentTimeMillis()

            // Create a valid playable WAV audio file with pleasant tones
            val tempAudioFile = File(context.cacheDir, "sim_voice_$timestamp.m4a")
            createToneAudioFile(tempAudioFile, 440.0, 3.0)
            val audioUri = Uri.fromFile(tempAudioFile)

            val person = Person.Builder().setName("Sarah Chen").build()
            val messagingStyle = NotificationCompat.MessagingStyle(person)
                .addMessage(
                    NotificationCompat.MessagingStyle.Message("Voice note (0:03)", timestamp, person)
                        .setData("audio/m4a", audioUri)
                )

            val builder = NotificationCompat.Builder(context, "media_channel")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Sarah Chen")
                .setContentText("Voice message (0:03)")
                .setStyle(messagingStyle)

            val notification = builder.build()
            MediaNotificationListenerService.processAndSaveNotification(
                context = context,
                sbn = null,
                notification = notification,
                packageName = "com.whatsapp"
            )
        }
    }

    fun simulateIncomingVideoNotification() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val timestamp = System.currentTimeMillis()

            // Create simulated video content
            val tempVideoFile = File(context.cacheDir, "sim_video_$timestamp.mp4")
            FileOutputStream(tempVideoFile).use {
                // Write dummy binary stream with MP4 header signature
                it.write(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32))
                it.write(ByteArray(1024 * 32) { index -> (index % 128).toByte() })
            }
            val videoUri = Uri.fromFile(tempVideoFile)

            val person = Person.Builder().setName("Liam Smith").build()
            val messagingStyle = NotificationCompat.MessagingStyle(person)
                .addMessage(
                    NotificationCompat.MessagingStyle.Message("Video attachment", timestamp, person)
                        .setData("video/mp4", videoUri)
                )

            val builder = NotificationCompat.Builder(context, "media_channel")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Liam Smith")
                .setContentText("Video (0:15)")
                .setStyle(messagingStyle)

            val notification = builder.build()
            MediaNotificationListenerService.processAndSaveNotification(
                context = context,
                sbn = null,
                notification = notification,
                packageName = "com.facebook.orca"
            )
        }
    }

    private fun createSampleBitmap(title: String, subtitle: String, topColor: Int, bottomColor: Int): Bitmap {
        val width = 720
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Draw background gradient
        paint.color = topColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Accent card inside
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 24f, 24f, paint)

        // Title
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText(title, 80f, 160f, paint)

        // Subtitle
        paint.textSize = 24f
        paint.isFakeBoldText = false
        paint.color = 0xDDFFFFFF.toInt()
        canvas.drawText(subtitle, 80f, 220f, paint)

        // Decorative camera icon graphic
        paint.color = 0x44FFFFFF
        canvas.drawCircle(width - 160f, 180f, 60f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(width - 160f, 180f, 30f, paint)

        return bitmap
    }

    private fun createToneAudioFile(file: File, freqHz: Double, durationSec: Double) {
        val sampleRate = 44100
        val numSamples = (durationSec * sampleRate).toInt()
        val pcmData = ByteArray(numSamples * 2)

        var angle = 0.0
        val angleIncrement = 2.0 * Math.PI * freqHz / sampleRate

        for (i in 0 until numSamples) {
            val sample = (Math.sin(angle) * 32767).toInt().toShort()
            pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            angle += angleIncrement
        }

        // Write as valid WAV file
        FileOutputStream(file).use { out ->
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size
            header.putShort(1.toShort()) // AudioFormat PCM
            header.putShort(1.toShort()) // NumChannels Mono
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(2.toShort()) // BlockAlign
            header.putShort(16.toShort()) // BitsPerSample
            header.put("data".toByteArray())
            header.putInt(pcmData.size)

            out.write(header.array())
            out.write(pcmData)
        }
    }
}
