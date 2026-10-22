package com.example

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import com.example.util.MediaFileSaver
import com.example.util.MediaType
import com.example.util.NotificationMediaResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Media Notif Listener", appName)
    }

    @Test
    fun `resolve media from MessagingStyle dataUri`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeUri = Uri.parse("content://media/external/images/media/42")
        val person = Person.Builder().setName("Alice").build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .addMessage(
                NotificationCompat.MessagingStyle.Message("Check this out", System.currentTimeMillis(), person)
                    .setData("image/jpeg", fakeUri)
            )

        val notification = NotificationCompat.Builder(context, "test_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Alice")
            .setContentText("Photo")
            .setStyle(messagingStyle)
            .build()

        val resolved = NotificationMediaResolver.resolveMedia(context, notification)
        assertNotNull(resolved)
        assertEquals(fakeUri, resolved?.uri)
        assertEquals("image/jpeg", resolved?.mimeType)
        assertEquals(MediaType.IMAGE, resolved?.mediaType)
    }

    @Test
    fun `resolve media fallback from BigPictureStyle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val notification = NotificationCompat.Builder(context, "test_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Bob")
            .setContentText("Photo")
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            .build()

        val resolved = NotificationMediaResolver.resolveMedia(context, notification)
        assertNotNull(resolved)
        assertEquals(MediaType.IMAGE, resolved?.mediaType)
        assertNotNull(resolved?.bitmap)
    }

    @Test
    fun `save preview bitmap to images folder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        val savedPath = MediaFileSaver.saveBitmap(context, bitmap, 123456789L)
        assertNotNull(savedPath)
        val file = File(savedPath!!)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("IMG_123456789"))
        assertTrue(file.parentFile?.name == "Images")
    }
}
