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

    // Job Screens
    object JobDetail : Screen("job_detail/{jobId}", "Job Detail") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
    object JobForm : Screen("job_form?jobId={jobId}", "Job Form") {
        fun createRoute(jobId: String? = null) =
            if (jobId != null) "job_form?jobId=$jobId" else "job_form"
    }

    // Customer Screens
    object CustomerList : Screen("customers", "Customers", Icons.Default.People)
    object CustomerForm : Screen("customer_form?customerId={customerId}", "Customer Form") {
        fun createRoute(customerId: String? = null) =
            if (customerId != null) "customer_form?customerId=$customerId" else "customer_form"
    }

    // Inspection Screens
    object InspectionDetail : Screen("inspection_detail/{inspectionId}", "Inspection Detail") {
        fun createRoute(inspectionId: String) = "inspection_detail/$inspectionId"
    }
    object InspectionForm : Screen("inspection_form?inspectionId={inspectionId}", "Inspection Form") {
        fun createRoute(inspectionId: String? = null) =
            if (inspectionId != null) "inspection_form?inspectionId=$inspectionId" else "inspection_form"
    }

    // Other Screens
    object Map : Screen("map", "Property Map", Icons.Default.Map)
    object Invoice : Screen("invoice/{jobId}", "Invoice") {
        fun createRoute(jobId: String) = "invoice/$jobId"
    }
    object PhotoGallery : Screen("photos", "Photo Gallery", Icons.Default.PhotoCamera)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AIAssistant : Screen("ai_assistant", "AI Assistant", Icons.Default.Psychology)
    object Expense : Screen("expenses", "Expenses", Icons.Default.Receipt)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object RouteOptimizer : Screen("routes", "Routes", Icons.Default.Route)
    object Estimate : Screen("estimate/{jobId}", "Estimate") {
        fun createRoute(jobId: String) = "estimate/$jobId"
    }

    // AR / v2 features (ported)
    object ARMeasure : Screen("ar_measure/{jobId}", "AR Measure") {
        fun createRoute(jobId: String) = "ar_measure/$jobId"
    }

    companion object {
        val bottomNavItems = listOf(Dashboard, JobList, InspectionList, Schedule, GPS)
        val drawerItems = listOf(
            CustomerList,
            Map,
            PhotoGallery,
            Expense,
            Inventory,
            RouteOptimizer,
            AIAssistant,
            Settings
        )
    }
}
