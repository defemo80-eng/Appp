package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
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

    fun isPipEligible(): Boolean {
        val state = (application as DeiPlayApplication).playerController.uiState.value
        return state.currentVideo != null && (state.isPlaying || state.isMinimized)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildDeiPlayPipParams(): PictureInPictureParams {
        val state = (application as DeiPlayApplication).playerController.uiState.value
        val isShort = state.currentVideo?.video?.isShort == true
        val rawW = if (state.videoWidth > 0 && state.videoHeight > 0) state.videoWidth else if (isShort) 9 else 16
        val rawH = if (state.videoWidth > 0 && state.videoHeight > 0) state.videoHeight else if (isShort) 16 else 9
        val ratio = rawW.toFloat() / rawH.toFloat()
        val rational = when {
            ratio > 2.35f -> Rational(235, 100)
            ratio < 0.45f -> Rational(45, 100)
            else -> Rational(rawW, rawH)
        }
        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isPipEligible())
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun updateDeiPlayPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                setPictureInPictureParams(buildDeiPlayPipParams())
            }
        }
    }

    fun tryEnterDeiPlayPip(forceWhenVideoLoaded: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val state = (application as DeiPlayApplication).playerController.uiState.value
        val allowed = if (forceWhenVideoLoaded) state.currentVideo != null else isPipEligible()
        if (!allowed) return false
        return runCatching {
            enterPictureInPictureMode(buildDeiPlayPipParams())
        }.getOrDefault(false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode) {
            tryEnterDeiPlayPip(forceWhenVideoLoaded = false)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        updateDeiPlayPipParams()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode && !isChangingConfigurations) {
            val app = application as DeiPlayApplication
            app.playerController.pausePlayback()
        }
    }
}
