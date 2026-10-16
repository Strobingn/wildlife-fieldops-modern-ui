package com.strobingn.wildlifefieldops.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobAiSummaryTest {

    @Test
    fun onDeviceAndCloudSummariesAreGenerated() {
        assertTrue(JobAiSummary.isGenerated("📱 On-device:\n\n- Raccoon in attic"))
        assertTrue(JobAiSummary.isGenerated("☁️ Cloud:\n\n- Raccoon in attic"))
    }

    @Test
    fun errorAndHintTextIsNotASummary() {
        assertFalse(JobAiSummary.isGenerated("Edge function returned 500\n\nOn-device LLM is not ready."))
        assertFalse(JobAiSummary.isGenerated("On-device LLM is not ready. Open AI Assistant.\n\nJob title: Bat"))
        assertFalse(JobAiSummary.isGenerated(""))
    }

    @Test
    fun markerWithNoBodyIsNotASummary() {
        assertFalse(JobAiSummary.isGenerated("📱 On-device:"))
    }

    @Test
    fun failureMessageUsesFirstNonBlankLine() {
        val msg = JobAiSummary.failureMessage("\n  Network timeout \n\nOn-device LLM is not ready.")
        assertTrue(msg.contains("Network timeout"))
        assertFalse(msg.contains("On-device"))
    }

    @Test
    fun failureMessageHandlesBlankInput() {
        assertTrue(JobAiSummary.failureMessage("   ").contains("no model answered"))
    }
}
