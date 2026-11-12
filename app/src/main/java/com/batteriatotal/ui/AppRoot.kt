package com.batteriatotal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.ui.screens.ConsumptionScreen
import com.batteriatotal.ui.screens.DashboardScreen
import com.batteriatotal.ui.screens.DataScreen
import com.batteriatotal.ui.screens.HistoryScreen
import com.batteriatotal.ui.screens.ProcessScreen
import com.batteriatotal.ui.screens.SettingsScreen
import com.batteriatotal.ui.theme.BateriaTotalTheme

private enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Resumen", Icons.Default.Dashboard),
    CONSUMPTION("Consumo", Icons.Default.PieChart),
    PROCESSES("Procesos", Icons.Default.Memory),
    HISTORY("Historial", Icons.Default.Timeline),
    DATA("Datos", Icons.Default.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BateriaTotalApp(viewModel: AppViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    BateriaTotalTheme(themeMode = themeMode) {
        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { settingsOpen = false }
            )
            return@BateriaTotalTheme
        }

        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = AppTab.entries[selectedTab].label,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshData() }) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(6.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                            }
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (AppTab.entries[selectedTab]) {
                    AppTab.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onOpenConsumption = { selectedTab = AppTab.CONSUMPTION.ordinal }
                    )
                    AppTab.CONSUMPTION -> ConsumptionScreen(viewModel = viewModel)
                    AppTab.PROCESSES -> ProcessScreen(viewModel = viewModel)
                    AppTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                    AppTab.DATA -> DataScreen(viewModel = viewModel)
                }
            }
        }
    }
}
