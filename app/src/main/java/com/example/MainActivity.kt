package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.PermissionsScreen
import com.example.ui.RecordsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                // Request crucial permissions
                val permissions = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                val permissionState = rememberMultiplePermissionsState(permissions = permissions)

                // Check Accessibility
                var accessibilityGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(this, com.example.service.CallAccessibilityService::class.java)) }

                LaunchedEffect(Unit) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }
                
                val needsPermissions = !permissionState.allPermissionsGranted || !accessibilityGranted

                NavHost(
                    navController = navController,
                    startDestination = if (needsPermissions) "permissions" else "records"
                ) {
                    composable("permissions") {
                        PermissionsScreen(
                            onAllPermissionsGranted = {
                                // Recheck accessibility
                                accessibilityGranted = isAccessibilityServiceEnabled(this@MainActivity, com.example.service.CallAccessibilityService::class.java)
                                if (permissionState.allPermissionsGranted && accessibilityGranted) {
                                    navController.navigate("records") {
                                        popUpTo("permissions") { inclusive = true }
                                    }
                                } else if (!permissionState.allPermissionsGranted) {
                                    permissionState.launchMultiplePermissionRequest()
                                }
                            }
                        )
                    }
                    composable("records") {
                        RecordsScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Here we could trigger a recomposition or a state update for Accessibility checks
        // if we wanted seamless resume updates, but the user clicking the button is okay.
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = android.content.ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
