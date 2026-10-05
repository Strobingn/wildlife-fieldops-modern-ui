package com.strobingn.wildlifefieldops

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.repository.syncFailureDetail
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.navigation.MoreDestination
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.components.BrandMark
import com.strobingn.wildlifefieldops.ui.screens.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, granted) ->
            android.util.Log.d("Permissions", "$permission: $granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            setContent {
                val settingsVm: SettingsViewModel = hiltViewModel()
                val themePreference by settingsVm.themePreference.collectAsState(
                    initial = ThemePreference.SYSTEM
                )
                val systemDark = isSystemInDarkTheme()
                val darkTheme = themePreference.resolveIsDark(systemDark)
                SideEffect { ThemeMode.isDark = darkTheme }
                WildlifeFieldOpsTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(800)
                            requestLaunchPermissions()
                        }
                        WildlifeFieldOpsNavHost()
                        AppUpdateHost()
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Fatal setContent failure", t)
            throw t
        }
    }

    private fun requestLaunchPermissions() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isEmpty()) return
        try {
            permissionLauncher.launch(permissions.toTypedArray())
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Permission request failed", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WildlifeFieldOpsNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val tabRoutes = Screen.bottomNavItems.map { it.route }
    val showBottomNav = currentRoute in tabRoutes

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(Screen.Dashboard.route) { inclusive = false; saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openTool(dest: MoreDestination) {
        navController.navigate(dest.route) { launchSingleTop = true }
        if (dest.screen == Screen.Settings) {
            navController.getBackStackEntry(Screen.Settings.route)
                .savedStateHandle["settingsFocus"] = dest.settingsFocus
        }
    }

    Scaffold(
        topBar = {},
        bottomBar = {
            if (showBottomNav) {
                Column {
                    AppUpdateBannerBar()
                    if (showsFloatingSync(currentRoute)) {
                        AutoSyncStatusBar()
                    }
                    ModernBottomBar(
                        currentRoute = currentRoute ?: Screen.Dashboard.route,
                        onNavigate = { route -> navigateTab(route) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(padding),
            onOpenMore = { navigateTab(Screen.More.route) },
            onOpenTool = { dest -> openTool(dest) }
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onOpenMore: () -> Unit = {},
    onOpenTool: (MoreDestination) -> Unit = {}
) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = modifier) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToJobs = { navController.navigate(Screen.JobList.route) },
                onNavigateToInspections = { navController.navigate(Screen.InspectionList.route) },
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(ManualJobEntry.createRoute()) },
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToRoutes = { navController.navigate(Screen.RouteOptimizer.route) },
                onNavigateToCountyReports = { navController.navigate(Screen.CountyReports.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAI = { navController.navigate(Screen.AIAssistant.route) },
                onNavigateToTrapChecks = { navController.navigate(Screen.TrapChecks.route) },
                onNavigateToDictate = { navController.navigate(VoiceJobEntry.createRoute()) },
                onNavigateToTodayRoute = { navController.navigate(Screen.TodayRoute.route) },
                onOpenDrawer = onOpenMore
            )
        }
        composable(Screen.JobList.route) {
            JobListScreen(
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(ManualJobEntry.createRoute()) },
                onNavigateToDictate = { navController.navigate(VoiceJobEntry.createRoute()) },
                onBack = { navController.popBackStack() },
                showBack = false
            )
        }
        composable(
            route = Screen.JobDetail.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "fieldops://report/{jobId}" })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            JobDetailScreen(
                jobId = jobId,
                onNavigateToEdit = { id -> navController.navigate(Screen.JobForm.createRoute(id)) },
                onNavigateToInvoice = { navController.navigate(Screen.Invoice.createRoute(jobId)) },
                onNavigateToEstimate = { navController.navigate(Screen.Estimate.createRoute(jobId)) },
                onNavigateToInspectionForm = { jid -> navController.navigate(Screen.InspectionForm.createRoute(jobId = jid)) },
                onNavigateToLiveCapture = { jid -> navController.navigate(Screen.LiveCapture.createRoute(jobId = jid)) },
                onNavigateToVoiceLog = { jid -> navController.navigate(Screen.VoiceLog.createRoute(jobId = jid)) },
                onNavigateToTrapChecks = { navController.navigate(Screen.TrapChecks.route) },
                onNavigateToInspection = { iid -> navController.navigate(Screen.InspectionDetail.createRoute(iid)) },
                onNavigateToJob = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToTodayRoute = { navController.navigate(Screen.TodayRoute.route) },
                onNavigateToPhotos = { navController.navigate(Screen.PhotoGallery.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = Screen.JobForm.route, arguments = listOf(navArgument("jobId") { type = NavType.StringType })) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("jobId")
            val jobId = rawId?.takeUnless { it.isBlank() || it == "new" }
            JobFormScreen(jobId = jobId, onBack = { navController.popBackStack() })
        }
        composable(Screen.JobDictate.route) {
            JobDictateScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    navController.navigate(Screen.JobList.route) {
                        popUpTo(Screen.JobDictate.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onTypeManually = {
                    navController.navigate(VoiceJobEntry.manualFallbackRoute()) {
                        popUpTo(Screen.JobDictate.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.VoiceLog.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType; nullable = true; defaultValue = "" },
                navArgument("observationEventId") { type = NavType.StringType; nullable = true; defaultValue = "" }
            )
        ) { backStackEntry ->
            val voiceJobId = backStackEntry.arguments?.getString("jobId")
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            val voiceEventId = backStackEntry.arguments?.getString("observationEventId")
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            VoiceFirstLogScreen(
                jobId = voiceJobId,
                observationEventId = voiceEventId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.InspectionList.route) {
            InspectionListScreen(
                onNavigateToInspectionDetail = { id -> navController.navigate(Screen.InspectionDetail.createRoute(id)) },
                onNavigateToInspectionForm = { navController.navigate(Screen.InspectionForm.createRoute()) },
                onBack = { navController.popBackStack() },
                showBack = false
            )
        }
        composable(route = Screen.InspectionDetail.route, arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })) { backStackEntry ->
            InspectionFormScreen(
                inspectionId = backStackEntry.arguments?.getString("inspectionId"),
                onBack = { navController.popBackStack() },
                onNavigateToEstimate = { jid ->
                    navController.navigate(Screen.Estimate.createRoute(jid, autoDraft = true))
                },
                onNavigateToJob = { jid -> navController.navigate(Screen.JobDetail.createRoute(jid)) }
            )
        }
        composable(
            route = Screen.InspectionForm.route,
            arguments = listOf(
                navArgument("inspectionId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("jobId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            InspectionFormScreen(
                inspectionId = backStackEntry.arguments?.getString("inspectionId"),
                prefilledJobId = backStackEntry.arguments?.getString("jobId").orEmpty(),
                onBack = { navController.popBackStack() },
                onNavigateToEstimate = { jid ->
                    navController.navigate(Screen.Estimate.createRoute(jid, autoDraft = true))
                },
                onNavigateToJob = { jid -> navController.navigate(Screen.JobDetail.createRoute(jid)) }
            )
        }
        composable(Screen.Schedule.route) {
            ScheduleScreen(
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(ManualJobEntry.createRoute()) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.GPS.route) {
            GPSScreen(onNavigateToMap = { navController.navigate(Screen.Map.route) }, onBack = { navController.popBackStack() })
        }
        composable(Screen.CustomerList.route) {
            CustomerListScreen(
                onNavigateToCustomerForm = { id -> navController.navigate(Screen.CustomerForm.createRoute(id)) },
                onBack = { navController.popBackStack() },
                showBack = false
            )
        }
        composable(route = Screen.CustomerForm.route, arguments = listOf(navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStackEntry ->
            CustomerFormScreen(customerId = backStackEntry.arguments?.getString("customerId"), onBack = { navController.popBackStack() })
        }
        composable(Screen.Map.route) {
            MapScreen(onBack = { navController.popBackStack() }, onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) })
        }
        composable(Screen.SmartSearch.route) {
            SmartSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onOpenInspection = { id -> navController.navigate(Screen.InspectionDetail.createRoute(id)) }
            )
        }
        composable(Screen.TrapChecks.route) {
            TrapCheckScreen(
                onBack = { navController.popBackStack() },
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onOpenNwcoLog = { navController.navigate(Screen.DecNwcoLog.route) }
            )
        }
        composable(Screen.EarningsTax.route) {
            EarningsTaxScreen(
                onBack = { navController.popBackStack() },
                showBack = true,
                onNavigateToInvoices = { navController.navigate(Screen.InvoiceList.route) },
                onNavigateToMileage = { navController.navigate(Screen.MileageLog.route) }
            )
        }
        composable(Screen.DecNwcoLog.route) {
            DecNwcoLogScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.MileageLog.route) {
            MileageLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.InvoiceList.route) {
            InvoiceListScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) }
            )
        }
        composable(Screen.WarrantyList.route) {
            WarrantyListScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) }
            )
        }
        composable(Screen.DuplicateCustomers.route) {
            DuplicateCustomerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.CountyReports.route) {
            CountyReportScreen(onBack = { navController.popBackStack() })
        }
        composable(route = Screen.Invoice.route, arguments = listOf(navArgument("jobId") { type = NavType.StringType })) { backStackEntry ->
            InvoiceScreen(jobId = backStackEntry.arguments?.getString("jobId") ?: "", onBack = { navController.popBackStack() })
        }
        composable(Screen.PhotoGallery.route) {
            PhotoGalleryScreen(onBack = { navController.popBackStack() }, viewModel = hiltViewModel())
        }
        composable(
            route = Screen.LiveCapture.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType; defaultValue = "" },
                navArgument("inspectionId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId")
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            val inspectionId = backStackEntry.arguments?.getString("inspectionId")
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            LiveCaptureScreen(
                onBack = { navController.popBackStack() },
                jobId = jobId,
                inspectionId = inspectionId
            )
        }
        composable(Screen.More.route) {
            val sync = rememberSyncSnapshot()
            MoreScreen(
                onOpen = onOpenTool,
                header = { MoreShellHeader(sync) },
                primaryActions = {
                    MoreShellActions(
                        onNewJob = { navController.navigate(ManualJobEntry.createRoute()) },
                        onDictate = { navController.navigate(VoiceJobEntry.createRoute()) }
                    )
                },
                onNewJob = { navController.navigate(ManualJobEntry.createRoute()) },
                onDictate = { navController.navigate(VoiceJobEntry.createRoute()) },
                onSchedule = { navController.navigate(Screen.Schedule.route) },
                onMap = { navController.navigate(Screen.Map.route) },
                onInspect = { navController.navigate(Screen.InspectionList.route) },
                onRoutes = { navController.navigate(Screen.RouteOptimizer.route) },
                onReports = { navController.navigate(Screen.CountyReports.route) }
            )
        }
        composable(Screen.Settings.route) { entry ->
            val focus by entry.savedStateHandle.getStateFlow("settingsFocus", "").collectAsState()
            SettingsScreen(
                onBack = { navController.popBackStack() },
                focusBackup = focus == "backup"
            )
        }
        composable(Screen.AIAssistant.route) {
            AIAssistantScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AIOperations.route) {
            AIOperationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Expense.route) {
            ExpenseScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Inventory.route) {
            InventoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RouteOptimizer.route) {
            RouteOptimizerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.TodayRoute.route) {
            TodayRouteScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.Estimate.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("autoDraft") { type = NavType.StringType; nullable = true; defaultValue = "false" }
            )
        ) { backStackEntry ->
            EstimateScreen(
                jobId = backStackEntry.arguments?.getString("jobId") ?: "",
                autoDraft = backStackEntry.arguments?.getString("autoDraft") == "true",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** Home and More draw sync in the header chip. Other tabs keep the line above the bar. */
internal fun showsFloatingSync(route: String?): Boolean =
    route != Screen.More.route && route != Screen.Dashboard.route

data class SyncSnapshot(val label: String, val color: Color)

internal fun syncSnapshot(
    isSyncing: Boolean,
    failed: Boolean,
    pending: Int,
    failureDetail: String?
): SyncSnapshot {
    val label = when {
        isSyncing -> "Syncing…"
        failed -> "Sync failed" + (failureDetail?.let { " — $it" } ?: "")
        pending > 0 -> "Pending sync · $pending"
        else -> "Synced"
    }
    val color = when {
        isSyncing -> TextSecondary
        failed -> ErrorRed
        pending > 0 -> TextTertiary
        else -> TextSecondary
    }
    return SyncSnapshot(label, color)
}

@Composable
internal fun SyncStatusLine(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
internal fun rememberSyncSnapshot(viewModel: SettingsViewModel = hiltViewModel()): SyncSnapshot {
    val backlog by viewModel.backlog.collectAsState()
    val lastOk by viewModel.lastSyncOk.collectAsState()
    val lastMessage by viewModel.lastSyncMessage.collectAsState()
    val lastAt by viewModel.lastSyncAt.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    LaunchedEffect(lastAt) { viewModel.refreshBacklog() }
    val pending = backlog?.pendingTotal ?: 0
    val failed = backlog?.hasFailures == true || lastOk == false
    return syncSnapshot(
        isSyncing = isSyncing,
        failed = failed,
        pending = pending,
        failureDetail = syncFailureDetail(
            backlogLine = backlog?.recentFailures?.firstOrNull(),
            lastOk = lastOk,
            lastMessage = lastMessage
        )
    )
}


@Composable
private fun AutoSyncStatusBar() {
    val snapshot = rememberSyncSnapshot()
    SyncStatusLine(text = snapshot.label, color = snapshot.color)
}

@Composable
internal fun ModernBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = if (ThemeMode.isDark) {
            MaterialTheme.colorScheme.surfaceDim
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                label = {
                    Text(screen.title, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                },
                selected = selected,
                onClick = { onNavigate(screen.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = if (ThemeMode.isDark) {
                        NavIndicator
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    },
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
internal fun HomeShellHeader(
    sync: SyncSnapshot,
    greeting: String,
    todayLabel: String,
    onOpenDrawer: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShapes.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            BrandMark(size = 40)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Wildlife Whisperer",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$greeting · $todayLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "FieldOps · Cornwall, NY",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SyncStatusChip(label = sync.label, color = sync.color)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onOpenAssistant) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentPurple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = "AI Assistant",
                            tint = AccentPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun MoreShellHeader(sync: SyncSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShapes.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(size = 40)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Wildlife Whisperer",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "FieldOps · Cornwall, NY",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SyncStatusChip(label = sync.label, color = sync.color)
                }
            }
        }
    }
}

@Composable
internal fun SyncStatusChip(label: String, color: Color) {
    Surface(
        shape = FieldShapes.chip,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun MoreShellActions(onNewJob: () -> Unit, onDictate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onNewJob,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = FieldShapes.button,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = ManualJobEntry.ACTION_LABEL)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                ManualJobEntry.ACTION_LABEL,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Button(
            onClick = onDictate,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = FieldShapes.button,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Default.Mic, contentDescription = VoiceJobEntry.ACTION_LABEL)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                VoiceJobEntry.ACTION_LABEL,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
