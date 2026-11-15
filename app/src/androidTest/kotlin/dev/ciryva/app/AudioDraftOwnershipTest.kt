package dev.ciryva.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * No MediaRecorder, API keys, model requests or network use. This exercises the
 * exact private-file ownership handoff used when the upload gate is occupied.
 */
@RunWith(AndroidJUnit4::class)
class AudioDraftOwnershipTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun refusedUploaderReservationRestoresReadyRecording() {
        val session = AudioDraftSession(context) {}
        val file = File.createTempFile("ciryva-handoff-", ".m4a", context.cacheDir)
        file.writeBytes(byteArrayOf(0x01, 0x02))
        try {
            assertTrue(session.restoreRecording(file))
            assertFalse(session.restoreRecording(file))
            // UI can offer the recording to the uploader, but still owns it
            // until the uploader explicitly returns accepted=true.
            val offered = session.takeRecording()
            assertEquals(file, offered)
            assertTrue(session.restoreRecording(offered!!))
            assertEquals(file, session.takeRecording())
            // Simulated refusal while the session is disposed: the Activity,
            // as recipient of the file, now owns cleanup.
            session.close()
            assertTrue(file.isFile)
        } finally {
            session.close()
            file.delete()
        }
    }

    @Test fun discardedUnsavedClipIsActuallyDeleted() {
        val session = AudioDraftSession(context) {}
        val file = File.createTempFile("ciryva-discard-", ".m4a", context.cacheDir)
        file.writeBytes(byteArrayOf(0x01))
        try {
            assertTrue(session.restoreRecording(file))
            session.discard()
            assertFalse(file.exists())
        } finally {
            session.close()
            file.delete()
        }
    }
}
