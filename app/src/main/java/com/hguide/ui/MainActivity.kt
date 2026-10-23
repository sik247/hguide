package com.hguide.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
        setContent { HguideTheme { AppRoot(vm) } }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Closing the app ends the trip (spec F3), which runs the recap.
        if (isFinishing) (application as HguideApp).container.onAppStop()
    }
}
