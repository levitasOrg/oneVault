package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Copies [value] to the system clipboard. Sensitive values are flagged so Android 13+ keeps them
 * out of the clipboard preview, and are automatically cleared after CLIPBOARD_CLEAR_DELAY_MS so a
 * copied password/CVV/PIN does not linger for other apps to read.
 */
fun secureCopyToClipboard(context: Context, label: String, value: String, sensitive: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText(label, value)
    if (sensitive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)

    if (sensitive) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Only clear if our value is still the current clip, so we don't wipe something the
            // user copied afterwards.
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == value) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                }
            }
        }, CLIPBOARD_CLEAR_DELAY_MS)
    }
}

private const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L

@Composable
fun ClipboardDisplayRow(label: String, value: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    if (value.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = value, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { secureCopyToClipboard(context, label, value, sensitive = false) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SensitiveClipboardDisplayRow(label: String, value: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    if (value.isEmpty()) return
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (visible) value else "••••••••••••",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row {
                IconButton(
                    onClick = { visible = !visible },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Show/Hide",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { secureCopyToClipboard(context, label, value, sensitive = true) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy text",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
