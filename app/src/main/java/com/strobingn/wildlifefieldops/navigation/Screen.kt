package com.strobingn.wildlifefieldops.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    // Bottom Nav Screens
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object JobList : Screen("jobs", "Jobs", Icons.Default.Work)
    object InspectionList : Screen("inspections", "Inspections", Icons.Default.Search)
    object Schedule : Screen("schedule", "Schedule", Icons.Default.CalendarMonth)
    object GPS : Screen("gps", "GPS", Icons.Default.LocationOn)
    object EarningsTax : Screen("earnings_tax", "Tax", Icons.Default.AccountBalance)

    // Job Screens
    object JobDetail : Screen("job_detail/{jobId}", "Job Detail") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
    /** Use path segment "new" for create; real UUID for edit (query params were flaky). */
    object JobForm : Screen("job_form/{jobId}", "Job Form") {
        fun createRoute(jobId: String? = null) = "job_form/${jobId ?: "new"}"
    }
    object JobDictate : Screen("job_dictate", "Dictate job", Icons.Default.Mic)
    object VoiceLog : Screen(
        "voice_log?jobId={jobId}&observationEventId={observationEventId}",
        "Voice Log",
        Icons.Default.Mic
    ) {
        fun createRoute(jobId: String? = null, observationEventId: String? = null): String {
            val j = jobId?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            val e = observationEventId?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            return "voice_log?jobId=$j&observationEventId=$e"
        }
    }

    // Customer Screens
    object CustomerList : Screen("customers", "Customers", Icons.Default.People)
    /** Tools that used to live in the drawer, plus Schedule and Tax. */
    object More : Screen("more", "More", Icons.Default.Menu)
    object CustomerForm : Screen("customer_form?customerId={customerId}", "Customer Form") {
        fun createRoute(customerId: String? = null) =
            if (customerId != null) "customer_form?customerId=$customerId" else "customer_form"
    }
    /** Paste or type a customer's text message, then Read text. */
    object TextImport : Screen("text_import?target={target}", "Import from text") {
        fun createRoute(target: String = "job") = "text_import?target=$target"
    }

    // Inspection Screens
    object InspectionDetail : Screen("inspection_detail/{inspectionId}", "Inspection Detail") {
        fun createRoute(inspectionId: String) = "inspection_detail/$inspectionId"
    }
    object InspectionForm : Screen(
        "inspection_form?inspectionId={inspectionId}&jobId={jobId}",
        "Inspection Form"
    ) {
        fun createRoute(inspectionId: String? = null, jobId: String? = null): String {
            val params = buildList {
                if (!inspectionId.isNullOrBlank()) add("inspectionId=$inspectionId")
                if (!jobId.isNullOrBlank()) add("jobId=$jobId")
            }
            return if (params.isEmpty()) "inspection_form" else "inspection_form?${params.joinToString("&")}"
        }
    }

    // Other Screens
    object Map : Screen("map", "Property Map", Icons.Default.Map)
    object TrapChecks : Screen("trap_checks", "Trap checks", Icons.Default.PestControl)
    object DecNwcoLog : Screen("dec_nwco_log", "DEC NWCO log", Icons.Default.Assignment)
    object SmartSearch : Screen("smart_search", "Search", Icons.Default.ManageSearch)
    object MileageLog : Screen("mileage_log", "Mileage log", Icons.Default.DirectionsCar)
    object InvoiceList : Screen("invoice_list", "Invoices", Icons.Default.ReceiptLong)
    object WarrantyList : Screen("warranty_list", "Warranties", Icons.Default.Verified)
    object DuplicateCustomers : Screen("duplicate_customers", "Duplicate customers", Icons.Default.CallMerge)
    object Invoice : Screen("invoice/{jobId}", "Invoice") {
        fun createRoute(jobId: String) = "invoice/$jobId"
    }
    object PhotoGallery : Screen("photos", "Photo Gallery", Icons.Default.PhotoCamera)
    object LiveCapture : Screen(
        "live_capture?jobId={jobId}&inspectionId={inspectionId}",
        "Live Capture",
        Icons.Default.Videocam
    ) {
        fun createRoute(jobId: String? = null, inspectionId: String? = null): String {
            val j = jobId?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            val i = inspectionId?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            return "live_capture?jobId=$j&inspectionId=$i"
        }
    }
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object SyncStatus : Screen("sync_status", "Sync status", Icons.Default.Sync)
    object AiAccuracy : Screen("ai_accuracy", "AI accuracy", Icons.Default.FactCheck)
    object AIAssistant : Screen("ai_assistant", "AI Assistant", Icons.Default.Psychology)
    object AIOperations : Screen("ai_operations", "AI Operations", Icons.Default.AutoAwesome)
    object Expense : Screen("expenses", "Expenses", Icons.Default.Receipt)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object RouteOptimizer : Screen("routes", "Routes", Icons.Default.Route)
    object TodayRoute : Screen("today_route", "Today's route", Icons.Default.Navigation)
    object CountyReports : Screen("county_reports", "County Reports", Icons.Default.Assessment)
    object Estimate : Screen("estimate/{jobId}?autoDraft={autoDraft}", "Estimate") {
        fun createRoute(jobId: String, autoDraft: Boolean = false) =
            if (autoDraft) "estimate/$jobId?autoDraft=true" else "estimate/$jobId"
    }

    companion object {
        // Lazy so nested objects are finished initializing before the lists read them.
        val bottomNavItems: List<Screen> by lazy {
            listOf(Dashboard, JobList, InspectionList, CustomerList, More)
        }
        val drawerItems: List<Screen> by lazy {
            listOf(
                Map,
                EarningsTax,
                DecNwcoLog,
                SmartSearch,
                TrapChecks,
                GPS,
                InvoiceList,
                WarrantyList,
                DuplicateCustomers,
                MileageLog,
                CountyReports,
                PhotoGallery,
                LiveCapture,
                VoiceLog,
                Expense,
                Inventory,
                TodayRoute,
                RouteOptimizer,
                AIOperations,
                AIAssistant,
                Settings
            )
        }
    }
}
