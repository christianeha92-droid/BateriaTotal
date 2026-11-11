package com.batteriatotal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.BateriaTotalApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()

            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                viewModel.setUiVisible(true)
            }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                viewModel.setUiVisible(false)
            }
            LifecycleResumeEffect(Unit) {
                viewModel.onAppResumed()
                onPauseOrDispose { }
            }
            BateriaTotalApp(viewModel = viewModel)
        }
    }
}
