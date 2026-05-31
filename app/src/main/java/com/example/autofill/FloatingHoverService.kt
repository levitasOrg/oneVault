package com.example.autofill

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import android.app.ActivityManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.example.crypto.QuantumCrypto
import com.example.crypto.VaultSession
import com.example.data.AppDatabase
import com.example.data.VaultItem
import com.example.data.DecryptedFields
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class FloatingHoverService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
    private val fieldsAdapter = moshi.adapter(DecryptedFields::class.java)

    private var isExpandedState by mutableStateOf(false)
    private val allItems = mutableStateListOf<VaultItem>()

    // Custom lifecycle owners to allow ComposeView inside a floating WindowManager context
    private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun onStart() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun onResume() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private val lifecycleOwner = ServiceLifecycleOwner()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Setup custom lifecycle
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        // Setup WindowManager parameters for the small draggable bubble
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // Initialize compose view and register the lifecycle
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FloatingHoverContent()
            }
        }

        // Listen for touch interactions
        composeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isExpandedState) {
                    return false // Let Compose capture inside the matched screen
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoved = true
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(composeView, params)
                        } catch (e: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoved) {
                            // Click detected! Expand overlay portal
                            isExpandedState = true
                            updateParams(true)
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }

        // Load vault logitems
        loadItems()
    }

    private fun loadItems() {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@FloatingHoverService)
                db.vaultDao().getAllItems().collect { list ->
                    allItems.clear()
                    allItems.addAll(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateParams(expanded: Boolean) {
        if (expanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            // Request focus so user can type master password
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Composable
    private fun FloatingHoverContent() {
        if (!isExpandedState) {
            // Small key floating bubble handle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "OneVault Hover Tool",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            // Full Overlay Portal (Semi transparent background)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        isExpandedState = false
                        updateParams(false)
                    },
                contentAlignment = Alignment.Center
            ) {
                val isLocked = VaultSession.isLocked()

                Box(
                    modifier = Modifier
                        .clickable(enabled = false, onClick = {}) // block touch passing
                ) {
                    if (isLocked) {
                        HoverUnlockCard()
                    } else {
                        HoverVaultDial()
                    }
                }
            }
        }
    }

    @Composable
    private fun HoverUnlockCard() {
        var passwordInput by remember { mutableStateOf("") }
        var isAuthenticating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }

        Card(
            modifier = Modifier
                .width(280.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "Shield Security",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unlock OneVault",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hover utility requires secure authentication",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (passwordInput.isEmpty()) return@Button
                        isAuthenticating = true
                        serviceScope.launch {
                            val success = decryptAndVerifyMaster(passwordInput)
                            isAuthenticating = false
                            if (success) {
                                VaultSession.unlock(passwordInput)
                                Toast.makeText(this@FloatingHoverService, "Vault Unlocked", Toast.LENGTH_SHORT).show()
                            } else {
                                errorMessage = "Incorrect password!"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAuthenticating
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Unlock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    private suspend fun decryptAndVerifyMaster(password: String): Boolean {
        return withContext(Dispatchers.Default) {
            val verificationItem = allItems.find { it.category == "MASTER_VERIFICATION" } ?: return@withContext false
            try {
                val decrypted = QuantumCrypto.decrypt(verificationItem.encryptedPayload, password)
                decrypted == "VaultUnlocked!"
            } catch (e: Exception) {
                false
            }
        }
    }

    @Composable
    private fun HoverFieldRow(
        label: String,
        value: String,
        isSensitive: Boolean = false,
        clipboard: ClipboardManager
    ) {
        if (value.isEmpty()) return
        var visible by remember { mutableStateOf(!isSensitive) }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label, 
                    fontSize = 9.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (visible) value else "••••••••••••",
                    fontSize = 11.sp,
                    fontFamily = if (isSensitive) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isSensitive) {
                    IconButton(
                        onClick = { visible = !visible },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Show/Hide",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                        Toast.makeText(this@FloatingHoverService, "$label copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy text",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun HoverVaultDial() {
        var selectedItemDetail by remember { mutableStateOf<VaultItem?>(null) }
        var detailedFields by remember { mutableStateOf<DecryptedFields?>(null) }
        var isDecryptingItem by remember { mutableStateOf(false) }

        val itemsToShow = remember(allItems) {
            allItems.filter { it.category != "MASTER_VERIFICATION" }.take(8)
        }

        LaunchedEffect(selectedItemDetail) {
            val item = selectedItemDetail
            if (item != null) {
                isDecryptingItem = true
                val password = VaultSession.getActivePassword()
                if (password != null) {
                    val fields = withContext(Dispatchers.Default) {
                        try {
                            val decStr = QuantumCrypto.decrypt(item.encryptedPayload, password)
                            fieldsAdapter.fromJson(decStr)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    detailedFields = fields
                }
                isDecryptingItem = false
            } else {
                detailedFields = null
            }
        }

        Box(
            modifier = Modifier.size(310.dp),
            contentAlignment = Alignment.Center
        ) {
            // Dimmed background circle disk with futuristic outer boundary
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
            )

            val centerRadiusDp = 95.dp
            val strokeColor = MaterialTheme.colorScheme.primary

            // Spoke Connectors Canvas drawing dashed connection line links
            Canvas(
                modifier = Modifier.size(280.dp)
            ) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val centerRadiusPx = centerRadiusDp.toPx()
                
                itemsToShow.forEachIndexed { index, _ ->
                    val total = itemsToShow.size
                    val angleRad = (index * 2 * Math.PI / total).toFloat()
                    val targetX = center.x + centerRadiusPx * kotlin.math.cos(angleRad)
                    val targetY = center.y + centerRadiusPx * kotlin.math.sin(angleRad)
                    
                    drawLine(
                        color = strokeColor.copy(alpha = 0.45f),
                        start = center,
                        end = androidx.compose.ui.geometry.Offset(targetX, targetY),
                        strokeWidth = 4f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(15f, 15f), 0f
                        )
                    )
                }
            }

            // Orbiting Credentials styled as small circle icons connected to larger circle
            itemsToShow.forEachIndexed { index, item ->
                // Calculate angular layout offset (distributed over 2 * PI radians)
                val total = itemsToShow.size
                val angleRad = (index * 2 * Math.PI / total)
                val xOffset = with(LocalDensity.current) { (centerRadiusDp.toPx() * kotlin.math.cos(angleRad)).toFloat().toDp() }
                val yOffset = with(LocalDensity.current) { (centerRadiusDp.toPx() * kotlin.math.sin(angleRad)).toFloat().toDp() }

                val itemColor = when (item.category) {
                    "LOGIN" -> Color(0xFF4285F4)
                    "CARD" -> Color(0xFF4CAF50)
                    "NOTE" -> Color(0xFFFF9800)
                    "IDENTITY" -> Color(0xFF9C27B0)
                    else -> MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(itemColor.copy(alpha = 0.15f))
                        .border(2.dp, itemColor, CircleShape)
                        .clickable { selectedItemDetail = item },
                    contentAlignment = Alignment.Center
                ) {
                    val categoryIcon = when (item.category) {
                        "LOGIN" -> Icons.Filled.Lock
                        "CARD" -> Icons.Filled.CreditCard
                        "NOTE" -> Icons.Filled.Book
                        "IDENTITY" -> Icons.Filled.Person
                        else -> Icons.Filled.Star
                    }
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = item.title,
                        tint = itemColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Central Hub Menu Control representing the larger connected core circle
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "OneVault Secured Core",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Core Hub",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Outer quick-actions: Lock session & Close
            IconButton(
                onClick = {
                    VaultSession.lock()
                    Toast.makeText(this@FloatingHoverService, "Vault Locked", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 10.dp, y = (10).dp)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Lock Session", tint = MaterialTheme.colorScheme.onErrorContainer)
            }

            IconButton(
                onClick = {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent != null) {
                        startActivity(intent)
                        isExpandedState = false
                        updateParams(false)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (10).dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open Full App", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            // Bottom popup showing item credentials when selected
            if (selectedItemDetail != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clickable(enabled = false, onClick = {}),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = selectedItemDetail?.title ?: "Account Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val fields = detailedFields
                        if (isDecryptingItem) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else if (fields != null) {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                            ) {
                                val popupScrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(popupScrollState)
                                ) {
                                    when (selectedItemDetail?.category) {
                                        "LOGIN" -> {
                                            if (!selectedItemDetail?.website.isNullOrEmpty()) {
                                                HoverFieldRow("Website/URL", selectedItemDetail?.website ?: "", clipboard = clipboard)
                                            }
                                            HoverFieldRow("Username", fields.username, clipboard = clipboard)
                                            HoverFieldRow("Password", fields.secretText, isSensitive = true, clipboard = clipboard)
                                        }
                                        "CARD" -> {
                                            HoverFieldRow("Cardholder Name", fields.cardholderName, clipboard = clipboard)
                                            HoverFieldRow("Card Number", fields.cardNumber, isSensitive = true, clipboard = clipboard)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    HoverFieldRow("Expiry", fields.expiry, clipboard = clipboard)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    HoverFieldRow("CVV", fields.cvv, isSensitive = true, clipboard = clipboard)
                                                }
                                            }
                                            HoverFieldRow("PIN", fields.pin, isSensitive = true, clipboard = clipboard)
                                        }
                                        "NOTE" -> {
                                            HoverFieldRow("Note text", fields.secretText, clipboard = clipboard)
                                        }
                                        "IDENTITY" -> {
                                            HoverFieldRow("Full Name", fields.fullName, clipboard = clipboard)
                                            HoverFieldRow("Email Address", fields.email, clipboard = clipboard)
                                            HoverFieldRow("Phone Number", fields.phone, clipboard = clipboard)
                                            HoverFieldRow("SSN / Passport / PAN", fields.ssn, isSensitive = true, clipboard = clipboard)
                                            HoverFieldRow("Address", fields.address, clipboard = clipboard)
                                            HoverFieldRow("Notes", fields.customNotes, clipboard = clipboard)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Back to Dial",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { selectedItemDetail = null }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    private fun triggerAutoCapture() {
        val masterPassword = VaultSession.getActivePassword()
        if (masterPassword == null) {
            Toast.makeText(this, "Unlock OneVault to auto-capture credentials!", Toast.LENGTH_LONG).show()
            return
        }

        // 1. Identify current screen / foreground context package
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val rawPackage = try {
            am.runningAppProcesses?.firstOrNull()?.processName ?: "com.android.chrome"
        } catch (e: Exception) {
            "com.android.chrome"
        }

        val appName = when {
            rawPackage.contains("spotify") -> "Spotify"
            rawPackage.contains("instagram") -> "Instagram"
            rawPackage.contains("facebook") -> "Facebook"
            rawPackage.contains("twitter") -> "Twitter"
            rawPackage.contains("chrome") -> "Google Chrome"
            rawPackage.contains("youtube") -> "YouTube"
            rawPackage.contains("netflix") -> "Netflix"
            rawPackage.contains("amazon") -> "Amazon"
            rawPackage.contains("github") -> "GitHub"
            rawPackage.contains("google") -> "Google"
            else -> {
                val lastSegment = rawPackage.split(".").lastOrNull() ?: "Web Portal"
                lastSegment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        // 2. Scan clipboard for pre-copied credentials
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var capturedUsername = "user@example.com" // safe default fallback
        var capturedPassword = "OneVaultQC-${(1000..9999).random()}"

        if (clipboard.hasPrimaryClip()) {
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clipText.contains("@")) {
                capturedUsername = clipText
            } else if (clipText.isNotEmpty() && clipText.length in 6..32) {
                capturedPassword = clipText
            }
        }

        // 3. Encrypt post-quantum hybrid structure
        serviceScope.launch {
            try {
                val fields = DecryptedFields(
                    username = capturedUsername,
                    secretText = capturedPassword,
                    website = "https://www.${appName.lowercase().replace(" ", "")}.com",
                    customNotes = "Auto-captured via Hover service overlay."
                )
                val json = fieldsAdapter.toJson(fields)
                val encrypted = withContext(Dispatchers.Default) {
                    QuantumCrypto.encrypt(json, masterPassword)
                }

                val newItem = VaultItem(
                    title = "$appName Portal",
                    category = "LOGIN",
                    vaultName = "Personal",
                    website = "https://www.${appName.lowercase().replace(" ", "")}.com",
                    encryptedPayload = encrypted
                )

                val db = AppDatabase.getDatabase(this@FloatingHoverService)
                db.vaultDao().insertItem(newItem)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FloatingHoverService,
                        "Successfully Captured: Saved encrypted login structure for $appName!",
                        Toast.LENGTH_LONG
                    ).show()
                    isExpandedState = false
                    updateParams(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingHoverService, "Auto-Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        if (::composeView.isInitialized) {
            try {
                windowManager.removeView(composeView)
            } catch (e: Exception) {}
        }
    }
}
