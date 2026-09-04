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

    val showBottomNav = currentRoute in Screen.bottomNavItems.map { it.route }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerOpen = drawerState.currentValue == DrawerValue.Open ||
        drawerState.targetValue == DrawerValue.Open

    LaunchedEffect(showBottomNav) {
        if (!showBottomNav && drawerState.isOpen) {
            drawerState.close()
        }
    }

    BackHandler(enabled = drawerOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateFromDrawer(route: String) {
        scope.launch {
            drawerState.close()
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showBottomNav,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
        drawerContent = {
            AppDrawer(
                onNavigate = { route -> navigateFromDrawer(route) },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
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
