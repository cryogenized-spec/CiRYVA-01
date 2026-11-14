package dev.ciryva.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

/**
 * User must enable this service in Android Accessibility settings.
 * No screen observation occurs without a short-lived, exact-package grant.
 */
class CiryvaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AgentRuntime.needsWindowEvent()) return
        // No agent interaction through a locked or sleeping display.
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive || keyguard.isDeviceLocked) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                AgentRuntime.onAccessibilityWindow(this)
        }
    }
    override fun onInterrupt() {
        // The OS can suspend accessibility. No pending interaction is assumed successful.
        AgentRuntime.stop()
    }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AgentRuntime.stop()
        return super.onUnbind(intent)
    }
}
