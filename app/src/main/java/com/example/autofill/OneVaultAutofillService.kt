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
                    // Vault is unlocked. Process logins
                    var datasetCount = 0
                    for (item in allItems) {
                        if (item.category == "LOGIN") {
                            try {
                                val decryptedStr = QuantumCrypto.decrypt(item.encryptedPayload, masterPassword)
                                val fields = fieldsAdapter.fromJson(decryptedStr) ?: continue
                                
                                val usernameVal = fields.username
                                val passwordVal = fields.secretText

                                if (usernameVal.isNotEmpty() || passwordVal.isNotEmpty()) {
                                    val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                                        setTextViewText(android.R.id.text1, "🔑 OneVault: ${item.title} ($usernameVal)")
                                    }
                                    val dataset = Dataset.Builder()
                                    if (uId != null) {
                                        dataset.setValue(uId, AutofillValue.forText(usernameVal), presentation)
                                    }
                                    if (pId != null) {
                                        dataset.setValue(pId, AutofillValue.forText(passwordVal), presentation)
                                    }
                                    responseBuilder.addDataset(dataset.build())
                                    datasetCount++
                                    // Limit datasets to 3 for safety and screen space
                                    if (datasetCount >= 3) break
                                }
                            } catch (e: Exception) {
                                // Master password may have changed or decryption failed
                            }
                        }
                    }
                }
                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                callback.onFailure(e.localizedMessage)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
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
