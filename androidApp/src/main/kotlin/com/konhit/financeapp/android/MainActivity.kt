package com.konhit.financeapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.konhit.financeapp.android.ui.navigation.AppNavGraph
import com.konhit.financeapp.android.ui.navigation.Routes
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.usecase.OpenFileUseCase
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settings: SettingsRepository by inject()
    private val syncCoordinator: SyncCoordinator by inject()
    private val openFile: OpenFileUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val hasFile = settings.getLocalFilePath() != null
                    if (hasFile) {
                        try {
                            openFile()
                            startDestination = Routes.MAIN
                        } catch (e: Exception) {
                            // No local cache available and network unreachable — go to first launch
                            startDestination = Routes.FIRST_LAUNCH
                        }
                    } else {
                        startDestination = Routes.FIRST_LAUNCH
                    }
                }

                startDestination?.let { dest ->
                    AppNavGraph(startDestination = dest)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { syncCoordinator.uploadCurrent() }
    }
}
