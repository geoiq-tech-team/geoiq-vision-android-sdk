package com.geoiq.lk_vision_demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.geoiq.geoiq_android_lk_vision_bot_sdk.VisionBotSDKManager
import com.geoiq.lk_vision_demo.navigation.AppNavHost
import com.geoiq.lk_vision_demo.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required for WindowInsets.ime to reach Compose. With the default (decor fits system
        // windows) the framework resizes the window instead and reports a zero IME inset, which
        // makes Modifier.imePadding() a silent no-op.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestMissingPermissions()
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) VisionBotSDKManager.shutdown()
    }

    private fun requestMissingPermissions() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }   
        if (missing.isNotEmpty()) requestPermissionsLauncher.launch(missing.toTypedArray())
    }
}
