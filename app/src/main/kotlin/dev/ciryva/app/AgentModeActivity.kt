package dev.ciryva.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ciryva.core.agent.AgentAuthorizationKernel
import dev.ciryva.core.agent.AgentOperation

/**
 * Initial real-device agent cockpit. Owner explicitly chooses a package and
 * authorizes exactly one observable action; no cloud/model control yet.
 */
class AgentModeActivity : ComponentActivity() {
    private var accessibilityEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentModeScreen(
                accessGranted = accessibilityEnabled,
                onAccessSettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onLaunch = ::launchOneShot,
                onTorch = ::setTorch,
                onStopAll = AgentStopSwitch::stopAll,
                onClose = ::finish,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        accessibilityEnabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { info ->
            info.resolveInfo.serviceInfo.packageName == packageName &&
                info.resolveInfo.serviceInfo.name == CiryvaAccessibilityService::class.java.name
        }
    }

    private fun launchOneShot(packageName: String, operation: AgentOperation, label: String?) {
        if (!accessibilityEnabled) {
            AgentRuntime.note("Enable the CiRYVA accessibility service before running a UI action.")
            return
        }
        val target = packageName.trim()
        if (!AgentAuthorizationKernel.isPackageValid(target)) {
            AgentRuntime.note("Enter an exact installed Android package name.")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(target)
        if (intent == null) {
            AgentRuntime.note("No launchable installed app found for " + target + ".")
            return
        }
        if (!AgentRuntime.arm(target, operation, label)) return
        try {
            startActivity(intent)
        } catch (_: RuntimeException) {
            AgentRuntime.stop()
            AgentRuntime.note("Android refused to open " + target + ".")
        }
    }

    private fun setTorch(enabled: Boolean) {
        if (checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) {
            AgentRuntime.note("Camera permission required for flashlight control.")
            return
        }
        val camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val candidate = try {
            camera.cameraIdList.firstOrNull { id ->
                camera.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
        if (candidate == null) {
            AgentRuntime.note("No available torch-capable camera was detected.")
            return
        }
        try {
            camera.setTorchMode(candidate, enabled)
            AgentRuntime.note(
                "Android accepted torch " + if (enabled) "ON request." else "OFF request."
            )
        } catch (_: Exception) {
            AgentRuntime.note("The camera could not change torch state.")
        }
    }
}

@Composable
private fun AgentModeScreen(
    accessGranted: Boolean,
    onAccessSettings: () -> Unit,
    onLaunch: (String, AgentOperation, String?) -> Unit,
    onTorch: (Boolean) -> Unit,
    onStopAll: () -> Unit,
    onClose: () -> Unit,
) {
    val snapshot by AgentRuntime.state
    var target by rememberSaveable { mutableStateOf("") }
    var exactLabel by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Retain the originating emergency-stop generation alongside the request.
    // A delayed Android permission result can never resurrect work after STOP.
    var pendingTorch by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val original = pendingTorch
        pendingTorch = null
        when {
            original == null -> Unit
            !AgentStopSwitch.isCurrent(original.second) ->
                AgentRuntime.note("Torch action cancelled by STOP ALL.")
            granted -> onTorch(original.first)
            else -> AgentRuntime.note("Camera permission denied; flashlight unchanged.")
        }
    }
    fun torch(desired: Boolean) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) onTorch(desired)
        else {
            pendingTorch = desired to AgentStopSwitch.generation()
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = CiRYVAVisual.ink,
            onBackground = CiRYVAVisual.ivory,
            surface = CiRYVAVisual.raised,
            primary = CiRYVAVisual.rose,
        ),
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 25.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CiRYVA · Agent Mode", style = MaterialTheme.typography.headlineSmall)
            Text("Native device controls — A0/A1. Observe and one approved tap; no LLM-driven autonomous jobs yet.")
            Button(
                onClick = onStopAll,
                modifier = Modifier.fillMaxWidth().testTag("agent-emergency-stop"),
            ) { Text("STOP ALL — cancel agent and AI stream") }
            Text("For a stop switch from other apps, add the CiRYVA STOP ALL tile to Quick Settings.")
            Text(
                if (accessGranted) "Accessibility: enabled"
                else "Accessibility: not enabled (must be granted in Android Settings)",
                modifier = Modifier.testTag("agent-accessibility-state"),
            )
            OutlinedButton(
                onClick = onAccessSettings,
                modifier = Modifier.testTag("agent-open-access-settings"),
            ) { Text("Open Accessibility settings") }
            OutlinedTextField(
                value = target,
                onValueChange = { if (it.length <= 150) target = it },
                label = { Text("Exact Android package, e.g. com.android.settings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("agent-target-package"),
            )
            OutlinedButton(
                onClick = { onLaunch(target, AgentOperation.OBSERVE_WINDOW, null) },
                enabled = accessGranted,
                modifier = Modifier.fillMaxWidth().testTag("agent-observe-launch"),
            ) { Text("Observe this app once and open it") }
            OutlinedTextField(
                value = exactLabel,
                onValueChange = { if (it.length <= 80) exactLabel = it },
                label = { Text("Exact visible label for a single tap") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("agent-click-label"),
            )
            OutlinedButton(
                onClick = { onLaunch(target, AgentOperation.TAP_EXACT_LABEL, exactLabel) },
                enabled = accessGranted && exactLabel.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("agent-tap-launch"),
            ) { Text("Authorize one tap and open app") }
            Text("Only harmless navigation labels are allowed in this milestone. Posting, deletion, payment, permission changes and other consequential actions are blocked.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { torch(true) },
                    modifier = Modifier.testTag("agent-torch-on")) {
                    Text("Torch ON")
                }
                OutlinedButton(onClick = { torch(false) },
                    modifier = Modifier.testTag("agent-torch-off")) {
                    Text("Torch OFF")
                }
            }
            Text("Last result: " + snapshot.status,
                modifier = Modifier.testTag("agent-current-status"))
            if (snapshot.labels.isNotEmpty()) {
                Text("Observed labels (local only):")
                snapshot.labels.forEach { Text("• " + it) }
            }
            if (snapshot.events.isNotEmpty()) {
                Text("Execution receipts")
                snapshot.events.takeLast(8).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onClose) { Text("Return to CiRYVA") }
        }
    }
}
