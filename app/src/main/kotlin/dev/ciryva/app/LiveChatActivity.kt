package dev.ciryva.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.ciryva.core.stream.ResponsesHttpTransport

/**
 * Live Android BYOK entry point. No network at Activity creation; the
 * controller's credential provider is called only after explicit Send.
 * This screen intentionally does not own a durable conversation archive.
 */
class LiveChatActivity : ComponentActivity() {
    private val snapshot = mutableStateOf(LiveChatSnapshot())
    private val keyPresent = mutableStateOf(false)
    private var controller: LiveChatController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(22, 17, 24)),
        )
        val keyStore = AndroidByokKeyStore(applicationContext)
        controller = LiveChatController(
            client = ChatStreamClient { request, key, cancel, onEvent ->
                ResponsesHttpTransport().stream(request, key, cancel, onEvent)
            },
            credentialProvider = { keyStore.load() },
            onUpdate = { value ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) snapshot.value = value
                }
            },
        )
        setContent {
            LiveChatScreen(
                snapshot = snapshot.value,
                hasKey = keyPresent.value,
                onBack = ::finish,
                onKeys = { startActivity(Intent(this, ByokSettingsActivity::class.java)) },
                onSend = { controller?.send(it) ?: false },
                onCancel = { controller?.cancel() ?: false },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        keyPresent.value = AndroidByokKeyStore(applicationContext).hasKey()
    }

    // No invisible chargeable generation after app leaves foreground.
    override fun onStop() {
        controller?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        controller?.close()
        controller = null
        super.onDestroy()
    }
}
