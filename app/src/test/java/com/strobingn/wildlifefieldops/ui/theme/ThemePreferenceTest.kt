package com.strobingn.wildlifefieldops.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun defaultAndStorageRoundTrip() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage(" "))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage("nope"))
        ThemePreference.entries.forEach { pref ->
            assertEquals(pref, ThemePreference.fromStorage(pref.storageValue))
            assertEquals(pref, ThemePreference.fromStorage(pref.storageValue.uppercase()))
        }
    }

    @Test
    fun systemFollowsDevice() {
        assertTrue(ThemePreference.SYSTEM.resolveIsDark(isSystemDark = true))
        assertTrue(!ThemePreference.SYSTEM.resolveIsDark(isSystemDark = false))
        assertTrue(!ThemePreference.LIGHT.resolveIsDark(isSystemDark = true))
        assertTrue(ThemePreference.DARK.resolveIsDark(isSystemDark = false))
    }

    @Test
    fun legacyBooleanMigratesOnlyWhenThemeModeMissing() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromPersisted(null, null))
        assertEquals(ThemePreference.DARK, ThemePreference.fromPersisted(null, true))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromPersisted(null, false))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromPersisted("system", true))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromPersisted("light", true))
        assertEquals(ThemePreference.DARK, ThemePreference.fromPersisted("dark", false))
    }
}
