package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.totp.Totp
import kotlinx.coroutines.delay

/**
 * Live two-factor (TOTP) row: the current code refreshed every second, a copy button, and a
 * circular ring that drains as the 30-second window elapses (turning warm near expiry). The
 * secret never leaves this screen — only the derived code is shown or copied.
 */
@Composable
fun TotpCodeRow(base32Secret: String, periodSeconds: Int = 30) {
    if (!Totp.isValidSecret(base32Secret)) return
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(base32Secret) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val code = Totp.currentCode(base32Secret, atMillis = now, periodSeconds = periodSeconds)
    val remaining = Totp.secondsRemaining(atMillis = now, periodSeconds = periodSeconds)
    // Split "287082" -> "287 082" for readability; leave 8-digit codes as one group of 4+4.
    val pretty = when (code.length) {
        6 -> code.substring(0, 3) + " " + code.substring(3)
        8 -> code.substring(0, 4) + " " + code.substring(4)
        else -> code
    }

    val fraction by animateFloatAsState(
        targetValue = remaining.toFloat() / periodSeconds.toFloat(),
        animationSpec = tween(durationMillis = 900),
        label = "totpRing",
    )
    val ringColor =
        if (remaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "Two-Factor Code (TOTP)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pretty,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.size(28.dp)) {
                    val stroke = 3.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = -360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(text = "$remaining", fontSize = 9.sp, color = ringColor)
            }
            IconButton(
                onClick = { secureCopyToClipboard(context, "TOTP code", code, sensitive = true) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
