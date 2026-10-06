package com.strobingn.wildlifefieldops.navigation

/**
 * Where every existing screen lives after the 2.7 navigation redesign.
 * Tabs are the five bottom destinations. Everything else is one tap from More,
 * or one tap from the list that owns it (a job, an inspection, a customer).
 */
data class MoreDestination(
    val screen: Screen,
    val group: String,
    val label: String,
    val route: String,
    val blurb: String,
    val settingsFocus: String = ""
)

data class EmbeddedSurface(
    val name: String,
    val path: String
)

object FieldNav {
    const val MAX_TAPS_FROM_TAB = 2

    val tabs: List<Screen> = Screen.bottomNavItems

    fun openRoute(screen: Screen): String = when (screen) {
        Screen.LiveCapture -> Screen.LiveCapture.createRoute()
        Screen.VoiceLog -> Screen.VoiceLog.createRoute()
        else -> screen.route
    }

    private fun dest(
        screen: Screen,
        group: String,
        blurb: String,
        label: String = screen.title,
        settingsFocus: String = ""
    ) = MoreDestination(screen, group, label, openRoute(screen), blurb, settingsFocus)

    /**
     * Every tool that used to be a bottom tab or a drawer row, plus Backup.
     * Drawer rows stay in [Screen.drawerItems]; this list is how More shows them.
     */
    val moreDestinations: List<MoreDestination> = listOf(
        dest(Screen.Schedule, "Today", "Calendar of jobs, visits, and inspections"),
        dest(Screen.TodayRoute, "Today", "Stops in drive order for today"),
        dest(Screen.RouteOptimizer, "Today", "Plan a shorter driving order"),
        dest(Screen.TrapChecks, "Today", "Traps that need a check"),
        dest(
            Screen.EarningsTax,
            "Money",
            "Paid, invoiced, estimated, and NY sales tax",
            label = "Earnings & sales tax"
        ),
        dest(Screen.InvoiceList, "Money", "Invoices across jobs"),
        dest(Screen.MileageLog, "Money", "Miles driven"),
        dest(Screen.Expense, "Money", "Job expenses"),
        dest(Screen.WarrantyList, "Money", "Warranties on completed work"),
        dest(Screen.SmartSearch, "Records", "Jobs, customers, notes, and findings"),
        dest(Screen.DuplicateCustomers, "Records", "Find and merge duplicate customers"),
        dest(Screen.CountyReports, "Records", "County activity reports"),
        dest(Screen.DecNwcoLog, "Records", "DEC NWCO log for New York", label = "NYS DEC log"),
        dest(Screen.PhotoGallery, "Records", "Job and inspection photos"),
        dest(Screen.Map, "Field", "Property map and job pins"),
        dest(Screen.GPS, "Field", "GPS position and tools"),
        dest(Screen.LiveCapture, "Field", "Live camera capture and measure"),
        dest(Screen.VoiceLog, "Field", "Voice field log"),
        dest(Screen.Inventory, "Field", "Materials on the truck"),
        dest(Screen.AIOperations, "AI", "AI command center"),
        dest(Screen.AIAssistant, "AI", "Ask the assistant"),
        dest(Screen.AiAccuracy, "AI", "Which filled fields you change most"),
        dest(
            Screen.Settings,
            "App",
            "Export, share, import, or restore a backup",
            label = "Backup & restore",
            settingsFocus = "backup"
        ),
        dest(Screen.Settings, "App", "Theme, sync, company, license, and updates"),
        dest(Screen.SyncStatus, "App", "Last sync, last error, and what is waiting to upload")
    )

    /** Every NavHost destination, including the new More tab. */
    val graphScreens: List<Screen> = listOf(
        Screen.Dashboard,
        Screen.JobList,
        Screen.InspectionList,
        Screen.Schedule,
        Screen.GPS,
        Screen.EarningsTax,
        Screen.JobDetail,
        Screen.JobForm,
        Screen.JobDictate,
        Screen.VoiceLog,
        Screen.CustomerList,
        Screen.CustomerForm,
        Screen.InspectionDetail,
        Screen.InspectionForm,
        Screen.Map,
        Screen.TrapChecks,
        Screen.DecNwcoLog,
        Screen.SmartSearch,
        Screen.MileageLog,
        Screen.InvoiceList,
        Screen.WarrantyList,
        Screen.DuplicateCustomers,
        Screen.Invoice,
        Screen.PhotoGallery,
        Screen.LiveCapture,
        Screen.Settings,
        Screen.AIAssistant,
        Screen.AIOperations,
        Screen.Expense,
        Screen.Inventory,
        Screen.RouteOptimizer,
        Screen.TodayRoute,
        Screen.CountyReports,
        Screen.Estimate,
        Screen.More,
        Screen.SyncStatus,
        Screen.AiAccuracy
    )

    /**
     * Routes registered on main before this redesign. More is additive.
     * Kept as literals so a dropped destination fails the nav test.
     */
    val routesOnMain: List<String> = listOf(
        "dashboard",
        "jobs",
        "inspections",
        "schedule",
        "gps",
        "earnings_tax",
        "job_detail/{jobId}",
        "job_form/{jobId}",
        "job_dictate",
        "voice_log?jobId={jobId}&observationEventId={observationEventId}",
        "customers",
        "customer_form?customerId={customerId}",
        "inspection_detail/{inspectionId}",
        "inspection_form?inspectionId={inspectionId}&jobId={jobId}",
        "map",
        "trap_checks",
        "dec_nwco_log",
        "smart_search",
        "mileage_log",
        "invoice_list",
        "warranty_list",
        "duplicate_customers",
        "invoice/{jobId}",
        "photos",
        "live_capture?jobId={jobId}&inspectionId={inspectionId}",
        "settings",
        "ai_assistant",
        "ai_operations",
        "expenses",
        "inventory",
        "routes",
        "today_route",
        "county_reports",
        "estimate/{jobId}?autoDraft={autoDraft}"
    )

    fun howToReach(screen: Screen): String = when (screen) {
        Screen.Dashboard -> "Home tab"
        Screen.JobList -> "Jobs tab"
        Screen.InspectionList -> "Inspections tab"
        Screen.CustomerList -> "Customers tab"
        Screen.More -> "More tab"
        Screen.Schedule -> "More → Schedule"
        Screen.GPS -> "More → GPS"
        Screen.EarningsTax -> "More → Earnings & sales tax"
        Screen.JobDetail -> "Jobs → a job"
        Screen.JobForm -> "Home → New Job"
        Screen.JobDictate -> "Home → Dictate job"
        Screen.VoiceLog -> "More → Voice Log"
        Screen.CustomerForm -> "Customers → a customer"
        Screen.InspectionDetail -> "Inspections → an inspection"
        Screen.InspectionForm -> "Inspections → New inspection"
        Screen.Map -> "More → Property Map"
        Screen.TrapChecks -> "More → Trap checks"
        Screen.DecNwcoLog -> "More → NYS DEC log"
        Screen.SmartSearch -> "More → Search"
        Screen.MileageLog -> "More → Mileage log"
        Screen.InvoiceList -> "More → Invoices"
        Screen.WarrantyList -> "More → Warranties"
        Screen.DuplicateCustomers -> "More → Duplicate customers"
        Screen.Invoice -> "Jobs → a job → Invoice"
        Screen.PhotoGallery -> "More → Photo Gallery"
        Screen.LiveCapture -> "More → Live Capture"
        Screen.Settings -> "More → Settings"
        Screen.AIAssistant -> "More → AI Assistant"
        Screen.AIOperations -> "More → AI Operations"
        Screen.Expense -> "More → Expenses"
        Screen.Inventory -> "More → Inventory"
        Screen.RouteOptimizer -> "More → Routes"
        Screen.TodayRoute -> "More → Today's route"
        Screen.CountyReports -> "More → County Reports"
        Screen.Estimate -> "Jobs → a job → Estimate"
        Screen.SyncStatus -> "More → Sync status"
        Screen.AiAccuracy -> "More → AI accuracy"
        else -> error("No path for ${screen.route}")
    }

    fun tapsFromTab(path: String): Int = path.split(" → ").size - 1

    /** Full-screen surfaces that are not their own nav route. */
    val embeddedSurfaces: List<EmbeddedSurface> = listOf(
        EmbeddedSurface("AI and App Diagnostics", "More → Settings → AI and App Diagnostics"),
        EmbeddedSurface("Backup & restore", "More → Backup & restore"),
        EmbeddedSurface("AR measure", "More → Live Capture → Measure"),
        EmbeddedSurface("Copy from estimate", "Jobs → a job → Invoice"),
        EmbeddedSurface("App updates", "More → Settings → App updates")
    )

    val groupsInOrder: List<String> = moreDestinations.map { it.group }.distinct()
}
