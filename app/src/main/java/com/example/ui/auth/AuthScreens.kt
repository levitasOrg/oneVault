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
fun MasterSetupScreen(
    viewModel: VaultViewModel,
    onSetup: (String, Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Shield logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(72.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "Welcome to OneVault",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Quantum-Safe Symmetric Password Manager Cluster",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF001C38),
                contentColor = Color(0xFFD3E4FF)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "POST-QUANTUM COVERAGE ENHANCED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7ABCFF),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Lattice Kyber KEM derived AES-256 local database locking represents maximum post-quantum immunity.",
                    fontSize = 13.sp,
                    color = Color(0xFFD3E4FF).copy(alpha = 0.9f),
                    textAlign = TextAlign.Start,
                    lineHeight = 18.sp
                )
            }
        }

        var enableBiometrics by remember { mutableStateOf(true) }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Choose a Master Password") },
            placeholder = { Text("At least 5 characters") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Toggle password visibility")
                }
            }
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Master Password") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clickable { enableBiometrics = !enableBiometrics },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Biometric setup",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Enable Biometric Login",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Fingerprint or face lock screen authentication",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(
                checked = enableBiometrics,
                onCheckedChange = { enableBiometrics = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Button(
            onClick = {
                if (password.length >= 5 && password == confirmPassword) {
                    onSetup(password, enableBiometrics)
                }
            },
            enabled = password.length >= 5 && password == confirmPassword,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Setup")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Crypto Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        var showCloudRestore by remember { mutableStateOf(false) }

        TextButton(
            onClick = { showCloudRestore = true }
        ) {
            Icon(Icons.Filled.CloudDownload, contentDescription = "Cloud Restore Direct")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Restore Vault from Cloud Sync", fontWeight = FontWeight.SemiBold)
        }

        if (showCloudRestore) {
            CloudRestoreDialog(
                viewModel = viewModel,
                onDismiss = { showCloudRestore = false }
            )
        }
    }
}

@Composable
fun MasterUnlockScreen(
    viewModel: VaultViewModel,
    onUnlock: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var biometricUnavailable by remember { mutableStateOf(false) }

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Invoke the REAL OS biometric prompt if biometric unlock is enabled.
    //
    // The previous build had a fake "simulated scanner": an animated progress bar that then
    // unlocked using the stored password without any actual authentication. That is a security
    // bypass (anyone holding the phone could trigger it), so it is removed. If the real prompt
    // is unavailable, we fall back to requiring the master password — never an auto-unlock.
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled) {
            launchRealBiometrics(
                activity = activity,
                viewModel = viewModel,
                onSuccess = { masterPassword ->
                    onUnlock(masterPassword)
                },
                onFallbackSimulated = {
                    biometricUnavailable = true
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(72.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "OneVault is Locked",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Post-Quantum Cryptographic Layer is Sealed",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Enter Master Password") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Toggle password")
                }
            }
        )

        Button(
            onClick = {
                if (password.isNotEmpty()) {
                    onUnlock(password)
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Unlock")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (isBiometricEnabled) {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                onClick = {
                    launchRealBiometrics(
                        activity = activity,
                        viewModel = viewModel,
                        onSuccess = { masterPassword -> onUnlock(masterPassword) },
                        onFallbackSimulated = { biometricUnavailable = true }
                    )
                },
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Trigger biometric authentication",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to Quick Unlock",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (biometricUnavailable) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Biometric unlock isn't available right now. Please enter your master password.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        var showCloudRestore by remember { mutableStateOf(false) }

        TextButton(
            onClick = { showCloudRestore = true }
        ) {
            Icon(Icons.Filled.CloudDownload, contentDescription = "Cloud Restore Direct Unlock")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Restore Vault from Cloud Sync", fontWeight = FontWeight.SemiBold)
        }

        if (showCloudRestore) {
            CloudRestoreDialog(
                viewModel = viewModel,
                onDismiss = { showCloudRestore = false }
            )
        }
    }
}

fun launchRealBiometrics(
    activity: FragmentActivity?,
    viewModel: VaultViewModel,
    onSuccess: (String) -> Unit,
    onFallbackSimulated: () -> Unit
) {
    if (activity == null) {
        onFallbackSimulated()
        return
    }
    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
    val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor,
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFallbackSimulated()
            }

            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val pwd = viewModel.getBiometricPassword()
                if (pwd != null) {
                    onSuccess(pwd)
                } else {
                    onFallbackSimulated()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

    val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
        .setTitle("OneVault Vault Unlock")
        .setSubtitle("Confirm local fingerprint or face scan")
        .setNegativeButtonText("Use master key")
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onFallbackSimulated()
    }
}

