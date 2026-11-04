package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import android.view.View
import android.view.ViewGroup
import com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.review.GoogleReviewRequest
import com.strobingn.wildlifefieldops.review.ReviewChannel
import com.strobingn.wildlifefieldops.review.ReviewComposer
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1400dp-xhdpi")
class GoogleReviewUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val link = "https://g.page/r/wildlife-whisperer/review"
    private val sentAt = Instant.parse("2026-11-04T18:00:00Z").toEpochMilli()

    @Test fun requestButtonOpensSmsWithFirstNameAndLink() {
        val opened = mutableListOf<ReviewComposer>()
        val marked = mutableListOf<Long>()
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = true) {
                GoogleReviewRequestCard(
                    offer = true,
                    sentAt = sentAt,
                    customerName = "Pam Johnston (TSO)",
                    phone = "8455550100",
                    email = "pam@example.com",
                    reviewLink = link,
                    template = "",
                    onOpenSettings = {},
                    onMarkSent = { marked += it },
                    opener = { _, composer -> opened += composer; true },
                    now = { sentAt }
                )
            }
        }
        val sentLabel = GoogleReviewRequest.sentLabel(sentAt)
        assertTrue(sentLabel, sentLabel.matches(Regex("""Review request sent \d{2}/\d{2}/\d{4}""")))
        composeRule.onNodeWithText(sentLabel).assertExists()
        composeRule.onNodeWithTag("google-review-request").performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened.size)
        assertEquals(ReviewChannel.TEXT, opened.single().channel)
        assertTrue(opened.single().body.contains("Hi Pam,"))
        assertTrue(opened.single().body.contains(link))
        assertEquals(listOf(sentAt), marked)
        save("review-request-sent")
    }

    @Test fun missingLinkPromptsForSettingsAndDoesNotOpenAComposer() {
        val opened = mutableListOf<ReviewChannel>()
        var settings = 0
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = true) {
                GoogleReviewRequestCard(
                    offer = true,
                    sentAt = null,
                    customerName = "Pam Johnston",
                    phone = "8455550100",
                    email = "pam@example.com",
                    reviewLink = "",
                    template = "",
                    onOpenSettings = { settings++ },
                    onMarkSent = { error("must not record a send") },
                    opener = { _, composer -> opened += composer.channel; true }
                )
            }
        }
        composeRule.onNodeWithTag("google-review-request").performClick()
        composeRule.onNodeWithText(GoogleReviewRequest.MISSING_LINK).assertExists()
        save("review-request-needs-link")
        composeRule.onNodeWithTag("google-review-open-settings").performClick()
        assertEquals(1, settings)
        assertTrue(opened.isEmpty())
        save("review-request-needs-link")
    }

    @Test fun failedComposerDoesNotCountAsSent() {
        var marked = 0
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = true) {
                GoogleReviewPromptDialog(
                    customerName = "Sam Lee",
                    phone = "",
                    email = "sam@example.com",
                    reviewLink = link,
                    template = "",
                    promptAfterPayment = true,
                    onDismiss = {},
                    onOpenSettings = {},
                    onMarkSent = { marked++ },
                    opener = { _, _ -> false }
                )
            }
        }
        composeRule.onNodeWithText("This job is paid in full.").assertExists()
        save("review-request-after-payment")
        composeRule.onNodeWithText("Email").performClick()
        composeRule.onNodeWithText(GoogleReviewRequest.NO_APP).assertExists()
        assertEquals(0, marked)
    }

    @Test fun recordingThePayingAmountPromptsOnlyWhenTheBalanceClears() {
        var job by mutableStateOf(
            Job(id = "j1", customerName = "Pam Johnston", estimatedValue = 200.0, pricing = JobPricing())
        )
        var prompts = 0
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = true) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobPaymentsCard(
                        job = job,
                        onSave = { job = job.copy(pricing = it) },
                        onBecameFullyPaid = { prompts++ }
                    )
                }
            }
        }
        val total = PaymentLedger.invoiceTotal(job)
        assertEquals(200.0, total, 0.0)
        composeRule.onNode(hasSetTextAction() and hasText("Amount")).performTextInput("50")
        composeRule.onNodeWithText("Record payment").performClick()
        composeRule.waitForIdle()
        assertEquals(0, prompts)
        composeRule.onNode(hasSetTextAction() and hasText("Amount")).performTextInput("150")
        composeRule.onNodeWithText("Record payment").performClick()
        composeRule.waitForIdle()
        assertEquals(1, prompts)
        assertTrue(GoogleReviewRequest.coveredByPayments(total, job.pricing.payments))
    }

    @Test fun unpaidJobHidesTheReviewAction() {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = true) {
                GoogleReviewRequestCard(
                    offer = false,
                    sentAt = null,
                    customerName = "Pam Johnston",
                    phone = "",
                    email = "",
                    reviewLink = link,
                    template = "",
                    onOpenSettings = {},
                    onMarkSent = {}
                )
            }
        }
        composeRule.onNodeWithTag("google-review-request").assertDoesNotExist()
    }

    private fun save(name: String) {
        val bmp = runCatching {
            composeRule.runOnIdle {
                composeRule.activity.window.decorView.findReviewCompose()?.takeIf { it.width > 0 && it.height > 0 }?.drawToBitmap()
            }
        }.getOrNull() ?: return
        val dir = File("/opt/cursor/artifacts")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findReviewCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).findReviewCompose()?.let { return it }
    }
    return null
}
