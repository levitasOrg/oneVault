package com.example.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.example.crypto.QuantumCrypto
import com.example.crypto.VaultSession
import com.example.data.AppDatabase
import com.example.data.DecryptedFields
import com.example.data.VaultItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OneVaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val fieldsAdapter = moshi.adapter(DecryptedFields::class.java)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val contexts = request.fillContexts
        val structure = contexts[contexts.size - 1].structure

        // 1. Identify current username and password fields in the screen's AssistStructure
        val targets = findAutofillFields(structure)
        val uId = targets.usernameId
        val pId = targets.passwordId

        if (uId == null && pId == null) {
            callback.onSuccess(null)
            return
        }

        // Identify what app/site is asking, so we only offer matching credentials.
        val requestingContext = extractRequestingContext(structure)

        // 2. Fetch Vault Entries from DB and try to Match
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@OneVaultAutofillService)
                val allItems = db.vaultDao().getAllItems().first()

                val masterPassword = VaultSession.getActivePassword()
                val responseBuilder = FillResponse.Builder()

                if (masterPassword == null) {
                    // Vault is locked. Present an unlock prompt dataset
                    val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, "Unlock OneVault to autofill")
                    }
                    val dataset = Dataset.Builder()
                    if (uId != null) {
                        dataset.setValue(uId, null, presentation)
                    }
                    if (pId != null) {
                        dataset.setValue(pId, null, presentation)
                    }
                    responseBuilder.addDataset(dataset.build())
                } else {
                    // Vault is unlocked. Decrypt logins and rank them by how well they match the
                    // requesting app/site. Only matching credentials are offered, so a Netflix
                    // login is never shown on a Google sign-in form.
                    val candidates = mutableListOf<Pair<Int, Triple<String, String, String>>>() // score to (title, user, pass)
                    for (item in allItems) {
                        if (item.category != "LOGIN") continue
                        try {
                            val decryptedStr = QuantumCrypto.decrypt(item.encryptedPayload, masterPassword)
                            val fields = fieldsAdapter.fromJson(decryptedStr) ?: continue
                            val usernameVal = fields.username
                            val passwordVal = fields.secretText
                            if (usernameVal.isEmpty() && passwordVal.isEmpty()) continue

                            val score = matchScore(requestingContext, item.website, item.title, fields.website)
                            // Drop non-matches when we actually know the requesting context. If we
                            // could not determine any context, fall back to showing everything.
                            if (requestingContext.isNotEmpty() && score <= 0) continue
                            candidates.add(score to Triple(item.title, usernameVal, passwordVal))
                        } catch (e: Exception) {
                            // Master password may have changed or decryption failed
                        }
                    }

                    candidates.sortByDescending { it.first }
                    for ((_, cred) in candidates.take(3)) {
                        val (title, usernameVal, passwordVal) = cred
                        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                            setTextViewText(android.R.id.text1, "🔑 OneVault: $title ($usernameVal)")
                        }
                        val dataset = Dataset.Builder()
                        if (uId != null) {
                            dataset.setValue(uId, AutofillValue.forText(usernameVal), presentation)
                        }
                        if (pId != null) {
                            dataset.setValue(pId, AutofillValue.forText(passwordVal), presentation)
                        }
                        responseBuilder.addDataset(dataset.build())
                    }
                }
                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                callback.onFailure(e.localizedMessage)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // We can only persist a captured credential while the vault is unlocked (we need the
        // master password to encrypt it). If it is locked, acknowledge without saving rather than
        // silently dropping the data into an unencrypted store.
        val masterPassword = VaultSession.getActivePassword()
        if (masterPassword == null) {
            callback.onSuccess()
            return
        }

        val contexts = request.fillContexts
        val structure = contexts[contexts.size - 1].structure
        val targets = findAutofillFields(structure)
        val username = targets.usernameId?.let { readFieldText(structure, it) } ?: ""
        val password = targets.passwordId?.let { readFieldText(structure, it) } ?: ""

        if (password.isEmpty()) {
            callback.onSuccess()
            return
        }

        serviceScope.launch {
            try {
                val context = extractRequestingContext(structure)
                val title = context.ifEmpty { "Saved Login" }
                val fields = DecryptedFields(
                    username = username,
                    secretText = password,
                    website = context,
                    customNotes = "Captured via Android autofill."
                )
                val payload = QuantumCrypto.encrypt(fieldsAdapter.toJson(fields), masterPassword)
                val db = AppDatabase.getDatabase(this@OneVaultAutofillService)
                db.vaultDao().insertItem(
                    VaultItem(
                        title = title,
                        category = "LOGIN",
                        vaultName = "Personal",
                        website = context,
                        encryptedPayload = payload
                    )
                )
                callback.onSuccess()
            } catch (e: Exception) {
                callback.onFailure(e.localizedMessage)
            }
        }
    }

    /**
     * Best-effort identifier of the requesting app/site: prefers a web domain from the structure,
     * otherwise the app package name. Lower-cased for stable matching.
     */
    private fun extractRequestingContext(structure: AssistStructure): String {
        var webDomain = ""
        val count = structure.windowNodeCount
        for (i in 0 until count) {
            webDomain = findWebDomain(structure.getWindowNodeAt(i).rootViewNode) ?: ""
            if (webDomain.isNotEmpty()) break
        }
        if (webDomain.isNotEmpty()) return webDomain.lowercase()
        return (structure.activityComponent?.packageName ?: "").lowercase()
    }

    private fun findWebDomain(node: AssistStructure.ViewNode?): String? {
        if (node == null) return null
        node.webDomain?.takeIf { it.isNotEmpty() }?.let { return it }
        for (i in 0 until node.childCount) {
            findWebDomain(node.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun readFieldText(structure: AssistStructure, id: AutofillId): String {
        val count = structure.windowNodeCount
        for (i in 0 until count) {
            val found = readFieldTextRecursive(structure.getWindowNodeAt(i).rootViewNode, id)
            if (found != null) return found
        }
        return ""
    }

    private fun readFieldTextRecursive(node: AssistStructure.ViewNode?, id: AutofillId): String? {
        if (node == null) return null
        if (node.autofillId == id) {
            node.autofillValue?.let { if (it.isText) return it.textValue.toString() }
            node.text?.let { return it.toString() }
        }
        for (i in 0 until node.childCount) {
            readFieldTextRecursive(node.getChildAt(i), id)?.let { return it }
        }
        return null
    }

    /**
     * Scores how well a stored item matches the requesting context. Higher is better; 0 means
     * no meaningful match. Compares against the item's website, its decrypted website, and title.
     */
    private fun matchScore(context: String, vararg fieldsToCheck: String): Int {
        if (context.isEmpty()) return 0
        val ctxCore = coreDomain(context)
        var best = 0
        for (raw in fieldsToCheck) {
            val field = raw.lowercase().trim()
            if (field.isEmpty()) continue
            val fieldCore = coreDomain(field)
            when {
                fieldCore.isNotEmpty() && fieldCore == ctxCore -> best = maxOf(best, 100)
                ctxCore.isNotEmpty() && field.contains(ctxCore) -> best = maxOf(best, 60)
                fieldCore.isNotEmpty() && context.contains(fieldCore) -> best = maxOf(best, 60)
            }
        }
        return best
    }

    /** Reduces "https://www.netflix.com/login" or "com.netflix.app" to a comparable core token. */
    private fun coreDomain(value: String): String {
        var v = value.lowercase()
        v = v.substringAfter("://").substringBefore("/")
        v = v.removePrefix("www.")
        val parts = v.split(".", "/").filter { it.isNotEmpty() }
        // For "com.netflix.app" or "netflix.com" pick the most distinctive label.
        val ignore = setOf("com", "org", "net", "io", "app", "android", "co", "www")
        return parts.filterNot { it in ignore }.maxByOrNull { it.length } ?: ""
    }

    private data class AutofillTargets(
        var usernameId: AutofillId? = null,
        var passwordId: AutofillId? = null
    )

    private fun findAutofillFields(structure: AssistStructure): AutofillTargets {
        val targets = AutofillTargets()
        val count = structure.windowNodeCount
        for (i in 0 until count) {
            val node = structure.getWindowNodeAt(i)
            findFieldsRecursive(node.rootViewNode, targets)
        }
        return targets
    }

    private fun findFieldsRecursive(node: AssistStructure.ViewNode?, targets: AutofillTargets) {
        if (node == null) return

        val hints = node.autofillHints
        if (hints != null) {
            for (hint in hints) {
                if (hint.contains("username", ignoreCase = true) || hint.contains("email", ignoreCase = true)) {
                    targets.usernameId = node.autofillId
                } else if (hint.contains("password", ignoreCase = true)) {
                    targets.passwordId = node.autofillId
                }
            }
        }

        val idEntry = node.idEntry
        val hintText = node.hint

        if (idEntry != null) {
            if (idEntry.contains("username", ignoreCase = true) || idEntry.contains("email", ignoreCase = true) || idEntry.contains("login", ignoreCase = true)) {
                if (targets.usernameId == null) targets.usernameId = node.autofillId
            } else if (idEntry.contains("password", ignoreCase = true) || idEntry.contains("passwd", ignoreCase = true)) {
                if (targets.passwordId == null) targets.passwordId = node.autofillId
            }
        }

        if (hintText != null) {
            if (hintText.contains("username", ignoreCase = true) || hintText.contains("email", ignoreCase = true)) {
                if (targets.usernameId == null) targets.usernameId = node.autofillId
            } else if (hintText.contains("password", ignoreCase = true)) {
                if (targets.passwordId == null) targets.passwordId = node.autofillId
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            findFieldsRecursive(node.getChildAt(i), targets)
        }
    }
}
