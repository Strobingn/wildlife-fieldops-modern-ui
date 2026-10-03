package com.strobingn.wildlifefieldops.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FieldNavTest {

    @Test
    fun bottomNavIsTheFiveTabs() {
        assertEquals(
            listOf("Home", "Jobs", "Inspections", "Customers", "More"),
            Screen.bottomNavItems.map { it.title }
        )
        assertTrue(Screen.bottomNavItems.size <= 5)
        assertEquals(Screen.bottomNavItems, FieldNav.tabs)
    }

    @Test
    fun everyMainRouteIsStillRegistered() {
        val activity = readAppSource("MainActivity.kt")
        val registered = FieldNav.graphScreens.map { it.route }.toSet()
        FieldNav.routesOnMain.forEach { route ->
            assertTrue("missing route $route", registered.contains(route))
        }
        FieldNav.graphScreens.forEach { screen ->
            assertTrue(screen.route, activity.contains("Screen.${screen::class.simpleName}"))
        }
        assertTrue(activity.contains("composable(Screen.More.route)"))
    }

    @Test
    fun everyScreenIsAtMostTwoTapsFromATab() {
        FieldNav.graphScreens.forEach { screen ->
            val path = FieldNav.howToReach(screen)
            val taps = FieldNav.tapsFromTab(path)
            assertTrue("$path ($taps)", taps <= FieldNav.MAX_TAPS_FROM_TAB)
        }
        FieldNav.embeddedSurfaces.forEach { surface ->
            val taps = FieldNav.tapsFromTab(surface.path)
            assertTrue(surface.path, taps <= FieldNav.MAX_TAPS_FROM_TAB)
        }
    }

    @Test
    fun moreKeepsEveryFormerDrawerTool() {
        Screen.drawerItems.forEach { screen ->
            val count = FieldNav.moreDestinations.count { it.screen == screen }
            if (screen == Screen.Settings) assertEquals(2, count) else assertEquals(screen.route, 1, count)
        }
        assertTrue(FieldNav.moreDestinations.any { it.screen == Screen.Schedule })
        assertTrue(FieldNav.moreDestinations.any { it.label == "Backup & restore" })
        assertTrue(FieldNav.moreDestinations.any { it.label == "Earnings & sales tax" })
        assertTrue(FieldNav.moreDestinations.any { it.screen == Screen.SmartSearch })
    }

    @Test
    fun homeAndJobsExposeDictateAndNewJob() {
        val home = readAppSource("ui/screens/DashboardScreen.kt")
        val jobs = readAppSource("ui/screens/JobListScreen.kt")
        assertTrue(home.contains("ManualJobEntry.ACTION_LABEL"))
        assertTrue(home.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(home.contains("height(56.dp)"))
        assertFalseMoney(home)
        assertTrue(jobs.contains("ManualJobEntry.ACTION_LABEL"))
        assertTrue(jobs.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(jobs.contains("height(56.dp)"))
    }

    private fun assertFalseMoney(home: String) {
        listOf("Earnings", "earningsToday", "totalRevenue", "AttachMoney", "valuePrefix = \"$\"").forEach {
            assertTrue(it, !home.contains(it))
        }
    }

    private fun readAppSource(relativeUnderJava: String): String {
        val suffix = "src/main/java/com/strobingn/wildlifefieldops/$relativeUnderJava"
        val file = listOf(File(suffix), File("app/$suffix"), File("../$suffix")).firstOrNull { it.isFile }
            ?: error("Missing $suffix")
        return file.readText()
    }
}
