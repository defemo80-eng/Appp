package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DeiPlayAppRoot
import com.example.ui.theme.DeiPlayTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DeiPlayApplication
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(app.repository, app.playerController)
        )[MainViewModel::class.java]

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            DeiPlayTheme(oledBlack = settings.oledBlackTheme) {
                DeiPlayAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode && !isChangingConfigurations) {
            // Pause playback cleanly if app is backgrounded outside PiP
            val app = application as DeiPlayApplication
            app.playerController.pausePlayback()
        }
    }
}
