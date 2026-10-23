package com.hguide.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import com.hguide.HguideApp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.Factory((application as HguideApp).container) }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                "pleos.car.permission.CAR_DRIVE", "pleos.car.permission.CAR_LIGHTS", "pleos.car.permission.FUSED_LOCATION",
            ),
        )
        // System bars match the navy UI (tablet/emulator builds; AAOS draws its own bars).
        window.statusBarColor = Hg.Bg.toArgb()
        window.navigationBarColor = Hg.Bg.toArgb()
        setContent { HguideTheme { AppRoot(vm) } }
        handleAutoplay(intent)
    }

    // Demo recording: `adb shell am start -n com.hguide.app/com.hguide.ui.MainActivity --ez autoplay true`
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAutoplay(intent)
    }

    private fun handleAutoplay(intent: Intent?) {
        if (intent?.getBooleanExtra("autoplay", false) == true) { intent.removeExtra("autoplay"); vm.startAutoplay() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Closing the app ends the trip (spec F3), which runs the recap.
        if (isFinishing) (application as HguideApp).container.onAppStop()
    }
}
