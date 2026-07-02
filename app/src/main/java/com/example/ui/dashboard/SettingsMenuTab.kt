package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.autofill.FloatingHoverService
import com.example.crypto.VaultSession
import com.example.data.DecryptedFields
import com.example.data.VaultItem
import com.example.ui.VaultViewModel
import com.example.ui.components.ClipboardDisplayRow
import com.example.ui.components.SensitiveClipboardDisplayRow
import com.example.ui.components.secureCopyToClipboard
import com.example.ui.theme.MyApplicationTheme

@Composable
fun SettingsMenuTab(viewModel: VaultViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val isCloudLoggedIn by viewModel.isCloudLoggedIn.collectAsStateWithLifecycle()
    val cloudEmail by viewModel.cloudEmail.collectAsStateWithLifecycle()
    val cloudProvider by viewModel.cloudProvider.collectAsStateWithLifecycle()
    val cloudUserName by viewModel.cloudUserName.collectAsStateWithLifecycle()

    var showMasterKeyDialog by remember { mutableStateOf(false) }
    var restoreTargetEmail by remember { mutableStateOf("") }
    var activeMenuSubTab by remember { mutableStateOf(0) } // 0 = Profile, 1 = Settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "OneVault Management Console",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Sync secure cloud profile credentials and configure layout overlay widgets.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Sub-Tabs Header Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("👤 Profile" to 0, "⚙️ Settings & Tools" to 1)
            tabs.forEach { (label, idx) ->
                val isSelected = activeMenuSubTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeMenuSubTab = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeMenuSubTab == 0) {
            // Profile & Social cloud connect
            var selectedProviderForDialog by remember { mutableStateOf<String?>(null) }
            var syncFeedbackMessage by remember { mutableStateOf("") }
            var isBackupSuccess by remember { mutableStateOf<Boolean?>(null) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!isCloudLoggedIn) {
                        Text(
                            text = "Connect Secure Social Cloud Profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Link your work, personal, or default email space to enable immediate quantum-wrapped vault back-ups and cloud sync restores.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                            lineHeight = 16.sp
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SocialConnectButton(
                                provider = "Google",
                                color = Color(0xFF4285F4),
                                icon = Icons.Filled.AccountCircle,
                                onClick = { selectedProviderForDialog = "Google" }
                            )
                            SocialConnectButton(
                                provider = "Microsoft",
                                color = Color(0xFFF25022),
                                icon = Icons.Filled.Computer,
                                onClick = { selectedProviderForDialog = "Microsoft" }
                            )
                            SocialConnectButton(
                                provider = "Yahoo",
                                color = Color(0xFF6001D2),
                                icon = Icons.Filled.Mail,
                                onClick = { selectedProviderForDialog = "Yahoo" }
                            )
                        }
                    } else {
                        // Logged in Profile Layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = if (cloudUserName.isNotEmpty()) {
                                    cloudUserName.split(" ")
                                        .take(2)
                                        .map { it.firstOrNull()?.uppercaseChar() ?: "" }
                                        .joinToString("")
                                } else {
                                    "UR"
                                }
                                Text(
                                    text = initials,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cloudUserName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = cloudEmail,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // Provider Badge
                                val badgeColor = when (cloudProvider.lowercase()) {
                                    "google" -> Color(0xFF4285F4)
                                    "microsoft" -> Color(0xFFF25022)
                                    "yahoo" -> Color(0xFF6001D2)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active: $cloudProvider",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Kyber Post-Quantum Backup Engine",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "All database records are client-side quantum-encrypted by Kyber parameters. Transferred backup payload strings remain fully blind to cloud hosts.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                            lineHeight = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val activePwd = VaultSession.getActivePassword()
                                    if (activePwd == null) {
                                        syncFeedbackMessage = "Cannot sync: local vault is locked!"
                                        isBackupSuccess = false
                                    } else {
                                        scope.launch {
                                            viewModel.backupToCloud(cloudEmail)
                                            syncFeedbackMessage = "Encrypted database backup uploaded & verified on cloud!"
                                            isBackupSuccess = true
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = "Sync Cloud Backup")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Backup", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    restoreTargetEmail = cloudEmail
                                    showMasterKeyDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = "Restore database")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore & Sync", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.logoutCloud()
                                syncFeedbackMessage = ""
                                isBackupSuccess = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "Disconnect profile")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect Connected Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (syncFeedbackMessage.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBackupSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                    contentDescription = "Sync Status",
                                    tint = if (isBackupSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = syncFeedbackMessage,
                                    color = if (isBackupSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            val currentProvider = selectedProviderForDialog
            if (currentProvider != null) {
                SocialConnectDialog(
                    provider = currentProvider,
                    onDismiss = { selectedProviderForDialog = null },
                    onConnect = { email, name ->
                        scope.launch {
                            kotlinx.coroutines.delay(1000)
                            viewModel.connectSocialCloud(currentProvider, email, name)
                            selectedProviderForDialog = null
                        }
                    }
                )
            }
        } else {
            // General Settings Card Block
            // 1. Biometrics Setup Screen Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔐 Biometrics setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fingerprint or face lock screen authentication",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Biometric Login", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setBiometricEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Android Native Autofill Card Setup
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚙️ Register Native Android Autofill",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Activate custom post-quantum credential population natively across browsers and native apps on your Android phone:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        lineHeight = 16.sp
                    )

                    Text(
                        text = "1. Click setup below\n2. Select Autofill services\n3. Mark OneVault Password Autofill",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    )

                    Button(
                        onClick = {
                            try {
                                // Build the autofill-service URI from the runtime applicationId
                                // (context.packageName) plus the real, compile-checked class name.
                                // This avoids the earlier fragile mismatch where the package id
                                // (com.aistudio.*) and the source package (com.example.*) were
                                // mixed by hand in a string literal.
                                val component = android.content.ComponentName(
                                    context.packageName,
                                    com.example.autofill.OneVaultAutofillService::class.java.name
                                )
                                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                    data = Uri.parse("package:${component.packageName}/${component.className}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (exc: Exception) {}
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("Open System Autofill Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Experience Theme Card Setup
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🎨 Experience Theme Modes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tailor OneVault's visual contrast scheme between high-security light, ambient night cyber overlay, or standard active context:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf("SYSTEM" to "Default", "LIGHT" to "Light", "DARK" to "Dark")
                        modes.forEach { (mode, label) ->
                            val isSelected = themeMode == mode
                            Button(
                                onClick = { viewModel.setThemeMode(mode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Hover bubble widget trigger card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                val isOverlayEnabled by viewModel.isOverlayEnabled.collectAsStateWithLifecycle()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🛸 Persistent Hover Autofill Helper",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Display a miniature floating widget on your screen. Tap it anytime to immediately access credentials and autofill keys, even if OneVault is in the background:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Floating Bubble", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = isOverlayEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Settings.canDrawOverlays(context)) {
                                        viewModel.setOverlayEnabled(true)
                                        context.startService(Intent(context, FloatingHoverService::class.java))
                                    } else {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            viewModel.setOverlayEnabled(true)
                                            context.startService(Intent(context, FloatingHoverService::class.java))
                                        }
                                    }
                                } else {
                                    viewModel.setOverlayEnabled(false)
                                    context.stopService(Intent(context, FloatingHoverService::class.java))
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }

        if (showMasterKeyDialog) {
            var enteredMasterKey by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }
            var keyVisible by remember { mutableStateOf(false) }
            var isVerifying by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isVerifying) showMasterKeyDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔑 Decrypt & Overwrite Sync")
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "To fetch, decrypt, and import this cloud backup, please provide the Master Password that was used to lock it. This database sync will strictly overwrite any current local passwords.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = enteredMasterKey,
                            onValueChange = {
                                enteredMasterKey = it
                                errorMessage = ""
                            },
                            label = { Text("Backup Master Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle master key visibility"
                                    )
                                }
                              }
                        )

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (isVerifying) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Decrypting Kyber-wrapped payload...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isVerifying && enteredMasterKey.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                isVerifying = true
                                kotlinx.coroutines.delay(1000)
                                viewModel.restoreFromCloud(
                                    email = restoreTargetEmail,
                                    enteredMasterKey = enteredMasterKey,
                                    onSuccess = {
                                        isVerifying = false
                                        showMasterKeyDialog = false
                                        Toast.makeText(context, "Handshake verified! Database restored.", Toast.LENGTH_LONG).show()
                                    },
                                    onError = { error ->
                                        isVerifying = false
                                        errorMessage = error
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Verify & Import")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isVerifying,
                        onClick = { showMasterKeyDialog = false }
                    ) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            )
        }
    }
}

