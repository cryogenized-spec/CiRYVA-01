package dev.ciryva.app

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatVisualAssetsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir get() = File(context.filesDir, "chat_visual")

    @Test fun selectedWallpaperIsCroppedToNineBySixteenAndRemainsPrivate() {
        val image = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
        val source = File(context.filesDir, "ciryva_media/" + UUID.randomUUID().toString())
        source.parentFile?.mkdirs()
        try {
            ByteArrayOutputStream().use { buffer ->
                image.compress(Bitmap.CompressFormat.PNG, 100, buffer)
                source.writeBytes(buffer.toByteArray())
            }
            val uri = FileProvider.getUriForFile(context,
                context.packageName + ".media", source)
            val assets = ChatVisualAssets(context)
            assets.importWallpaper(uri)
            val result = assets.wallpaperImage()
            assertNotNull(result)
            assertEquals(1080, result!!.width)
            assertEquals(1920, result.height)
            assets.removeWallpaper()
            assertNull(assets.wallpaperImage())
            assertFalse(assets.hasWallpaper())
        } finally {
            image.recycle()
            source.delete()
        }
    }

    @Test fun malformedFontIsRejectedAndNeverCached() {
        val source = File(context.filesDir, "ciryva_media/" + UUID.randomUUID().toString())
        source.parentFile?.mkdirs()
        source.writeText("This is not a font")
        try {
            val uri = FileProvider.getUriForFile(context,
                context.packageName + ".media", source)
            val assets = ChatVisualAssets(context)
            assets.removeTypeface()
            assertThrows(IllegalArgumentException::class.java) {
                assets.importTypeface(uri)
            }
            assertNull(assets.importedTypeface())
        } finally { source.delete() }
    }
}
