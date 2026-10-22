package com.example.data

import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationDao) {
    val allNotifications: Flow<List<NotificationRecord>> = dao.getAllNotifications()
    val mediaNotifications: Flow<List<NotificationRecord>> = dao.getMediaNotifications()

    fun getNotificationsByType(type: String): Flow<List<NotificationRecord>> =
        dao.getNotificationsByType(type)

    suspend fun insert(record: NotificationRecord): Long =
        dao.insertNotification(record)

    suspend fun delete(id: Long) =
        dao.deleteNotification(id)

    suspend fun clearAll() =
        dao.clearAll()
}
