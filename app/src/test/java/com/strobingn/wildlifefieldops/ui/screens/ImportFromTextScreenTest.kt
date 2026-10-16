package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.strobingn.wildlifefieldops.data.remote.CustomerTextInbox
import com.strobingn.wildlifefieldops.data.remote.SharedText
import com.strobingn.wildlifefieldops.data.remote.TextImportEntry
import com.strobingn.wildlifefieldops.data.remote.TextImportTarget
import com.strobingn.wildlifefieldops.data.remote.TextMessageImport
import com.strobingn.wildlifefieldops.data.remote.TextShareInbox
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h900dp-xhdpi")
class ImportFromTextScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sample =
        "Hi this is Maria Lopez, 22 Oak St Newburgh NY 12550, 845-555-0199, maria@example.com, raccoon in the attic"

    @After
    fun clearInboxes() {
        TextShareInbox.peek()?.let { TextShareInbox.consume(it.id) }
        CustomerTextInbox.peek()?.let { CustomerTextInbox.consume(it.id) }
    }

    private fun setClipboard(text: String?) {
        val clipboard = composeRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (text == null) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        }
    }

    private fun show(target: TextImportTarget, onRead: (SharedText) -> Unit) {
        composeRule.setContent {
            WildlifeFieldOpsTheme {
                ImportFromTextScreen(target = target, onBack = {}, onRead = onRead)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun clipboardTextPrefillsTheBox() {
        setClipboard(sample)
        var read: SharedText? = null
        show(TextImportTarget.JOB) { read = it }
        composeRule.onNodeWithText(TextImportEntry.ACTION_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag("import-text-box").assertTextContains(sample)
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(sample, read?.body)
        assertNull(read?.senderPhone)
    }

    @Test
    fun emptyClipboardStartsBlankAndPasteFills() {
        setClipboard(null)
        var read: SharedText? = null
        show(TextImportTarget.JOB) { read = it }
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).assertIsNotEnabled()
        setClipboard(sample)
        composeRule.onNodeWithText(TextImportEntry.PASTE_LABEL).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("import-text-box").assertTextContains(sample)
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(sample, read?.body)
    }

    @Test
    fun typedTextIsKeptWhenPasting() {
        setClipboard(null)
        var read: SharedText? = null
        show(TextImportTarget.CUSTOMER) { read = it }
        composeRule.onNodeWithTag("import-text-box").performTextInput("Gate code 4411")
        setClipboard(sample)
        composeRule.onNodeWithText(TextImportEntry.PASTE_LABEL).performClick()
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).performClick()
        composeRule.waitForIdle()
        val body = read?.body.orEmpty()
        assertTrue(body, body.startsWith("Gate code 4411"))
        assertTrue(body, body.endsWith(sample))
    }

    @Test
    fun readTextHandsOffToTheSharedParser() {
        setClipboard(sample)
        show(TextImportTarget.JOB) { shared -> TextImportEntry.deliver(shared, TextImportTarget.JOB) }
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).performClick()
        composeRule.waitForIdle()
        val pending = TextShareInbox.peek() ?: error("nothing handed to the New Job form")
        assertNull(CustomerTextInbox.peek())
        val fields = TextMessageImport.parse(pending.body, pending.senderPhone)
        assertEquals("Maria Lopez", fields.name)
        assertEquals("maria@example.com", fields.email)
        assertEquals("12550", fields.zip)
    }

    @Test
    fun customerTargetHandsOffToCustomerForm() {
        setClipboard(sample)
        show(TextImportTarget.CUSTOMER) { shared -> TextImportEntry.deliver(shared, TextImportTarget.CUSTOMER) }
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).performClick()
        composeRule.waitForIdle()
        assertEquals(sample, CustomerTextInbox.peek()?.body)
        assertNull(TextShareInbox.peek())
    }
}
