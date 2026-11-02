package dev.ciryva.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ByokKeyStoreTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun encryptRestoreAndDeleteFakeCredentialWithoutPlaintextOnDisk() {
        val store = AndroidByokKeyStore(ctx)
        store.clear()
        try {
            assertNull(store.load())
            val fake = "sk-test-only-do-not-use-12345"
            store.save(fake)
            assertTrue(store.hasKey())
            val bytes = File(ctx.noBackupFilesDir, "ciryva-openai-key.bin").readBytes()
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(fake))
            assertEquals(fake, AndroidByokKeyStore(ctx).load())
            store.clear()
            assertNull(AndroidByokKeyStore(ctx).load())
            assertFalse(store.hasKey())
        } finally {
            store.clear()
        }
    }
}
