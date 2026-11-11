package com.batteriatotal.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteriatotal.Notifications
import com.batteriatotal.data.Analysis
import com.batteriatotal.data.AppConsumptionRow
import com.batteriatotal.data.BatteryReader
import com.batteriatotal.data.BatteryReading
import com.batteriatotal.data.BatteryRepository
import com.batteriatotal.data.ChargingSession
import com.batteriatotal.data.ConsumptionData
import com.batteriatotal.data.ConsumptionPeriod
import com.batteriatotal.data.DashboardData
import com.batteriatotal.data.DeviceInfo
import com.batteriatotal.data.GameSession
import com.batteriatotal.data.HardwareState
import com.batteriatotal.data.HistoryRange
import com.batteriatotal.data.Permissions
import com.batteriatotal.data.SensorInfo
import com.batteriatotal.data.SettingsStore
import com.batteriatotal.data.Sample
import com.batteriatotal.data.SystemInfoProvider
import com.batteriatotal.data.ThemeMode
import com.batteriatotal.data.UsageRepository
import com.batteriatotal.monitor.BatteryMonitorService
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LiveUiState(
    val reading: BatteryReading,
    val hardware: HardwareState = HardwareState(),
    val designCapacityMah: Int = SettingsStore.DEFAULT_CAPACITY_MAH,
    val avgDischargeMa: Float? = null,
    val avgChargeMa: Float? = null,
    val dischargeEtaMs: Long? = null,
    val chargeEtaMs: Long? = null,
    val remainingCapacityMah: Float? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = BatteryRepository.get(app)
    private val settings = SettingsStore.get(app)
    private val reader = BatteryReader(app)
    private val usageRepository = UsageRepository(app)
    private val systemInfo = SystemInfoProvider(app)

    private val _live = MutableStateFlow(
        LiveUiState(
            reading = reader.read(),
            designCapacityMah = settings.designCapacityMah
        )
    )
    val live: StateFlow<LiveUiState> = _live.asStateFlow()

    private val _dashboard = MutableStateFlow(DashboardData())
    val dashboard: StateFlow<DashboardData> = _dashboard.asStateFlow()

    private val _consumption = MutableStateFlow(ConsumptionData())
    val consumption: StateFlow<ConsumptionData> = _consumption.asStateFlow()

    private val _consumptionPeriod = MutableStateFlow(ConsumptionPeriod.TODAY)
    val consumptionPeriod: StateFlow<ConsumptionPeriod> = _consumptionPeriod.asStateFlow()

    private val _historyRange = MutableStateFlow(HistoryRange.DAY)
    val historyRange: StateFlow<HistoryRange> = _historyRange.asStateFlow()

    private val _historySamples = MutableStateFlow<List<Sample>>(emptyList())
    val historySamples: StateFlow<List<Sample>> = _historySamples.asStateFlow()

    private val _chargingSessions = MutableStateFlow<List<ChargingSession>>(emptyList())
    val chargingSessions: StateFlow<List<ChargingSession>> = _chargingSessions.asStateFlow()

    private val _usageAccess = MutableStateFlow(Permissions.hasUsageAccess(app))
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    private val _notificationPermission = MutableStateFlow(Permissions.hasNotificationPermission(app))
    val notificationPermission: StateFlow<Boolean> = _notificationPermission.asStateFlow()

    private val _ignoringBatteryOptimizations =
        MutableStateFlow(Permissions.isIgnoringBatteryOptimizations(app))
    val ignoringBatteryOptimizations: StateFlow<Boolean> = _ignoringBatteryOptimizations.asStateFlow()

    private val _monitoringEnabled = MutableStateFlow(settings.monitoringEnabled)
    val monitoringEnabled: StateFlow<Boolean> = _monitoringEnabled.asStateFlow()

    private val _gameModeEnabled = MutableStateFlow(settings.gameModeEnabled)
    val gameModeEnabled: StateFlow<Boolean> = _gameModeEnabled.asStateFlow()

    private val _gameSessions = MutableStateFlow<List<GameSession>>(emptyList())
    val gameSessions: StateFlow<List<GameSession>> = _gameSessions.asStateFlow()

    private val _samplingSeconds = MutableStateFlow(settings.samplingSeconds)
    val samplingSeconds: StateFlow<Int> = _samplingSeconds.asStateFlow()

    private val _designCapacityMah = MutableStateFlow(settings.designCapacityMah)
    val designCapacityMah: StateFlow<Int> = _designCapacityMah.asStateFlow()

    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _sensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val sensors: StateFlow<List<SensorInfo>> = _sensors.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val refreshMutex = Mutex()
    private var liveJob: Job? = null
    private var averageCacheAt = 0L
    private var averageCache: Pair<Float?, Float?> = null to null

    @Volatile
    private var uiVisible = false

    private data class CachedUsage(val at: Long, val snapshot: UsageRepository.UsageSnapshot)

    private val usageCache = java.util.concurrent.ConcurrentHashMap<String, CachedUsage>()

    init {
        viewModelScope.launch {
            repository.version.collectLatest {
                delay(1_200L)
                if (uiVisible) refreshData()
            }
        }
        loadDeviceInfo(false)
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) {
            if (liveJob?.isActive != true) {
                liveJob = viewModelScope.launch { liveLoop() }
            }
            refreshPermissions()
            refreshData()
        } else {
            liveJob?.cancel()
            liveJob = null
        }
    }

    fun onAppResumed() {
        refreshPermissions()
        ensureMonitoring()
        refreshData()
        if (_deviceInfo.value == null) loadDeviceInfo(false)
    }

    fun refreshPermissions() {
        _usageAccess.value = Permissions.hasUsageAccess(app)
        _notificationPermission.value = Permissions.hasNotificationPermission(app)
        _ignoringBatteryOptimizations.value = Permissions.isIgnoringBatteryOptimizations(app)
        usageCache.clear()
    }

    fun ensureMonitoring() {
        if (!settings.monitoringEnabled) return
        _monitoringEnabled.value = true
        startMonitorService()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        settings.monitoringEnabled = enabled
        _monitoringEnabled.value = enabled
        if (enabled) {
            startMonitorService()
        } else {
            runCatching { app.stopService(Intent(app, BatteryMonitorService::class.java)) }
        }
        refreshData()
    }

    fun setGameModeEnabled(enabled: Boolean) {
        settings.gameModeEnabled = enabled
        _gameModeEnabled.value = enabled
        if (enabled) {
            ensureMonitoring()
        } else {
            Notifications.cancelGame(app)
        }
    }

    private fun startMonitorService() {
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, BatteryMonitorService::class.java)
            )
        }
    }

    fun setSamplingSeconds(seconds: Int) {
        val sanitized = seconds.coerceIn(15, 900)
        settings.samplingSeconds = sanitized
        _samplingSeconds.value = sanitized
    }

    fun setDesignCapacity(mah: Int) {
        if (mah !in 1000..20000) return
        settings.designCapacityMah = mah
        _designCapacityMah.value = mah
        averageCacheAt = 0L
        _live.update { it.copy(designCapacityMah = mah) }
        refreshData()
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        _themeMode.value = mode
    }

    fun setConsumptionPeriod(period: ConsumptionPeriod) {
        if (_consumptionPeriod.value == period) return
        _consumptionPeriod.value = period
        refreshData()
    }

    fun setHistoryRange(range: HistoryRange) {
        if (_historyRange.value == range) return
        _historyRange.value = range
        refreshData()
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            averageCacheAt = 0L
            refreshData()
        }
    }

    fun refreshDeviceInfo() {
        systemInfo.invalidateDeviceInfo()
        loadDeviceInfo(true)
    }

    fun detectedCapacityMah(): Int? = settings.suggestedCapacityMah

    private fun loadDeviceInfo(refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = runCatching { systemInfo.deviceInfo(refresh) }.getOrNull()
            val sensorList = runCatching { systemInfo.sensors() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (info != null) _deviceInfo.value = info
                _sensors.value = sensorList
            }
        }
    }

    private suspend fun liveLoop() {
        while (true) {
            try {
                val now = System.currentTimeMillis()
                val reading = withContext(Dispatchers.IO) { reader.read() }
                val hardware = withContext(Dispatchers.IO) { systemInfo.snapshot() }
                val averages = currentAverages(now)
                val remainingMah = reading.chargeCounterUah
                    ?.takeIf { it > 0L }
                    ?.let { it / 1000f }
                    ?: (settings.designCapacityMah * reading.percent / 100f)
                val avgDischarge = averages.first
                val avgCharge = averages.second
                val dischargeEta = dischargeEta(reading, remainingMah, avgDischarge)
                val chargeEta = chargeEta(reading, remainingMah)

                _live.update {
                    it.copy(
                        reading = reading,
                        hardware = hardware,
                        designCapacityMah = settings.designCapacityMah,
                        avgDischargeMa = avgDischarge,
                        avgChargeMa = avgCharge,
                        dischargeEtaMs = dischargeEta,
                        chargeEtaMs = chargeEta,
                        remainingCapacityMah = remainingMah
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Mantener el último estado válido si un OEM no expone algún dato.
            }
            delay(3_000L)
        }
    }

    private suspend fun currentAverages(now: Long): Pair<Float?, Float?> {
        if (averageCacheAt > 0L && now - averageCacheAt < 20_000L) {
            return averageCache
        }
        val samples = withContext(Dispatchers.IO) {
            repository.samplesBetween(now - 45 * 60_000L, now)
        }
        val analysis = Analysis.analyze(
            samples,
            settings.designCapacityMah,
            now - 45 * 60_000L,
            now
        )
        val chargingCurrents = samples
            .filter { it.isChargingConnection }
            .mapNotNull { sample -> sample.currentUa?.let { abs(it) / 1000f } }
            .filter { it > 0f }
        val avgCharge = if (chargingCurrents.isEmpty()) null else chargingCurrents.average().toFloat()
        averageCache = analysis.overallAvgMa to avgCharge
        averageCacheAt = now
        return averageCache
    }

    private fun dischargeEta(
        reading: BatteryReading,
        remainingMah: Float,
        avgDischargeMa: Float?
    ): Long? {
        if (reading.isPlugged) return null
        val current = (avgDischargeMa?.takeIf { it > 0.2f } ?: reading.currentMa) ?: return null
        if (remainingMah <= 0f) return null
        val hours = remainingMah / current
        return (hours * 3_600_000L).toLong().coerceIn(0L, 30L * 24L * 3_600_000L)
    }

    private fun chargeEta(reading: BatteryReading, remainingMah: Float): Long? {
        if (!reading.isPlugged || reading.status != android.os.BatteryManager.BATTERY_STATUS_CHARGING) {
            return null
        }
        val current = reading.currentMa ?: return null
        if (current <= 0f) return null
        val capacity = settings.designCapacityMah.toFloat()
        val missingMah = (capacity - remainingMah).coerceAtLeast(0f)
        if (missingMah <= 0f) return null
        return ((missingMah / current) * 3_600_000L).toLong().coerceIn(0L, 24L * 3_600_000L)
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                _isRefreshing.value = true
                try {
                    computeAndPublish()
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    private suspend fun computeAndPublish() {
        val now = System.currentTimeMillis()
        val capacity = settings.designCapacityMah
        val todayStart = startOfToday()
        val todaySamples = repository.samplesBetween(todayStart, now)
        val todayAnalysis = Analysis.analyze(todaySamples, capacity, todayStart, now)
        val todayUsage = usageFor("today", todayStart, now)
        val todayDrains = repository.appDrainsBetween(todayStart, now)
        val todayRows = mergeApps(todayUsage.apps, todayDrains)
        val lastSample = repository.lastSample()

        val period = _consumptionPeriod.value
        val periodStart = when (period) {
            ConsumptionPeriod.TODAY -> todayStart
            ConsumptionPeriod.LAST_24H -> now - 24L * 60L * 60L * 1000L
            ConsumptionPeriod.LAST_7D -> now - 7L * 24L * 60L * 60L * 1000L
        }
        val periodSamples = if (period == ConsumptionPeriod.TODAY) {
            todaySamples
        } else {
            repository.samplesBetween(periodStart, now)
        }
        val periodAnalysis = if (period == ConsumptionPeriod.TODAY) {
            todayAnalysis
        } else {
            Analysis.analyze(periodSamples, capacity, periodStart, now)
        }
        val usageKey = when (period) {
            ConsumptionPeriod.TODAY -> "today"
            ConsumptionPeriod.LAST_24H -> "24h"
            ConsumptionPeriod.LAST_7D -> "7d"
        }
        val periodUsage = if (period == ConsumptionPeriod.TODAY) {
            todayUsage
        } else {
            usageFor(usageKey, periodStart, now)
        }
        val periodDrains = if (period == ConsumptionPeriod.TODAY) {
            todayDrains
        } else {
            repository.appDrainsBetween(periodStart, now)
        }
        val periodRows = mergeApps(periodUsage.apps, periodDrains)

        val historyStart = now - _historyRange.value.durationMs
        val historySamples = repository.samplesBetween(historyStart, now)
        val sessions = Analysis.chargingSessions(historySamples, capacity)
        val activeGame = repository.activeGameSession(now)
        val gameSessions = repository.gameSessions(50)

        val userRows = todayRows.filter { !it.isSystem }
        val rowsWithDrain = userRows.filter { it.drainUah > 0 }
        val dashboardApps = (if (rowsWithDrain.isNotEmpty()) {
            rowsWithDrain.sortedByDescending { it.drainUah }
        } else {
            userRows.sortedByDescending { it.foregroundMs }
        }).take(5)
        val dashboard = DashboardData(
            periodStart = todayStart,
            analysis = todayAnalysis,
            attributedUah = todayRows.sumOf { it.drainUah },
            apps = dashboardApps,
            screenOnMs = todayUsage.screenOnMs ?: todayAnalysis.screenOnMs,
            lastSampleTs = lastSample?.ts ?: 0L,
            sampleCount = todaySamples.size,
            activeGame = activeGame
        )
        val consumption = ConsumptionData(
            period = period,
            periodStart = periodStart,
            analysis = periodAnalysis,
            apps = periodRows,
            attributedUah = periodRows.sumOf { it.drainUah }
        )

        withContext(Dispatchers.Main) {
            _dashboard.value = dashboard
            _consumption.value = consumption
            _historySamples.value = historySamples
            _chargingSessions.value = sessions
            _gameSessions.value = gameSessions
        }
    }

    private fun usageFor(key: String, start: Long, end: Long): UsageRepository.UsageSnapshot {
        val now = System.currentTimeMillis()
        val cached = usageCache[key]
        if (cached != null && now - cached.at < 45_000L) return cached.snapshot
        val snapshot = usageRepository.load(start, end, includeSystem = true)
        usageCache[key] = CachedUsage(now, snapshot)
        return snapshot
    }

    private fun mergeApps(
        usageApps: List<com.batteriatotal.data.AppUsageInfo>,
        drains: Map<String, Long>
    ): List<AppConsumptionRow> {
        val rows = LinkedHashMap<String, AppConsumptionRow>()
        usageApps.forEach { usage ->
            rows[usage.packageName] = AppConsumptionRow(
                packageName = usage.packageName,
                label = usage.label,
                isSystem = usage.isSystem,
                foregroundMs = usage.foregroundMs,
                launches = usage.launches,
                lastUsed = usage.lastUsed,
                drainUah = drains[usage.packageName] ?: 0L
            )
        }
        drains.forEach { (pkg, drain) ->
            if (!rows.containsKey(pkg)) {
                val meta = usageRepository.resolve(pkg) ?: UsageRepository.AppMeta(pkg, false)
                rows[pkg] = AppConsumptionRow(
                    packageName = pkg,
                    label = meta.label,
                    isSystem = meta.isSystem,
                    foregroundMs = 0L,
                    launches = 0,
                    lastUsed = 0L,
                    drainUah = drain
                )
            }
        }
        return rows.values.sortedWith(
            compareByDescending<AppConsumptionRow> { it.drainUah }
                .thenByDescending { it.foregroundMs }
        )
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
