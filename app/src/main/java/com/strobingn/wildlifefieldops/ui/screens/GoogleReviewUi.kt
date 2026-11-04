package com.strobingn.wildlifefieldops.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.review.GoogleReviewRequest
import com.strobingn.wildlifefieldops.review.ReviewChannel
import com.strobingn.wildlifefieldops.review.ReviewComposer
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.GoogleReviewViewModel

@Composable
fun GoogleReviewSettingsBlock(
    modifier: Modifier = Modifier,
    viewModel: GoogleReviewViewModel = hiltViewModel()
) {
    val prefs by viewModel.prefs.collectAsState()
    val templateShown = prefs.template.ifBlank { GoogleReviewRequest.DEFAULT_TEMPLATE }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Google reviews",
            style = MaterialTheme.typography.titleSmall,
            color = PrimaryGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("google-review-settings")
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = BackgroundCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Paste the Google review link for your business. When a job is paid in full, you can text, email, or share a short note. You press Send.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                SettingPlainField(
                    storedValue = prefs.link,
                    label = "Google review link",
                    keyboardType = KeyboardType.Uri,
                    onCommit = viewModel::setLink,
                    supportingText = "Example: https://g.page/r/your-business/review"
                )
                SettingPlainField(
                    storedValue = templateShown,
                    label = "Message",
                    onCommit = viewModel::setTemplate,
                    singleLine = false,
                    supportingText = "Use {firstName} and {reviewLink}."
                )
                Text(
                    GoogleReviewRequest.message(templateShown, "Alex Morgan", prefs.link.ifBlank { "{reviewLink}" }),
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun GoogleReviewRequestCard(
    offer: Boolean,
    sentAt: Long?,
    customerName: String,
    phone: String,
    email: String,
    reviewLink: String,
    template: String,
    onOpenSettings: () -> Unit,
    onMarkSent: (Long) -> Unit,
    opener: (Context, ReviewComposer) -> Boolean = ::openReviewComposer,
    now: () -> Long = System::currentTimeMillis
) {
    val context = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf<String?>(null) }
    val sent = GoogleReviewRequest.sentLabel(sentAt)
    if (!offer && sent.isBlank()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google-review-card")
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Google review",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (sent.isNotBlank()) {
                Text(
                    sent,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("google-review-sent")
                )
            }
            if (offer) {
                Text(
                    GoogleReviewRequest.OWNER_SENDS,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        launchError = null
                        if (!GoogleReviewRequest.hasReviewLink(reviewLink)) {
                            ask = true
                        } else {
                            launchError = launchReview(
                                context, ReviewChannel.TEXT, customerName, phone, email,
                                reviewLink, template, opener, now, onMarkSent
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google-review-request")
                ) { Text(GoogleReviewRequest.ACTION) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            launchError = null
                            if (!GoogleReviewRequest.hasReviewLink(reviewLink)) ask = true
                            else launchError = launchReview(
                                context, ReviewChannel.EMAIL, customerName, phone, email,
                                reviewLink, template, opener, now, onMarkSent
                            )
                        },
                        modifier = Modifier.testTag("google-review-email")
                    ) { Text("Email") }
                    OutlinedButton(
                        onClick = {
                            launchError = null
                            if (!GoogleReviewRequest.hasReviewLink(reviewLink)) ask = true
                            else launchError = launchReview(
                                context, ReviewChannel.SHARE, customerName, phone, email,
                                reviewLink, template, opener, now, onMarkSent
                            )
                        },
                        modifier = Modifier.testTag("google-review-share")
                    ) { Text("Share") }
                }
                launchError?.let {
                    Text(it, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (ask) {
        GoogleReviewPromptDialog(
            customerName = customerName,
            phone = phone,
            email = email,
            reviewLink = reviewLink,
            template = template,
            promptAfterPayment = false,
            onDismiss = { ask = false },
            onOpenSettings = {
                ask = false
                onOpenSettings()
            },
            onMarkSent = onMarkSent,
            opener = opener,
            now = now
        )
    }
}

@Composable
fun GoogleReviewPromptDialog(
    customerName: String,
    phone: String,
    email: String,
    reviewLink: String,
    template: String,
    promptAfterPayment: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onMarkSent: (Long) -> Unit,
    opener: (Context, ReviewComposer) -> Boolean = ::openReviewComposer,
    now: () -> Long = System::currentTimeMillis
) {
    val context = LocalContext.current
    var launchError by remember { mutableStateOf<String?>(null) }
    val linked = GoogleReviewRequest.hasReviewLink(reviewLink)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        modifier = Modifier.testTag("google-review-dialog"),
        title = { Text(GoogleReviewRequest.ACTION) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (linked) {
                    if (promptAfterPayment) {
                        Text("This job is paid in full.")
                    }
                    Text(GoogleReviewRequest.OWNER_SENDS)
                    Text(
                        GoogleReviewRequest.message(template, customerName, reviewLink),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(GoogleReviewRequest.MISSING_LINK)
                }
                launchError?.let { Text(it, color = TextPrimary) }
            }
        },
        confirmButton = {
            if (linked) {
                Row {
                    TextButton(
                        onClick = {
                            launchError = launchReview(
                                context, ReviewChannel.TEXT, customerName, phone, email,
                                reviewLink, template, opener, now, onMarkSent, onDismiss
                            )
                        },
                        modifier = Modifier.testTag("google-review-text")
                    ) { Text("Text", color = PrimaryGreen) }
                    TextButton(
                        onClick = {
                            launchError = launchReview(
                                context, ReviewChannel.EMAIL, customerName, phone, email,
                                reviewLink, template, opener, now, onMarkSent, onDismiss
                            )
                        }
                    ) { Text("Email", color = PrimaryGreen) }
                    TextButton(
                        onClick = {
                            launchError = launchReview(
                                context, ReviewChannel.SHARE, customerName, phone, email,
                                reviewLink, template, opener, now, onMarkSent, onDismiss
                            )
                        }
                    ) { Text("Share", color = PrimaryGreen) }
                }
            } else {
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("google-review-open-settings")) {
                    Text("Open Settings", color = PrimaryGreen)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = TextPrimary) }
        }
    )
}

/**
 * Card plus the after-payment prompt. Settings and the sent date come from [GoogleReviewViewModel].
 */
@Composable
fun GoogleReviewSection(
    jobId: String,
    offer: Boolean,
    customerName: String,
    phone: String,
    email: String,
    prompt: Boolean,
    onPromptDismissed: () -> Unit,
    onOpenSettings: () -> Unit,
    showCard: Boolean = true,
    viewModel: GoogleReviewViewModel = hiltViewModel(),
    opener: (Context, ReviewComposer) -> Boolean = ::openReviewComposer,
    now: () -> Long = System::currentTimeMillis
) {
    val prefs by viewModel.prefs.collectAsState()
    val sentAt = prefs.sentAtByJob[jobId]
    if (showCard) {
        GoogleReviewRequestCard(
            offer = offer,
            sentAt = sentAt,
            customerName = customerName,
            phone = phone,
            email = email,
            reviewLink = prefs.link,
            template = prefs.template,
            onOpenSettings = onOpenSettings,
            onMarkSent = { at -> viewModel.markSent(jobId, at) },
            opener = opener,
            now = now
        )
    }
    if (prompt) {
        GoogleReviewPromptDialog(
            customerName = customerName,
            phone = phone,
            email = email,
            reviewLink = prefs.link,
            template = prefs.template,
            promptAfterPayment = true,
            onDismiss = onPromptDismissed,
            onOpenSettings = {
                onPromptDismissed()
                onOpenSettings()
            },
            onMarkSent = { at ->
                viewModel.markSent(jobId, at)
            },
            opener = opener,
            now = now
        )
    }
}

private fun launchReview(
    context: Context,
    channel: ReviewChannel,
    customerName: String,
    phone: String,
    email: String,
    reviewLink: String,
    template: String,
    opener: (Context, ReviewComposer) -> Boolean,
    now: () -> Long,
    onMarkSent: (Long) -> Unit,
    onOpened: () -> Unit = {}
): String? {
    val composer = GoogleReviewRequest.composer(channel, customerName, phone, email, reviewLink, template)
        ?: return null
    val opened = opener(context, composer)
    if (!opened) return GoogleReviewRequest.NO_APP
    onMarkSent(now())
    onOpened()
    return null
}

fun openReviewComposer(context: Context, composer: ReviewComposer): Boolean {
    val spec = GoogleReviewRequest.outbound(composer)
    val intent = if (spec.chooser) {
        val send = Intent(spec.action).apply {
            type = "text/plain"
            spec.extras.forEach { (key, value) -> putExtra(key, value) }
        }
        Intent.createChooser(send, GoogleReviewRequest.ACTION)
    } else {
        Intent(spec.action, Uri.parse(spec.data.orEmpty())).apply {
            spec.extras.forEach { (key, value) -> putExtra(key, value) }
        }
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
