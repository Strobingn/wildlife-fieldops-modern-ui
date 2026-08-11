package com.strobingn.wildlifefieldops

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.ui.screens.*
import com.strobingn.wildlifefieldops.ui.theme.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
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

    // PROPERLY WIRED drawer state — controls open/close programmatically
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showBottomNav && drawerState.isOpen,
        drawerContent = {
            if (showBottomNav) {
                AppDrawer(
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showBottomNav) {
                    TopAppBar(
                        title = {
                            Text(
                                text = Screen.bottomNavItems.find { it.route == currentRoute }?.title
                                    ?: "FieldOps",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onBackground
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open menu",
                                    tint = colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colorScheme.background.copy(alpha = 0.95f),
                            titleContentColor = colorScheme.onBackground,
                            navigationIconContentColor = colorScheme.primary
                        )
                    )
                }
            },
            bottomBar = {
                if (showBottomNav) {
                    BottomNavigationBar(
                        currentRoute = currentRoute ?: Screen.Dashboard.route,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        // Main screens
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToJobs = { navController.navigate(Screen.JobList.route) },
                onNavigateToInspections = { navController.navigate(Screen.InspectionList.route) },
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(Screen.JobForm.createRoute()) },
                onNavigateToCustomers = { navController.navigate(Screen.CustomerList.route) },
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAI = { navController.navigate(Screen.AIAssistant.route) }
            )
        }

        composable(Screen.JobList.route) {
            JobListScreen(
                onNavigateToJobDetail = { id -> navController.navigate(Screen.JobDetail.createRoute(id)) },
                onNavigateToJobForm = { navController.navigate(Screen.JobForm.createRoute()) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.JobDetail.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            JobDetailScreen(
                jobId = jobId,
                onNavigateToEdit = { navController.navigate(Screen.JobForm.createRoute(jobId)) },
                onNavigateToInvoice = { navController.navigate(Screen.Invoice.createRoute(jobId)) },
                onNavigateToEstimate = { navController.navigate(Screen.Estimate.createRoute(jobId)) },
                onNavigateToInspectionForm = { navController.navigate(Screen.InspectionForm.createRoute()) },
                onNavigateToARMeasure = { navController.navigate(Screen.ARMeasure.createRoute(jobId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.JobForm.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId")
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

        // Drawer screens
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

        // AR Measure (ported from fieldops-v2-features)
        composable(
            route = Screen.ARMeasure.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            ARMeasureScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        contentColor = colorScheme.onSurface
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            val iconColor = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant

            NavigationBarItem(
                icon = {
                    screen.icon?.let {
                        Icon(it, contentDescription = screen.title, tint = iconColor)
                    }
                },
                label = {
                    Text(
                        screen.title,
                        color = iconColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                selected = selected,
                onClick = { onNavigate(screen.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    selectedTextColor = colorScheme.primary,
                    indicatorColor = colorScheme.primary.copy(alpha = 0.12f),
                    unselectedIconColor = colorScheme.onSurfaceVariant,
                    unselectedTextColor = colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDrawer(
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    ModalDrawerSheet(
        drawerContainerColor = colorScheme.surface,
        drawerContentColor = colorScheme.onSurface
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // Drawer Header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Wildlife FieldOps",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Field Operations Center",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Drawer Items
        Screen.drawerItems.forEach { screen ->
            NavigationDrawerItem(
                icon = {
                    screen.icon?.let {
                        Icon(it, contentDescription = screen.title, tint = colorScheme.onSurfaceVariant)
                    }
                },
                label = {
                    Text(
                        screen.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                },
                selected = false,
                onClick = { onNavigate(screen.route) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                shape = MaterialTheme.shapes.medium,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = colorScheme.onSurface,
                    unselectedIconColor = colorScheme.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = colorScheme.outlineVariant
        )

        Text(
            "v1.4.0 — AR + ML + Modern UI",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
