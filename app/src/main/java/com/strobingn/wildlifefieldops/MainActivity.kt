package com.strobingn.wildlifefieldops

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.ui.components.BrandMark
import com.strobingn.wildlifefieldops.ui.screens.*
import com.strobingn.wildlifefieldops.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        super.onCreate(savedInstanceState)
        try {
            setContent {
                WildlifeFieldOpsTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Show UI first. Ask for location only after a short delay
                        // (Samsung One UI / Android 15 can be strict about permission storms at cold start).
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(800)
                            requestLaunchPermissions()
                        }
                        WildlifeFieldOpsNavHost()
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Fatal setContent failure", t)
            throw t
        }
    }

    /**
     * Only permissions needed to land on Home without scaring Android 14/15 privacy checks.
     * Camera / media / notifications are requested later from the screens that need them.
     */
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

    // Show bottom nav only on main screens
    val showBottomNav = currentRoute in Screen.bottomNavItems.map { it.route }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerOpen = drawerState.currentValue == DrawerValue.Open ||
        drawerState.targetValue == DrawerValue.Open

    // Always close the drawer when leaving main tabs — prevents stuck scrim
    // if navigation runs before the close animation finishes.
    LaunchedEffect(showBottomNav) {
        if (!showBottomNav && drawerState.isOpen) {
            drawerState.close()
        }
    }

    // System back closes the drawer first instead of trapping the user under the scrim.
    BackHandler(enabled = drawerOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateFromDrawer(route: String) {
        scope.launch {
            // Wait for close so ModalNavigationDrawer clears the scrim before route change.
            drawerState.close()
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Allow open + close gestures on main tabs (not only while already open).
        gesturesEnabled = showBottomNav,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
        drawerContent = {
            // Always compose drawer content — an empty drawerContent while Open
            // was leaving a permanent dim overlay with no sheet to dismiss.
            AppDrawer(
                onNavigate = { route -> navigateFromDrawer(route) },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            // Screens own their headers — shell only provides chrome (drawer + bottom bar)
            // so we avoid double top bars on Home/Jobs/etc.
            topBar = {},
            bottomBar = {
                if (showBottomNav) {
                    ModernBottomBar(
                        currentRoute = currentRoute ?: Screen.Dashboard.route,
                        onNavigate = { route ->
                            scope.launch {
                                if (drawerState.isOpen) drawerState.close()
                                navController.navigate(route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(padding),
                onOpenDrawer = {
                    scope.launch {
                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                    }
                }
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToJobs = { navController.navigate(Screen.JobList.route) },
                onNavigateToInspections = { navController.navigate(Screen.InspectionList.route) },
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(Screen.JobForm.createRoute()) },
                onNavigateToCustomers = { navController.navigate(Screen.CustomerList.route) },
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToRoutes = { navController.navigate(Screen.RouteOptimizer.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAI = { navController.navigate(Screen.AIAssistant.route) },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.JobList.route) {
            JobListScreen(
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(Screen.JobForm.createRoute()) },
                onBack = { navController.popBackStack() },
                showBack = false
            )
        }

        composable(
            route = Screen.JobDetail.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            JobDetailScreen(
                jobId = jobId,
                onNavigateToEdit = { id -> navController.navigate(Screen.JobForm.createRoute(id)) },
                onNavigateToInvoice = { navController.navigate(Screen.Invoice.createRoute(jobId)) },
                onNavigateToEstimate = { navController.navigate(Screen.Estimate.createRoute(jobId)) },
                onNavigateToInspectionForm = { navController.navigate(Screen.InspectionForm.createRoute()) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.JobForm.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("jobId")
            val jobId = rawId?.takeUnless { it.isBlank() || it == "new" }
            JobFormScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.InspectionList.route) {
            InspectionListScreen(
                onNavigateToInspectionDetail = { id -> navController.navigate(Screen.InspectionDetail.createRoute(id)) },
                onNavigateToInspectionForm = { navController.navigate(Screen.InspectionForm.createRoute()) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.InspectionDetail.route,
            arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val inspectionId = backStackEntry.arguments?.getString("inspectionId")
            InspectionFormScreen(
                inspectionId = inspectionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.InspectionForm.route,
            arguments = listOf(navArgument("inspectionId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val inspectionId = backStackEntry.arguments?.getString("inspectionId")
            InspectionFormScreen(
                inspectionId = inspectionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Schedule.route) {
            ScheduleScreen(
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(Screen.JobForm.createRoute()) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GPS.route) {
            GPSScreen(
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CustomerList.route) {
            CustomerListScreen(
                onNavigateToCustomerForm = { id ->
                    navController.navigate(Screen.CustomerForm.createRoute(id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CustomerForm.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")
            CustomerFormScreen(
                customerId = customerId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                onBack = { navController.popBackStack() },
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) }
            )
        }

        composable(
            route = Screen.Invoice.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            InvoiceScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PhotoGallery.route) {
            PhotoGalleryScreen(
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel()
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AIAssistant.route) {
            AIAssistantScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AIOperations.route) {
            AIOperationsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Expense.route) {
            ExpenseScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Inventory.route) {
            InventoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.RouteOptimizer.route) {
            RouteOptimizerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Estimate.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            EstimateScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun ModernBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = {
                    screen.icon?.let {
                        Icon(it, contentDescription = screen.title)
                    }
                },
                label = {
                    Text(
                        screen.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selected = selected,
                onClick = { onNavigate(screen.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun AppDrawer(
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        // Fixed width so the sheet never covers the full screen (scrim stays tappable).
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            // Brand header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(GradientStart, MaterialTheme.colorScheme.surfaceContainerLow)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 48)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Wildlife FieldOps",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Field operations center",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Text(
                            "✕",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "TOOLS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Screen.drawerItems.forEach { screen ->
                NavigationDrawerItem(
                    icon = {
                        screen.icon?.let {
                            Icon(it, contentDescription = screen.title)
                        }
                    },
                    label = {
                        Text(screen.title, style = MaterialTheme.typography.bodyLarge)
                    },
                    selected = false,
                    onClick = { onNavigate(screen.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = FieldShapes.button,
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                "v2.0.1 · Modern UI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
