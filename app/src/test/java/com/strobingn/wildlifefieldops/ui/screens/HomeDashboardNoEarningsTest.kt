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
        assertEquals(1, drawer.count { it == Screen.EarningsTax })
        assertFalse(Screen.bottomNavItems.contains(Screen.EarningsTax))
        assertEquals(
            listOf(Screen.Dashboard, Screen.JobList, Screen.InspectionList, Screen.CustomerList, Screen.More),
            Screen.bottomNavItems
        )
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
        assertFalse(home.contains("At a glance"))
        assertFalse(home.contains("Quick actions"))
        assertFalse(home.contains("Recent jobs"))
        assertTrue(home.contains("HomeShellHeader"))
        assertTrue(home.contains("Today's jobs"))
        assertTrue(home.contains("compact = true"))
        assertTrue(home.contains("HomeListBottomClearance"))
        assertTrue(home.contains("ManualJobEntry.ACTION_LABEL"))
        assertTrue(home.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(home.contains("AppUpdateHomeChip"))
        val jobs = readAppSource("ui/screens/JobListScreen.kt")
        assertTrue(jobs.contains("Recent jobs"))
        assertTrue(jobs.contains("JobFlagSummaryRow"))
        assertTrue(jobs.contains("Next steps due"))
        assertTrue(jobs.contains("Reminders"))
        val customers = readAppSource("ui/screens/CustomerListScreen.kt")
        assertTrue(customers.contains("label = \"Customers\""))
        val inspections = readAppSource("ui/screens/InspectionListScreen.kt")
        assertTrue(inspections.contains("label = \"Inspections\""))
        assertTrue(inspections.contains("label = \"Follow-ups\""))
        val more = readAppSource("ui/screens/MoreScreen.kt")
        assertTrue(more.contains("QuickActionsGrid"))
        val summaries = readAppSource("ui/components/TabSummaries.kt")
        assertTrue(summaries.contains("Quick actions"))
        assertTrue(summaries.contains("\"Scheduled\""))
        assertTrue(summaries.contains("\"In progress\""))
        assertTrue(summaries.contains("\"Completed\""))
        val traps = readAppSource("ui/screens/TrapCheckScreen.kt")
        assertTrue(traps.contains("Trap checks due"))
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
