package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_records ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_records WHERE media_file_path IS NOT NULL ORDER BY timestamp DESC")
    fun getMediaNotifications(): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_records WHERE mediaType = :type ORDER BY timestamp DESC")
    fun getNotificationsByType(type: String): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_records WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: Long): NotificationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(record: NotificationRecord): Long

    @Query("DELETE FROM notification_records WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM notification_records")
    suspend fun clearAll()
}
