package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.remote.TextImportEntry
import com.strobingn.wildlifefieldops.data.remote.TextImportTarget
import com.strobingn.wildlifefieldops.data.remote.TextMessageImport
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
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

/**
 * Light and dark renders of every Import from text entry point, the
 * Import from text screen with a sample pasted, and the New Job review
 * filled from it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1600dp-xhdpi")
class TextImportButtonScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sample =
        "Hi this is Maria Lopez, 22 Oak St Newburgh NY 12550, 845-555-0199, maria@example.com, raccoon in the attic"

    @Test fun newJobFormLight() = newJobForm(dark = false)
    @Test fun newJobFormDark() = newJobForm(dark = true)
    @Test fun customersTabLight() = customersTab(dark = false)
    @Test fun customersTabDark() = customersTab(dark = true)
    @Test fun jobsTabLight() = jobsTab(dark = false)
    @Test fun jobsTabDark() = jobsTab(dark = true)
    @Test fun importScreenLight() = importScreen(dark = false)
    @Test fun importScreenDark() = importScreen(dark = true)
    @Test fun reviewLight() = review(dark = false)
    @Test fun reviewDark() = review(dark = true)

    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    private fun newJobForm(dark: Boolean) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 900.dp)) {
                    NewJobFormShot(quote = "", snapshot = TextMessageImport.JobSnapshot())
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TextImportEntry.ACTION_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText("Paste from text").assertIsDisplayed()
        saveTextButtonShot("new-job-form-${mode(dark)}")
    }

    private fun customersTab(dark: Boolean) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 820.dp)) {
                    CustomerListScreen(
                        onNavigateToCustomerForm = {},
                        onBack = {},
                        showBack = false,
                        onImportFromText = {},
                        preview = CustomerListPreview(
                            customers = listOf(
                                Customer(id = "c1", firstName = "Pat", lastName = "Lee", phone = "845-555-0142", city = "Cornwall", state = "NY"),
                                Customer(id = "c2", firstName = "Dana", lastName = "Ruiz", phone = "845-555-0177", city = "Newburgh", state = "NY")
                            ),
                            customerCount = 2
                        )
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // The Scaffold places its FAB slot a frame later.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TextImportEntry.ACTION_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText("Add customer").assertIsDisplayed()
        saveTextButtonShot("customers-tab-${mode(dark)}")
    }

    private fun jobsTab(dark: Boolean) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 900.dp)) {
                    JobListScreen(
                        onNavigateToJobDetail = {},
                        onNavigateToJobForm = {},
                        onNavigateToDictate = {},
                        onImportFromText = {},
                        onBack = {},
                        showBack = false,
                        preview = JobListPreview(jobs = emptyList())
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TextImportEntry.ACTION_LABEL).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(ManualJobEntry.ACTION_LABEL).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Dictate job").fetchSemanticsNodes().isNotEmpty())
        saveTextButtonShot("jobs-tab-${mode(dark)}")
    }

    private fun importScreen(dark: Boolean) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 820.dp)) {
                    ImportFromTextContent(
                        target = TextImportTarget.JOB,
                        text = sample,
                        onTextChange = {},
                        onPaste = {},
                        onClear = {},
                        onReadText = {},
                        onBack = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TextImportEntry.READ_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(TextImportEntry.PASTE_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(sample).assertIsDisplayed()
        saveTextButtonShot("import-from-text-${mode(dark)}")
    }

    private fun review(dark: Boolean) {
        val fields = TextImportEntry.preview(sample)
        val filled = TextMessageImport.applyToJob(TextMessageImport.JobSnapshot(), fields)
        assertEquals("Maria Lopez", filled.customer.name)
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1500.dp)) {
                    NewJobFormShot(quote = sample, snapshot = filled)
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Maria Lopez").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("maria@example.com").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("22 Oak St").fetchSemanticsNodes().isNotEmpty())
        saveTextButtonShot("review-filled-${mode(dark)}")
    }

    private fun saveTextButtonShot(name: String) {
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findTextButtonComposeView()
                ?: error("AndroidComposeView not in the hierarchy")
            compose.drawToBitmap()
        }
        val colors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height step 16) {
            for (x in 0 until bitmap.width step 16) {
                colors += bitmap.getPixel(x, y)
            }
        }
        assertTrue("render is blank", colors.size > 4)
        val dir = File("/tmp/shots/pr-text-button")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

/**
 * Top of the New Job form built from the same pieces JobFormScreen uses
 * (JobFormIntro, JobCustomerSection). The real screen needs Hilt view models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewJobFormShot(quote: String, snapshot: TextMessageImport.JobSnapshot) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ManualJobEntry.ACTION_LABEL, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = {}) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            JobFormIntro(
                isEditing = false,
                importQuote = quote,
                onImportFromText = {},
                onPasteApply = {}
            )
            OutlinedTextField(
                value = snapshot.title,
                onValueChange = {},
                label = { Text("Job Title *") },
                colors = jobCustomerFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            JobCustomerSection(
                draft = snapshot.customer,
                onDraftChange = {},
                searchQuery = "",
                onSearchQueryChange = {},
                matches = emptyList(),
                onPickCustomer = {},
                onNewCustomer = {}
            )
            OutlinedTextField(
                value = snapshot.description,
                onValueChange = {},
                label = { Text("Problem") },
                colors = jobCustomerFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = snapshot.species,
                onValueChange = {},
                label = { Text("Confirmed species") },
                colors = jobCustomerFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

private fun View.findTextButtonComposeView(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findTextButtonComposeView()?.let { return it }
        }
    }
    return null
}
