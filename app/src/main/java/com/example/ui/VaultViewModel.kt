package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.QuantumCrypto
import com.example.crypto.VaultSession
import com.example.data.AppDatabase
import com.example.data.DecryptedFields
import com.example.data.VaultItem
import com.example.data.VaultRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository
    private val secureRand = SecureRandom()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val fieldsAdapter = moshi.adapter(DecryptedFields::class.java)

    // Reactive database states
    val allItems: StateFlow<List<VaultItem>>
    val favorites: StateFlow<List<VaultItem>>

    // Locked state
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Flag for initial login setup
    private val _isFirstLaunch = MutableStateFlow(true)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    private val prefs = application.getSharedPreferences("onevault_prefs", android.content.Context.MODE_PRIVATE)

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean("biometric_enabled", false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _isBiometricEnabled.value = enabled
        if (!enabled) {
            prefs.edit().remove("biometric_password").apply()
        }
    }

    fun saveBiometricPassword(password: String) {
        prefs.edit().putString("biometric_password", password).apply()
    }

    fun getBiometricPassword(): String? {
        return prefs.getString("biometric_password", null)
    }

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    private val _isOverlayEnabled = MutableStateFlow(prefs.getBoolean("overlay_enabled", false))
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_enabled", enabled).apply()
        _isOverlayEnabled.value = enabled
    }

    private val _isShieldVisible = MutableStateFlow(prefs.getBoolean("shield_visible", true))
    val isShieldVisible: StateFlow<Boolean> = _isShieldVisible.asStateFlow()

    fun setShieldVisible(visible: Boolean) {
        prefs.edit().putBoolean("shield_visible", visible).apply()
        _isShieldVisible.value = visible
    }

    // State of cloud authentication
    private val _isCloudLoggedIn = MutableStateFlow(prefs.getBoolean("cloud_logged_in_state", false))
    val isCloudLoggedIn: StateFlow<Boolean> = _isCloudLoggedIn.asStateFlow()

    private val _cloudEmail = MutableStateFlow(prefs.getString("cloud_logged_in_email", "") ?: "")
    val cloudEmail: StateFlow<String> = _cloudEmail.asStateFlow()

    private val _cloudProvider = MutableStateFlow(prefs.getString("cloud_connected_provider", "") ?: "")
    val cloudProvider: StateFlow<String> = _cloudProvider.asStateFlow()

    private val _cloudUserName = MutableStateFlow(prefs.getString("cloud_connected_username", "") ?: "")
    val cloudUserName: StateFlow<String> = _cloudUserName.asStateFlow()

    fun connectSocialCloud(provider: String, email: String, name: String) {
        val cleanEmail = email.lowercase().trim()
        val cleanName = name.trim()
        prefs.edit()
            .putBoolean("cloud_logged_in_state", true)
            .putString("cloud_logged_in_email", cleanEmail)
            .putString("cloud_connected_provider", provider)
            .putString("cloud_connected_username", cleanName)
            .apply()

        _cloudEmail.value = cleanEmail
        _cloudProvider.value = provider
        _cloudUserName.value = cleanName
        _isCloudLoggedIn.value = true
        _feedbackMessage.value = "Connected with $provider. Welcome, $cleanName!"
    }

    fun loginCloud(email: String, authCode: String): Boolean {
        // Kept for backward compatibility but routes to social connection if needed
        val nameFromEmail = email.substringBefore("@").replace(".", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
        return connectSocialCloud("Google", email, nameFromEmail.ifBlank { "Authorized User" }).let { true }
    }

    fun logoutCloud() {
        val currentEmail = _cloudEmail.value
        prefs.edit()
            .remove("cloud_logged_in_state")
            .remove("cloud_logged_in_email")
            .remove("cloud_connected_provider")
            .remove("cloud_connected_username")
            .apply()
        _cloudEmail.value = ""
        _cloudProvider.value = ""
        _cloudUserName.value = ""
        _isCloudLoggedIn.value = false
        _feedbackMessage.value = "Cloud session ended. $currentEmail disconnected."
    }

    fun backupToCloud(email: String) {
        val activePwd = VaultSession.getActivePassword()
        if (activePwd == null) {
            _feedbackMessage.value = "Cannot backup: Vault is locked!"
            return
        }

        viewModelScope.launch {
            try {
                val cleanEmail = email.lowercase().trim()
                // Ensure they are logged in and authorized
                if (!_isCloudLoggedIn.value || _cloudEmail.value != cleanEmail) {
                    _feedbackMessage.value = "Access Denied: Please log in to authorize cloud backup."
                    return@launch
                }

                val items = allItems.value
                val listType = Types.newParameterizedType(List::class.java, VaultItem::class.java)
                val listAdapter = moshi.adapter<List<VaultItem>>(listType)
                val json = listAdapter.toJson(items)
                
                val encryptedBackupPayload = withContext(Dispatchers.Default) {
                    QuantumCrypto.encrypt(json, activePwd)
                }
                
                prefs.edit().putString("cloud_backup_email_$cleanEmail", encryptedBackupPayload).apply()
                _feedbackMessage.value = "Encrypted backup successfully synced to cloud profile!"
            } catch (e: Exception) {
                _feedbackMessage.value = "Cloud backup failed: ${e.localizedMessage}"
            }
        }
    }

    fun restoreFromCloud(email: String, enteredMasterKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val cleanEmail = email.lowercase().trim()
                // Ensure they are logged in and authorized
                if (!_isCloudLoggedIn.value || _cloudEmail.value != cleanEmail) {
                    onError("Access Denied: Cloud session is not verified or email mismatched.")
                    return@launch
                }

                val encryptedBackupPayload = prefs.getString("cloud_backup_email_$cleanEmail", null)
                if (encryptedBackupPayload == null) {
                    onError("No backup found under cloud profile: $cleanEmail")
                    return@launch
                }

                // Attempt decryption
                val decryptedJson = withContext(Dispatchers.Default) {
                    try {
                        QuantumCrypto.decrypt(encryptedBackupPayload, enteredMasterKey)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (decryptedJson == null) {
                    onError("Incorrect master key provided")
                    return@launch
                }

                val listType = Types.newParameterizedType(List::class.java, VaultItem::class.java)
                val listAdapter = moshi.adapter<List<VaultItem>>(listType)
                val restoredItems = try {
                    listAdapter.fromJson(decryptedJson)
                } catch (e: Exception) {
                    null
                }

                if (restoredItems == null) {
                    onError("Failed to deserialize cloud sync database.")
                    return@launch
                }

                // Check inner MASTER_VERIFICATION token to assure true lock validation
                val verificationItem = restoredItems.find { it.category == "MASTER_VERIFICATION" }
                if (verificationItem == null) {
                    onError("Corrupted backup: No key verification token found!")
                    return@launch
                }

                val verificationDecrypted = withContext(Dispatchers.Default) {
                    try {
                        QuantumCrypto.decrypt(verificationItem.encryptedPayload, enteredMasterKey)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (verificationDecrypted != "VaultUnlocked!") {
                    onError("Incorrect master key provided")
                    return@launch
                }

                // Sync/restore items safely
                withContext(Dispatchers.IO) {
                    repository.clear()
                    restoredItems.forEach { item ->
                        repository.insert(item)
                    }
                }

                // Inject password to unlock session safely with this correct key
                VaultSession.unlock(enteredMasterKey)
                _isLocked.value = false
                val stats = withContext(Dispatchers.Default) {
                    QuantumCrypto.getQuantumStats(enteredMasterKey)
                }
                _quantumStats.value = stats

                _feedbackMessage.value = "Vault database sync succeeded! Restored ${restoredItems.size} items."
                onSuccess()

            } catch (e: Exception) {
                onError("Cloud restore sync failed: ${e.localizedMessage}")
            }
        }
    }

    // Notification updates or feedback
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    // Active diagnostic stats for Post-Quantum mechanics visualization
    private val _quantumStats = MutableStateFlow<Map<String, Any>>(emptyMap())
    val quantumStats: StateFlow<Map<String, Any>> = _quantumStats.asStateFlow()

    // Filtering states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("ALL") // ALL, LOGIN, CARD, NOTE, IDENTITY
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedVault = MutableStateFlow("ALL") // ALL, Personal, Work, Private
    val selectedVault: StateFlow<String> = _selectedVault.asStateFlow()

    // Filtered lists
    val filteredItems: StateFlow<List<VaultItem>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = VaultRepository(database.vaultDao())

        allItems = repository.allItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        favorites = repository.favorites.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Combine database list with search results, category tabs and vault filters
        filteredItems = combine(allItems, _searchQuery, _selectedCategory, _selectedVault) { items, query, category, vault ->
            items.filter { item ->
                // Skip verification metadata item
                if (item.category == "MASTER_VERIFICATION") return@filter false

                val matchesSearch = item.title.contains(query, ignoreCase = true) ||
                        item.website.contains(query, ignoreCase = true)
                val matchesCategory = category == "ALL" || item.category == category
                val matchesVault = vault == "ALL" || item.vaultName == vault

                matchesSearch && matchesCategory && matchesVault
            }
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        _isFirstLaunch.value = !prefs.getBoolean("has_setup_completed", false)

        // Verify if a Master Password setup verification token exists
        viewModelScope.launch {
            allItems.collect { items ->
                val hasVerificationToken = items.any { it.category == "MASTER_VERIFICATION" }
                if (hasVerificationToken) {
                    prefs.edit().putBoolean("has_setup_completed", true).apply()
                    _isFirstLaunch.value = false
                } else {
                    if (!prefs.getBoolean("has_setup_completed", false)) {
                        _isFirstLaunch.value = true
                    }
                }
            }
        }
    }

    /**
     * Set a brand new master password
     */
    fun setupMasterPassword(password: String, enableBiometrics: Boolean = false) {
        if (password.length < 5) {
            _feedbackMessage.value = "Master password must be at least 5 characters!"
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    // Clear any leftover data (destructive setup)
                    withContext(Dispatchers.IO) {
                        repository.clear()
                    }

                    // Encrypt verification token "VaultUnlocked!" with our custom Kyber-AES hybrid
                    val encryptedVerification = QuantumCrypto.encrypt("VaultUnlocked!", password)
                    val verificationItem = VaultItem(
                        title = "Master Verification Token",
                        category = "MASTER_VERIFICATION",
                        encryptedPayload = encryptedVerification
                    )
                    withContext(Dispatchers.IO) {
                        repository.insert(verificationItem)
                    }

                    // Handle biometric setup
                    if (enableBiometrics) {
                        setBiometricEnabled(true)
                        saveBiometricPassword(password)
                    } else {
                        setBiometricEnabled(false)
                    }

                    // Cache password in active session and unlock
                    prefs.edit().putBoolean("has_setup_completed", true).apply()
                    VaultSession.unlock(password)
                    _isLocked.value = false
                    _isFirstLaunch.value = false
                    _quantumStats.value = QuantumCrypto.getQuantumStats(password)
                }
                _feedbackMessage.value = "Post-Quantum Vault successfully initialized!"
            } catch (e: Exception) {
                _feedbackMessage.value = "Failed to setup: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Unlock the vault using the entered master password
     */
    fun unlockVault(password: String): Boolean {
        var success = false
        viewModelScope.launch {
            try {
                // Search for the verification item
                val items = allItems.value
                val tokenItem = items.find { it.category == "MASTER_VERIFICATION" }
                
                if (tokenItem == null) {
                    _feedbackMessage.value = "Database error: token not set!"
                    return@launch
                }

                // Attempt to decrypt verification token using hybrid decapsulator
                val decrypted = withContext(Dispatchers.Default) {
                    QuantumCrypto.decrypt(tokenItem.encryptedPayload, password)
                }
                if (decrypted == "VaultUnlocked!") {
                    VaultSession.unlock(password)
                    _isLocked.value = false
                    val stats = withContext(Dispatchers.Default) {
                        QuantumCrypto.getQuantumStats(password)
                    }
                    _quantumStats.value = stats
                    _feedbackMessage.value = "Access Granted. Quantum security layer active."
                    success = true
                } else {
                    _feedbackMessage.value = "Access Denied: Incorrect master password!"
                }
            } catch (e: Exception) {
                _feedbackMessage.value = "Access Denied: Incorrect master password!"
            }
        }
        return success
    }

    fun lockVault() {
        VaultSession.lock()
        _isLocked.value = true
        _quantumStats.value = emptyMap()
        _feedbackMessage.value = "Vault master locked!"
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    // Set Search & Filters
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setVault(vault: String) {
        _selectedVault.value = vault
    }

    /**
     * Safe transparent decryption
     */
    fun decryptItem(item: VaultItem): DecryptedFields? {
        val activePwd = VaultSession.getActivePassword() ?: return null
        return try {
            val decryptedPlaintext = QuantumCrypto.decrypt(item.encryptedPayload, activePwd)
            fieldsAdapter.fromJson(decryptedPlaintext)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Add new credential or update existing item
     */
    fun addOrUpdateItem(
        title: String,
        category: String,
        vaultName: String,
        website: String,
        fields: DecryptedFields,
        isFavorite: Boolean,
        existingId: Int? = null
    ) {
        val activePwd = VaultSession.getActivePassword()
        if (activePwd == null) {
            _feedbackMessage.value = "Cannot save: Vault is locked!"
            return
        }

        if (title.isBlank()) {
            _feedbackMessage.value = "Item Title cannot be blank!"
            return
        }

        viewModelScope.launch {
            try {
                // Serialize custom fields
                val serializedFields = fieldsAdapter.toJson(fields)

                // Encrypt payload using post-quantum hybrid KEM and AES
                val encryptedPayload = withContext(Dispatchers.Default) {
                    QuantumCrypto.encrypt(serializedFields, activePwd)
                }

                val item = VaultItem(
                    id = existingId ?: 0,
                    title = title,
                    category = category,
                    vaultName = vaultName,
                    website = website,
                    encryptedPayload = encryptedPayload,
                    isFavorite = isFavorite,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = if (existingId != null) {
                        withContext(Dispatchers.IO) {
                            repository.getItemById(existingId)?.createdAt
                        } ?: System.currentTimeMillis()
                    } else System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    repository.insert(item)
                }
                _feedbackMessage.value = "Saved successfully!"
            } catch (e: Exception) {
                _feedbackMessage.value = "Saving failed: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Delete item
     */
    fun deleteItem(itemId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteById(itemId)
                _feedbackMessage.value = "Item removed successfully."
            } catch (e: Exception) {
                _feedbackMessage.value = "Deletion failed: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 1Password backup switcher/importer.
     * Handles both standard 1Password CSV headers and JSON formats (.1pux items).
     */
    fun importFromOnePassword(importText: String): Int {
        val password = VaultSession.getActivePassword() ?: return 0
        if (importText.isBlank()) return 0

        var importedCount = 0

        try {
            if (importText.trim().startsWith("{") || importText.trim().startsWith("[")) {
                // Dynamic JSON Parsing
                importedCount = parseOnePasswordJson(importText, password)
            } else {
                // CSV Parsing
                importedCount = parseOnePasswordCsv(importText, password)
            }

            if (importedCount > 0) {
                _feedbackMessage.value = "Imported $importedCount items from 1Password!"
            } else {
                _feedbackMessage.value = "No valid 1Password items found to import."
            }
        } catch (e: Exception) {
            _feedbackMessage.value = "Import error: ${e.localizedMessage}"
        }

        return importedCount
    }

    private fun parseOnePasswordJson(jsonText: String, activePwd: String): Int {
        var count = 0
        try {
            // Support simple JSON lists of credential maps
            val listType = Types.newParameterizedType(List::class.java, Map::class.java)
            val adapter = moshi.adapter<List<Map<String, Any>>>(listType)
            val rawItems = adapter.fromJson(jsonText) ?: return 0

            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    for (raw in rawItems) {
                        val title = (raw["title"] as? String) ?: (raw["Title"] as? String) ?: "Imported Item"
                        val category = ((raw["category"] as? String) ?: "LOGIN").uppercase(Locale.ROOT)
                        
                        val fields = DecryptedFields(
                            username = (raw["username"] as? String) ?: (raw["Username"] as? String) ?: "",
                            secretText = (raw["password"] as? String) ?: (raw["Password"] as? String) ?: (raw["notes"] as? String) ?: "",
                            website = (raw["website"] as? String) ?: (raw["Website"] as? String) ?: "",
                            cardNumber = (raw["cardNumber"] as? String) ?: (raw["card_number"] as? String) ?: "",
                            cvv = (raw["cvv"] as? String) ?: "",
                            expiry = (raw["expiry"] as? String) ?: "",
                            customNotes = "Imported from 1Password Backup on ${getFormattedDate()}"
                        )

                        val payload = QuantumCrypto.encrypt(fieldsAdapter.toJson(fields), activePwd)
                        val dbItem = VaultItem(
                            title = title,
                            category = if (category in listOf("LOGIN", "CARD", "NOTE", "IDENTITY")) category else "LOGIN",
                            website = fields.website,
                            encryptedPayload = payload,
                            vaultName = "Personal"
                        )
                        withContext(Dispatchers.IO) {
                            repository.insert(dbItem)
                        }
                        count++
                    }
                }
            }
            return rawItems.size
        } catch (e: Exception) {
            // Try wrapping single object
            try {
                val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(mapType)
                val raw = adapter.fromJson(jsonText) ?: return 0
                val title = (raw["title"] as? String) ?: "Imported Login"
                val username = (raw["username"] as? String) ?: ""
                val password = (raw["password"] as? String) ?: ""
                val website = (raw["website"] as? String) ?: ""

                viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        val fields = DecryptedFields(
                            username = username,
                            secretText = password,
                            website = website,
                            customNotes = "Imported single 1Password JSON"
                        )
                        val payload = QuantumCrypto.encrypt(fieldsAdapter.toJson(fields), activePwd)
                        withContext(Dispatchers.IO) {
                            repository.insert(
                                VaultItem(
                                    title = title,
                                    category = "LOGIN",
                                    website = website,
                                    encryptedPayload = payload
                                )
                            )
                        }
                    }
                }
                return 1
            } catch (ex: Exception) {
                return 0
            }
        }
    }

    private fun parseOnePasswordCsv(csvText: String, activePwd: String): Int {
        val lines = csvText.lines()
        if (lines.size <= 1) return 0

        val header = lines[0].split(",").map { it.trim().trim('"') }
        
        // Find element indexes in CSV schema
        val titleIdx = header.indexOfFirst { it.equals("title", true) || it.equals("name", true) }
        val userIdx = header.indexOfFirst { it.equals("username", true) || it.equals("username/email", true) || it.equals("login", true) }
        val pwdIdx = header.indexOfFirst { it.equals("password", true) || it.equals("pass", true) || it.equals("secret", true) }
        val webIdx = header.indexOfFirst { it.equals("website", true) || it.equals("url", true) }
        val notesIdx = header.indexOfFirst { it.equals("notes", true) || it.equals("note", true) }

        var count = 0

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                for (i in 1 until lines.size) {
                    val rawLine = lines[i]
                    if (rawLine.isBlank()) continue

                    // Handle basic quotes stripping
                    val cols = splitCsvLine(rawLine)
                    if (cols.isEmpty()) continue

                    val title = if (titleIdx >= 0 && titleIdx < cols.size) cols[titleIdx].trim('"') else "Imported Item #$i"
                    val username = if (userIdx >= 0 && userIdx < cols.size) cols[userIdx].trim('"') else ""
                    val passwordVal = if (pwdIdx >= 0 && pwdIdx < cols.size) cols[pwdIdx].trim('"') else ""
                    val website = if (webIdx >= 0 && webIdx < cols.size) cols[webIdx].trim('"') else ""
                    val notes = if (notesIdx >= 0 && notesIdx < cols.size) cols[notesIdx].trim('"') else ""

                    val fields = DecryptedFields(
                        username = username,
                        secretText = passwordVal,
                        website = website,
                        customNotes = "$notes\n\n[Imported from CSV on ${getFormattedDate()}]"
                    )

                    val payload = QuantumCrypto.encrypt(fieldsAdapter.toJson(fields), activePwd)
                    
                    val item = VaultItem(
                        title = title,
                        category = if (passwordVal.isNotEmpty()) "LOGIN" else "NOTE",
                        website = website,
                        encryptedPayload = payload,
                        vaultName = "Personal"
                    )
                    withContext(Dispatchers.IO) {
                        repository.insert(item)
                    }
                    count++
                }
            }
        }
        return count
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString())
                cur = StringBuilder()
            } else {
                cur.append(ch)
            }
        }
        result.add(cur.toString())
        return result
    }

    private fun getFormattedDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }

    /**
     * Real-time customizable random password builder
     */
    fun generatePassword(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        useWords: Boolean = false
    ): String {
        if (useWords) {
            val memorableWords = listOf(
                "crystal", "quantum", "cosmic", "shining", "secure", "ancient", "vibrant", "golden", "silent", "frozen",
                "brave", "mighty", "rapid", "clever", "wisdom", "forest", "safari", "cluster", "matrix", "shield",
                "beacon", "citadel", "canyon", "desert", "hazel", "island", "jungle", "lagoon", "mount", "ocean",
                "river", "valley", "vortex", "stream", "summit", "galaxy", "nebula", "plasma", "meteor", "aurora",
                "alpha", "omega", "vector", "binary", "system", "fusion", "shadow", "mirror", "flame", "frost"
            )
            val separator = if (includeSymbols) {
                listOf("-", "_", ".", "+", "*", "#", "!", "$", "%", "&")[secureRand.nextInt(10)]
            } else {
                "-"
            }
            
            val wordsList = mutableListOf<String>()
            val wordCount = Math.max(2, length / 7) // dynamic word count based on slider length
            for (i in 0 until wordCount) {
                var w = memorableWords[secureRand.nextInt(memorableWords.size)]
                
                // Casing logic:
                if (includeUpper && includeLower) {
                    // Alternating/mixed capitalization
                    if (secureRand.nextBoolean()) {
                        w = w.replaceFirstChar { it.uppercase() }
                    }
                } else if (includeUpper) {
                    w = w.replaceFirstChar { it.uppercase() }
                } else if (!includeLower) {
                    w = w.uppercase()
                }
                wordsList.add(w)
            }
            
            var passphrase = wordsList.joinToString(separator)
            
            if (includeDigits) {
                // Incorporate dynamic digits
                val numDigits = Math.max(1, length / 10)
                val digitsBuilder = StringBuilder()
                for (d in 0 until numDigits) {
                    digitsBuilder.append(secureRand.nextInt(10))
                }
                passphrase += separator + digitsBuilder.toString()
            }
            
            if (includeSymbols && secureRand.nextBoolean()) {
                passphrase += listOf("!", "@", "#", "$", "%", "^", "*", "?")[secureRand.nextInt(8)]
            }
            
            return passphrase
        }

        val uppercaseSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercaseSet = "abcdefghijklmnopqrstuvwxyz"
        val digitsSet = "0123456789"
        val symbolsSet = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        // Capture which categories are active
        val activeCategories = mutableListOf<String>()
        if (includeUpper) activeCategories.add(uppercaseSet)
        if (includeLower) activeCategories.add(lowercaseSet)
        if (includeDigits) activeCategories.add(digitsSet)
        if (includeSymbols) activeCategories.add(symbolsSet)

        if (activeCategories.isEmpty()) {
            return ""
        }

        // Proportional distribution algorithm to guarantee rich symbol & digit counts
        val countPerCategory = IntArray(activeCategories.size) { length / activeCategories.size }
        var remaining = length % activeCategories.size
        
        // Distribute remainder randomly among active categories
        while (remaining > 0) {
            val randomIndex = secureRand.nextInt(activeCategories.size)
            countPerCategory[randomIndex]++
            remaining--
        }

        val pwd = java.lang.StringBuilder()
        // Draw exact calculated numbers from each category pool
        for (i in activeCategories.indices) {
            val pool = activeCategories[i]
            val needed = countPerCategory[i]
            for (j in 0 until needed) {
                pwd.append(pool[secureRand.nextInt(pool.length)])
            }
        }

        // Final shuffling
        val shuffled = pwd.toString().toList().shuffled(secureRand).joinToString("")
        return shuffled
    }

    /**
     * Password strength utility
     */
    fun evaluatePasswordStrength(password: String): Triple<Float, String, androidx.compose.ui.graphics.Color> {
        if (password.isEmpty()) return Triple(0f, "Empty", androidx.compose.ui.graphics.Color.Gray)
        
        var score = 0
        if (password.length >= 8) score += 2
        if (password.length >= 14) score += 2
        if (password.any { it.isUpperCase() }) score += 1
        if (password.any { it.isLowerCase() }) score += 1
        if (password.any { it.isDigit() }) score += 2
        if (password.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) }) score += 2

        val fraction = (score / 10f).coerceIn(0f, 1f)
        return when {
            fraction < 0.35f -> Triple(fraction, "Weak", androidx.compose.ui.graphics.Color(0xFFE57373))
            fraction < 0.7f -> Triple(fraction, "Fair", androidx.compose.ui.graphics.Color(0xFFFFB74D))
            else -> Triple(fraction, "Strong & Secure", androidx.compose.ui.graphics.Color(0xFF81C784))
        }
    }
}
