package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.remote.SharedText
import com.strobingn.wildlifefieldops.data.remote.TextImportEntry
import com.strobingn.wildlifefieldops.data.remote.TextImportTarget
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

/** Clearly labeled entry point to the Import from text screen. */
@Composable
fun ImportFromTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Default.Sms, contentDescription = null, tint = PrimaryGreen)
        Spacer(Modifier.width(8.dp))
        Text(
            TextImportEntry.ACTION_LABEL,
            color = PrimaryGreen,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Paste or type a customer's text message, then Read text. The clipboard
 * fills the box when the screen opens. Read text hands the text to the
 * review form, which runs the shared parser and fills empty fields only.
 */
@Composable
fun ImportFromTextScreen(
    target: TextImportTarget,
    onBack: () -> Unit,
    onRead: (SharedText) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable {
        mutableStateOf(TextImportEntry.initialText(readClipboard { clipboard.getText()?.text }))
    }
    ImportFromTextContent(
        target = target,
        text = text,
        onTextChange = { text = it },
        onPaste = { text = TextImportEntry.paste(text, readClipboard { clipboard.getText()?.text }) },
        onClear = { text = "" },
        onReadText = {
            TextImportEntry.handoff(text, System.nanoTime())?.let(onRead)
        },
        onBack = onBack
    )
}

private inline fun readClipboard(read: () -> String?): String? =
    try {
        read()
    } catch (e: Exception) {
        null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportFromTextContent(
    target: TextImportTarget,
    text: String,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onReadText: () -> Unit,
    onBack: () -> Unit
) {
    val formName = when (target) {
        TextImportTarget.JOB -> "New Job"
        TextImportTarget.CUSTOMER -> "New Customer"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TextImportEntry.ACTION_LABEL, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Paste or type the customer's text message. Read text fills the empty fields " +
                    "of a $formName form so you can check them. Nothing is saved until you tap Save.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text(TextImportEntry.BOX_LABEL) },
                placeholder = { Text("Paste the customer's text here", color = TextSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .testTag("import-text-box"),
                minLines = 8,
                colors = jobCustomerFieldColors(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = PrimaryGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(TextImportEntry.PASTE_LABEL, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = text.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(TextImportEntry.CLEAR_LABEL, color = TextSecondary)
                }
            }
            Button(
                onClick = onReadText,
                enabled = TextImportEntry.canRead(text),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) {
                Text(TextImportEntry.READ_LABEL, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "You can edit every field on the next screen. Text you already typed is never replaced.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
