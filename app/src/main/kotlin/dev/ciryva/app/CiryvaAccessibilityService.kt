package dev.ciryva.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User must enable this service in Android Accessibility settings.
 * No screen observation occurs without a short-lived, exact-package grant.
 */
class CiryvaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || AgentRuntime.pending() == null) return
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
