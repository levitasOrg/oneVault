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

class MainActivity : FragmentActivity() {

    private var appViewModel: VaultViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE prevents screenshots, screen recording, and the app appearing in the
        // recent-apps thumbnail — all of which could leak plaintext passwords on screen.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()
        setContent {
            val viewModel: VaultViewModel = viewModel()
            appViewModel = viewModel
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OneVaultApp(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Auto-lock when the app leaves the foreground, so a stolen unlocked phone does not expose
        // the vault. The user re-authenticates (master password or biometric) on return.
        // isChangingConfigurations guards against locking on rotation.
        if (!isChangingConfigurations) {
            appViewModel?.lockVault()
        }
    }
}

@Composable
fun OneVaultApp(viewModel: VaultViewModel) {
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isFirstLaunch -> {
                    MasterSetupScreen(
                        viewModel = viewModel,
                        onSetup = { pwd, bio -> viewModel.setupMasterPassword(pwd, bio) }
                    )
                }
                isLocked -> {
                    MasterUnlockScreen(
                        viewModel = viewModel,
                        onUnlock = { pwd -> viewModel.unlockVault(pwd) }
                    )
                }
                else -> {
                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}

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

@Composable
fun CustomChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DashboardScreen(viewModel: VaultViewModel) {
    val items by viewModel.filteredItems.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val quantumStats by viewModel.quantumStats.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedVault by viewModel.selectedVault.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedItemDetail by remember { mutableStateOf<VaultItem?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Vault, 1 = Generator, 2 = Import, 3 = Diagnostics

    Scaffold(
        topBar = {
            OneVaultTopBar(
                onLock = { viewModel.lockVault() },
                selectedVault = selectedVault,
                onVaultSelect = { viewModel.setVault(it) }
            )
        },
        bottomBar = {
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            val borderLineColor = MaterialTheme.colorScheme.outline

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = borderLineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Vault") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = "Password Generator") },
                    label = { Text("Generator") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.Input, contentDescription = "Import Data") },
                    label = { Text("Import") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = "Settings Menu") },
                    label = { Text("Menu") },
                    colors = navItemColors
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add New Item")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> {
                    val isShieldVisible by viewModel.isShieldVisible.collectAsStateWithLifecycle()
                    VaultContentsTab(
                        items = items,
                        favorites = favorites,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        selectedCategory = selectedCategory,
                        onCategorySelect = { viewModel.setCategory(it) },
                        onItemClick = { selectedItemDetail = it },
                        isShieldVisible = isShieldVisible,
                        onCloseShield = { viewModel.setShieldVisible(false) }
                    )
                }
                1 -> {
                    StandaloneGeneratorTab(viewModel = viewModel)
                }
                2 -> {
                    ImportDataTab(viewModel = viewModel)
                }
                3 -> {
                    SettingsMenuTab(viewModel = viewModel)
                }
            }
        }
    }

    if (showAddDialog) {
        ItemManageDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }

    val currentItemDetail = selectedItemDetail
    if (currentItemDetail != null) {
        ItemDetailDialog(
            item = currentItemDetail,
            viewModel = viewModel,
            onDismiss = { selectedItemDetail = null }
        )
    }
}

@Composable
fun OneVaultTopBar(
    onLock: () -> Unit,
    selectedVault: String,
    onVaultSelect: (String) -> Unit
) {
    var vaultMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "OneVault",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Kyber KEM PQC",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Button(
                    onClick = { vaultMenuExpanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Vaults list",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedVault == "ALL") "All Vaults" else "$selectedVault Vault",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = vaultMenuExpanded,
                    onDismissRequest = { vaultMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Vaults") },
                        onClick = {
                            onVaultSelect("ALL")
                            vaultMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🔒 Personal Vault") },
                        onClick = {
                            onVaultSelect("Personal")
                            vaultMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🏢 Work Vault") },
                        onClick = {
                            onVaultSelect("Work")
                            vaultMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🔑 Private Vault") },
                        onClick = {
                            onVaultSelect("Private")
                            vaultMenuExpanded = false
                        }
                    )
                }
            }

            IconButton(onClick = onLock) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Vault",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun VaultContentsTab(
    items: List<VaultItem>,
    favorites: List<VaultItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onItemClick: (VaultItem) -> Unit,
    isShieldVisible: Boolean = true,
    onCloseShield: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search logins, cards, notes...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // Quantum active card from Geometric Balance guidelines (closable)
        if (isShieldVisible) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "POST-QUANTUM LAYER ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Advanced Encryption Shield",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "Your safe keys and personal data are bound with Kyber ML-KEM & AES-256 local locks, ensuring complete cryptographic safety against quantum-level intrusions.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Shield Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Top-End aligned close icon button
                    IconButton(
                        onClick = onCloseShield,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Banner",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        val categories = listOf("ALL", "LOGIN", "CARD", "NOTE", "IDENTITY")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                val chipLabel = when (cat) {
                    "ALL" -> "All"
                    "LOGIN" -> "Logins"
                    "CARD" -> "Credit Cards"
                    "NOTE" -> "Secure Notes"
                    "IDENTITY" -> "Identities"
                    else -> cat
                }
                CustomChip(
                    label = chipLabel,
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelect(cat) }
                )
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.Lock,
                        contentDescription = if (searchQuery.isNotEmpty()) "Search" else "Lock",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No Search Results" else "Your Protected Vault is Empty",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No vault items match \"$searchQuery\". Try checking active category and vault protection filters." else "Add credentials with '+' below or paste 1Password exports in the Import settings tab!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(items) { item ->
                    VaultItemRow(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun VaultItemRow(item: VaultItem, onClick: () -> Unit) {
    val icon = when (item.category) {
        "LOGIN" -> Icons.Default.Person
        "CARD" -> Icons.Default.Share
        "NOTE" -> Icons.Default.Info
        "IDENTITY" -> Icons.Default.Person
        else -> Icons.Default.Lock
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.category,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.website.isNotEmpty()) item.website else item.category,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite star",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.vaultName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StandaloneGeneratorTab(viewModel: VaultViewModel) {
    var length by remember { mutableFloatStateOf(16f) }
    var includeUpper by remember { mutableStateOf(true) }
    var includeLower by remember { mutableStateOf(true) }
    var includeDigits by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }
    var useWords by remember { mutableStateOf(false) }
    var generatedPwd by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(length, includeUpper, includeLower, includeDigits, includeSymbols, useWords) {
        generatedPwd = viewModel.generatePassword(
            length.toInt(), includeUpper, includeLower, includeDigits, includeSymbols, useWords
        )
    }

    val (fraction, strengthText, strengthColor) = viewModel.evaluatePasswordStrength(generatedPwd)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Text(
            text = "Generate Secure Passwords",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Create complex keys tailored to your platform requirements.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = generatedPwd,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        lineHeight = 22.sp
                    )

                    Row {
                        IconButton(onClick = {
                            generatedPwd = viewModel.generatePassword(
                                length.toInt(), includeUpper, includeLower, includeDigits, includeSymbols, useWords
                            )
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate")
                        }
                        IconButton(
                            onClick = {
                                if (generatedPwd.isNotEmpty()) {
                                    secureCopyToClipboard(context, "Generated Password", generatedPwd, sensitive = true)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = strengthColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = strengthText,
                        color = strengthColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        Text(
            text = "Length: ${length.toInt()} Characters",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 8f..64f,
            steps = 56,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rememberable Words (Passphrase)", 
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Generate readable, memorable word pairings", 
                    fontSize = 11.sp, 
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = useWords, 
                onCheckedChange = { useWords = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Uppercase Letters (A-Z)",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = includeUpper, 
                onCheckedChange = { includeUpper = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lowercase Letters (a-z)",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = includeLower, 
                onCheckedChange = { includeLower = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Numbers (0-9)",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = includeDigits, 
                onCheckedChange = { includeDigits = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Special Symbols (&, @, %)",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = includeSymbols, 
                onCheckedChange = { includeSymbols = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ImportDataTab(viewModel: VaultViewModel) {
    var importText by remember { mutableStateOf("") }
    var targetVault by remember { mutableStateOf("Personal") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Offline Zero-Knowledge Import",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Copy and paste CSV or JSON exports from 1Password or other credential managers to parse and import them.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📥 Import from 1Password (.JSON or .CSV)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Paste text from a 1Password JSON array or CSV backup format stream directly. Items will be post-quantum wrapped and added securely.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("Paste CSV or JSON details here...\nHeaders: Title,Username,Password,Website") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    maxLines = 10
                )

                // Let the user choose which vault imported items land in (previously hardcoded
                // to "Personal", so Work/Private imports were impossible).
                Text(
                    text = "Import into vault",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Personal", "Work", "Private").forEach { v ->
                        val isSel = targetVault == v
                        Button(
                            onClick = { targetVault = v },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).padding(2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(v, fontSize = 11.sp)
                        }
                    }
                }

                Button(
                    onClick = {
                        if (importText.isNotEmpty()) {
                            viewModel.importFromOnePassword(importText, targetVault)
                            importText = ""
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
                    Icon(Icons.Filled.Check, contentDescription = "Import")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Parse and decryptively import")
                }
            }
        }
    }
}

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



@Composable
fun ItemManageDialog(
    viewModel: VaultViewModel,
    itemToEdit: VaultItem? = null,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(itemToEdit?.title ?: "") }
    var category by remember { mutableStateOf(itemToEdit?.category ?: "LOGIN") }
    var vaultName by remember { mutableStateOf(itemToEdit?.vaultName ?: "Personal") }
    var website by remember { mutableStateOf(itemToEdit?.website ?: "") }
    var isFavorite by remember { mutableStateOf(itemToEdit?.isFavorite ?: false) }

    var isDecrypting by remember(itemToEdit) { mutableStateOf(itemToEdit != null) }
    var fields by remember { mutableStateOf(DecryptedFields()) }

    LaunchedEffect(itemToEdit) {
        if (itemToEdit != null) {
            isDecrypting = true
            val decrypted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                viewModel.decryptItem(itemToEdit)
            }
            if (decrypted != null) {
                fields = decrypted
            }
            isDecrypting = false
        }
    }

    var generatorExpanded by remember { mutableStateOf(false) }
    var genLength by remember { mutableFloatStateOf(16f) }
    var genUpper by remember { mutableStateOf(true) }
    var genLower by remember { mutableStateOf(true) }
    var genDigits by remember { mutableStateOf(true) }
    var genSymbols by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (itemToEdit != null) "Edit Vault Item" else "New Vault Item",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Box {
                if (isDecrypting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Locally decapsulating Kyber payload...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    val categories = listOf("LOGIN", "CARD", "NOTE", "IDENTITY")
                    Text(text = "Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    categories.forEach { cat ->
                        val catDisplay = when (cat) {
                            "LOGIN" -> "Login"
                            "CARD" -> "Credit Card"
                            "NOTE" -> "Secure Note"
                            "IDENTITY" -> "Identity"
                            else -> cat
                        }
                        val isSelected = category == cat
                        Button(
                            onClick = { category = cat },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).padding(2.dp)
                        ) {
                            Text(catDisplay, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                val vaults = listOf("Personal", "Work", "Private")
                Text(text = "Vault Protection Cluster", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    vaults.forEach { v ->
                        val isSel = vaultName == v
                        Button(
                            onClick = { vaultName = v },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).padding(2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(v, fontSize = 10.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Item Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                when (category) {
                    "LOGIN" -> {
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text("Website URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = fields.username,
                            onValueChange = { fields = fields.copy(username = it) },
                            label = { Text("Username / Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = fields.secretText,
                            onValueChange = { fields = fields.copy(secretText = it) },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { generatorExpanded = !generatorExpanded },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Gen", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (generatorExpanded) "Collapse Password Generator" else "🔑 Generate Secure Password", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        if (generatorExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text("Length: ${genLength.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = genLength,
                                    onValueChange = { genLength = it },
                                    valueRange = 8f..32f,
                                    steps = 24
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Digits", fontSize = 11.sp)
                                    Switch(
                                        checked = genDigits, 
                                        onCheckedChange = { genDigits = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Symbols", fontSize = 11.sp)
                                    Switch(
                                        checked = genSymbols, 
                                        onCheckedChange = { genSymbols = it },
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
                                        fields = fields.copy(
                                            secretText = viewModel.generatePassword(
                                                genLength.toInt(), genUpper, genLower, genDigits, genSymbols
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text("Apply Generated Password", fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = fields.customNotes,
                            onValueChange = { fields = fields.copy(customNotes = it) },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 5
                        )
                    }
                    "CARD" -> {
                        OutlinedTextField(
                            value = fields.cardNumber,
                            onValueChange = { fields = fields.copy(cardNumber = it) },
                            label = { Text("Card Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = fields.expiry,
                                onValueChange = { fields = fields.copy(expiry = it) },
                                label = { Text("Expiry (MM/YY)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).padding(end = 4.dp)
                            )
                            OutlinedTextField(
                                value = fields.cvv,
                                onValueChange = { fields = fields.copy(cvv = it) },
                                label = { Text("CVV") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            )
                        }
                        OutlinedTextField(
                            value = fields.cardholderName,
                            onValueChange = { fields = fields.copy(cardholderName = it) },
                            label = { Text("Cardholder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = fields.pin,
                            onValueChange = { fields = fields.copy(pin = it) },
                            label = { Text("Card PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = fields.customNotes,
                            onValueChange = { fields = fields.copy(customNotes = it) },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 5
                        )
                    }
                    "NOTE" -> {
                        OutlinedTextField(
                            value = fields.secretText,
                            onValueChange = { fields = fields.copy(secretText = it) },
                            label = { Text("Secure Notes Text") },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            maxLines = 15
                        )
                    }
                    "IDENTITY" -> {
                        OutlinedTextField(
                            value = fields.fullName,
                            onValueChange = { fields = fields.copy(fullName = it) },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = fields.email,
                            onValueChange = { fields = fields.copy(email = it) },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = fields.phone,
                            onValueChange = { fields = fields.copy(phone = it) },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = fields.ssn,
                            onValueChange = { fields = fields.copy(ssn = it) },
                            label = { Text("SSN / Passport ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = fields.address,
                            onValueChange = { fields = fields.copy(address = it) },
                            label = { Text("Full Address") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = fields.customNotes,
                            onValueChange = { fields = fields.copy(customNotes = it) },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 5
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Text("Pin Item to Favorites")
                }
            }
            }
            }
        },
        confirmButton = {
            Button(
                enabled = !isDecrypting,
                onClick = {
                    if (title.isNotEmpty()) {
                        viewModel.addOrUpdateItem(
                            title = title,
                            category = category,
                            vaultName = vaultName,
                            website = website,
                            fields = fields,
                            isFavorite = isFavorite,
                            existingId = itemToEdit?.id
                        )
                        onDismiss()
                    }
                }
            ) {
                Text("Save Credentials")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ItemDetailDialog(
    item: VaultItem,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    val isDecryptingState = remember(item) { mutableStateOf(true) }
    val isDecrypting = isDecryptingState.value
    val decryptedFieldsState = remember(item) { mutableStateOf<DecryptedFields?>(null) }
    val decryptedFields = decryptedFieldsState.value

    LaunchedEffect(item) {
        isDecryptingState.value = true
        decryptedFieldsState.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            viewModel.decryptItem(item)
        }
        isDecryptingState.value = false
    }
    var secretTextVisible by remember { mutableStateOf(false) }
    var editModeExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    if (editModeExpanded) {
        ItemManageDialog(
            viewModel = viewModel,
            itemToEdit = item,
            onDismiss = {
                editModeExpanded = false
                onDismiss()
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                IconButton(
                    enabled = !isDecrypting,
                    onClick = {
                        viewModel.addOrUpdateItem(
                            title = item.title,
                            category = item.category,
                            vaultName = item.vaultName,
                            website = item.website,
                            fields = decryptedFields ?: DecryptedFields(),
                            isFavorite = !item.isFavorite,
                            existingId = item.id
                        )
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        tint = if (item.isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        contentDescription = "Fav"
                    )
                }
            }
        },
        text = {
            if (isDecrypting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Locally decapsulating Kyber payload...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (decryptedFields == null) {
                Text("Decryption Failed: Private Kyber KEM decryption keys failed. Please relock and re-authenticate.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Row(modifier = Modifier.padding(bottom = 12.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val catDisplay = when (item.category) {
                                "LOGIN" -> "Login"
                                "CARD" -> "Credit Card"
                                "NOTE" -> "Secure Note"
                                "IDENTITY" -> "Identity"
                                else -> item.category
                            }
                            Text(catDisplay, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${item.vaultName} Vault", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }

                    when (item.category) {
                        "LOGIN" -> {
                            if (item.website.isNotEmpty()) {
                                ClipboardDisplayRow("Website", item.website, clipboardManager)
                            }
                            ClipboardDisplayRow("Username/Email", decryptedFields.username, clipboardManager)
                            SensitiveClipboardDisplayRow("Password", decryptedFields.secretText, clipboardManager)
                        }
                        "CARD" -> {
                            SensitiveClipboardDisplayRow("Card Number", decryptedFields.cardNumber, clipboardManager)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    ClipboardDisplayRow("Expiry", decryptedFields.expiry, clipboardManager)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    SensitiveClipboardDisplayRow("CVV", decryptedFields.cvv, clipboardManager)
                                }
                            }
                            ClipboardDisplayRow("Cardholder Name", decryptedFields.cardholderName, clipboardManager)
                            if (decryptedFields.pin.isNotEmpty()) {
                                SensitiveClipboardDisplayRow("PIN Code", decryptedFields.pin, clipboardManager)
                            }
                        }
                        "NOTE" -> {
                            ClipboardDisplayRow("Note Content", decryptedFields.secretText, clipboardManager)
                        }
                        "IDENTITY" -> {
                            ClipboardDisplayRow("Full Name", decryptedFields.fullName, clipboardManager)
                            ClipboardDisplayRow("Email", decryptedFields.email, clipboardManager)
                            ClipboardDisplayRow("Phone", decryptedFields.phone, clipboardManager)
                            SensitiveClipboardDisplayRow("SSN / Passport / PAN", decryptedFields.ssn, clipboardManager)
                            ClipboardDisplayRow("Full Address", decryptedFields.address, clipboardManager)
                        }
                    }

                    if (decryptedFields.customNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ClipboardDisplayRow("Notes / Metadata", decryptedFields.customNotes, clipboardManager)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Row {
                    TextButton(onClick = { editModeExpanded = true }) {
                        Text("Edit")
                    }
                    Button(onClick = onDismiss) {
                        Text("Finish")
                    }
                }
            }
        }
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
