package com.strobingn.wildlifefieldops.ui.screens

import com.strobingn.wildlifefieldops.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sir only wants money on the Tax tab. Home must not render earnings,
 * paid/invoiced lines, or a Revenue dollar pill.
 */
class HomeDashboardNoEarningsTest {

    @Test
    fun drawerKeepsSearchWarrantiesAndDuplicatesOnce() {
        val drawer = Screen.drawerItems
        assertEquals(1, drawer.count { it == Screen.SmartSearch })
        assertEquals(1, drawer.count { it == Screen.WarrantyList })
        assertEquals(1, drawer.count { it == Screen.DuplicateCustomers })
        assertEquals(1, drawer.count { it == Screen.InvoiceList })
        assertEquals(1, drawer.count { it == Screen.MileageLog })
        assertTrue(Screen.bottomNavItems.contains(Screen.EarningsTax))
    }

    @Test
    fun homeSourceHasNoEarningsOrMoneyFigures() {
        val home = readAppSource("ui/screens/DashboardScreen.kt")
        val banned = listOf(
            "SectionHeader(title = \"Earnings\")",
            "Open Earnings & sales tax",
            "earningsToday",
            "earningsWeek",
            "onNavigateToEarnings",
            "onNavigateToInvoices",
            "onNavigateToMileage",
            "onNavigateToWarranties",
            "onNavigateToDuplicates",
            "onNavigateToSearch",
            "MoneyFieldOpsViewModel",
            "CustomerFieldOpsViewModel",
            "title = \"Revenue\"",
            "valuePrefix = \"$\"",
            "AttachMoney",
            "totalRevenue",
            "Today  paid",
            "This week  paid"
        )
        banned.forEach { needle ->
            assertFalse(needle, home.contains(needle))
        }
        assertTrue(home.contains("At a glance"))
        assertTrue(home.contains("Quick actions"))
        assertTrue(home.contains("ManualJobEntry.ACTION_LABEL"))
    }

    @Test
    fun dashboardViewModelDropsUnusedRevenueState() {
        val vm = readAppSource("ui/viewmodel/DashboardViewModel.kt")
        assertFalse(vm.contains("totalRevenue"))
        assertFalse(vm.contains("actualCost"))
        assertTrue(vm.contains("data class DashboardStats"))
        assertTrue(vm.contains("todayJobs"))
    }

    @Test
    fun earningsScreenOwnsInvoiceAndMileageLinks() {
        val tax = readAppSource("ui/screens/EarningsTaxScreen.kt")
        assertTrue(tax.contains("onNavigateToInvoices"))
        assertTrue(tax.contains("onNavigateToMileage"))
        assertTrue(tax.contains("Text(\"Invoices\")"))
        assertTrue(tax.contains("Text(\"Mileage\")"))
    }

    private fun readAppSource(relativeUnderJava: String): String {
        val suffix = "src/main/java/com/strobingn/wildlifefieldops/$relativeUnderJava"
        val candidates = listOf(
            File(suffix),
            File("app/$suffix"),
            File("../$suffix")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing $suffix (cwd=${File(".").canonicalPath})")
        return file.readText()
    }
}
