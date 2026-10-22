package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_records")
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaType: String = "NONE", // "IMAGE", "VIDEO", "AUDIO", "NONE"
    val mediaMimeType: String? = null,
    val originalUri: String? = null,
    @ColumnInfo(name = "media_file_path")
    val mediaFilePath: String? = null,
    val isRead: Boolean = false
)
