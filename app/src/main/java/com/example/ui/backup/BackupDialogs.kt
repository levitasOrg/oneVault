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
fun SocialConnectButton(
    provider: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = "Connect $provider", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text("Connect with $provider Account", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SocialConnectDialog(
    provider: String,
    onDismiss: () -> Unit,
    onConnect: (email: String, name: String) -> Unit
) {
    // Honest local-profile dialog.
    //
    // The previous implementation loaded the real Google/Microsoft/Yahoo login pages inside a
    // WebView, sniffed session cookies, and then fabricated a "successful OAuth handshake" without
    // ever exchanging a token. That is indistinguishable from credential phishing and is removed.
    //
    // OneVault stores backups locally on-device, so all we actually need is a label (display name
    // + email) for the local backup slot. No remote login, no cookies, no tokens.
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val providerColor = when (provider.lowercase()) {
        "google" -> Color(0xFF4285F4)
        "microsoft" -> Color(0xFFF25022)
        "yahoo" -> Color(0xFF6001D2)
        else -> MaterialTheme.colorScheme.primary
    }

    val emailLooksValid = email.contains("@") && email.contains(".") && email.length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(providerColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (provider.lowercase()) {
                        "google" -> Icons.Filled.AccountCircle
                        "microsoft" -> Icons.Filled.Computer
                        "yahoo" -> Icons.Filled.Mail
                        else -> Icons.Filled.Cloud
                    }
                    Icon(icon, contentDescription = provider, tint = providerColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Set up $provider backup profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This labels a local, on-device backup slot. OneVault does not sign in to $provider or upload anything to a remote server — your encrypted vault stays on this device.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Profile Email (label only)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Local only",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Backups are encrypted with your master password and saved locally. Keep your master password safe — it is the only way to restore them.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && emailLooksValid) {
                        onConnect(email.trim(), name.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = providerColor, contentColor = Color.White),
                enabled = name.isNotBlank() && emailLooksValid
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun CloudRestoreDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCloudLoggedIn by viewModel.isCloudLoggedIn.collectAsStateWithLifecycle()
    val cloudEmail by viewModel.cloudEmail.collectAsStateWithLifecycle()
    val cloudProvider by viewModel.cloudProvider.collectAsStateWithLifecycle()
    val cloudUserName by viewModel.cloudUserName.collectAsStateWithLifecycle()

    var selectedProviderForDialog by remember { mutableStateOf<String?>(null) }
    var showMasterKeyDialog by remember { mutableStateOf(false) }
    var restoreTargetEmail by remember { mutableStateOf("") }
    var enteredMasterKey by remember { mutableStateOf("") }
    var masterKeyErrorMessage by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁️ Cloud Sync Import")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!isCloudLoggedIn) {
                    Text(
                        text = "To fetch, decrypt, and import your quantum-wrapped cloud backup, please log in with your authorized social profile.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
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
                    if (!showMasterKeyDialog) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Logged In",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "CONNECTED VIA $cloudProvider",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = cloudEmail,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            TextButton(
                                onClick = { viewModel.logoutCloud() },
                            ) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Only claim a backup exists if one actually does for this profile.
                        val backupExists = remember(cloudEmail) { viewModel.hasBackupFor(cloudEmail) }

                        Text(
                            text = if (backupExists) {
                                "An encrypted backup was found under $cloudEmail ($cloudUserName). To import it, verify the Master Password used to encrypt it."
                            } else {
                                "No backup was found under $cloudEmail. Create one first from the Profile tab (Sync Backup) while your vault is unlocked."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = {
                                restoreTargetEmail = cloudEmail
                                showMasterKeyDialog = true
                            },
                            enabled = backupExists,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "Restore")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fetch & Decrypt Backup", fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "🔑 Enter Backup Master Password:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = enteredMasterKey,
                            onValueChange = { 
                                enteredMasterKey = it
                                masterKeyErrorMessage = "" 
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

                        if (masterKeyErrorMessage.isNotEmpty()) {
                            Text(
                                text = masterKeyErrorMessage,
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

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                enabled = !isVerifying,
                                onClick = { showMasterKeyDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }

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
                                                onDismiss()
                                                Toast.makeText(context, "Handshake verified! Database restored.", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { error ->
                                                isVerifying = false
                                                masterKeyErrorMessage = error
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text("Verify & Import", fontSize = 11.sp)
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
        },
        confirmButton = {},
        dismissButton = {
            if (!isVerifying) {
                TextButton(onClick = { onDismiss() }) {
                    Text("Close")
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

