package dev.ciryva.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * All available attachment/intake actions under ONE left-side paperclip.
 * Do not show unavailable tools; selecting a file never silently sends.
 */
@Composable
internal fun ComposerAttachmentMenu(
    enabled: Boolean,
    mediaEnabled: Boolean,
    ocrEnabled: Boolean,
    voiceNoteEnabled: Boolean,
    onChooseMedia: () -> Unit,
    onPickOcr: () -> Unit,
    onPickVoiceNote: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled && (mediaEnabled || ocrEnabled || voiceNoteEnabled),
            modifier = Modifier.size(52.dp).testTag("p06-media-picker"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_composer_attach),
                tint = CiRYVAVisual.gold,
                contentDescription = "Attach photos, documents, OCR or voice notes",
                modifier = Modifier.size(25.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("composer-attachment-menu"),
        ) {
            if (mediaEnabled) DropdownMenuItem(
                text = { Text("Photos and PDF documents") },
                onClick = {
                    expanded = false
                    onChooseMedia()
                },
                modifier = Modifier.testTag("composer-attach-media"),
            )
            if (ocrEnabled) DropdownMenuItem(
                text = { Text("Extract text from image · offline OCR") },
                onClick = {
                    expanded = false
                    onPickOcr()
                },
                modifier = Modifier.testTag("composer-attach-ocr"),
            )
            if (voiceNoteEnabled) DropdownMenuItem(
                text = { Text("Import voice note for transcription") },
                onClick = {
                    expanded = false
                    onPickVoiceNote()
                },
                modifier = Modifier.testTag("composer-attach-audio"),
            )
        }
    }
}
