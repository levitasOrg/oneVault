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

