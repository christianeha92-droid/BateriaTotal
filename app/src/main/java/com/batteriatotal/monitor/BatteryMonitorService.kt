package com.batteriatotal.monitor

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.batteriatotal.Notifications
import com.batteriatotal.data.Analysis
import com.batteriatotal.data.BatteryReader
import com.batteriatotal.data.BatteryReading
import com.batteriatotal.data.BatteryRepository
import com.batteriatotal.data.GameDetector
import com.batteriatotal.data.Permissions
import com.batteriatotal.data.Sample
import com.batteriatotal.data.SettingsStore
import com.batteriatotal.data.SystemInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var reader: BatteryReader
    private lateinit var repository: BatteryRepository
    private lateinit var settings: SettingsStore
    private lateinit var systemInfo: SystemInfoProvider
    private lateinit var gameDetector: GameDetector
    private lateinit var gameTracker: GameSessionTracker
    private lateinit var powerManager: PowerManager

    @Volatile
    private var lastSample: Sample? = null

    @Volatile
    private var sampling = false

    @Volatile
    private var sampleScheduled = false

    private var receiverRegistered = false
    private var started = false
    private var lastSampleAt = 0L

    private val sampleRunnable = Runnable { performSample() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> triggerSample(minGapMs = 15_000L)
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF -> triggerSample(minGapMs = 1_500L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        reader = BatteryReader(this)
        repository = BatteryRepository.get(this)
        settings = SettingsStore.get(this)
        systemInfo = SystemInfoProvider(this)
        gameDetector = GameDetector(this)
        gameTracker = GameSessionTracker(repository)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        Notifications.createChannel(this)
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reading = reader.read()
        startAsForeground(reading)
        if (!settings.monitoringEnabled) {
            gameTracker.finish(System.currentTimeMillis())
            Notifications.cancelGame(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            val now = System.currentTimeMillis()
            lastSample = repository.lastSample()?.takeIf {
                now - it.ts in 0..Analysis.MAX_ATTRIBUTION_GAP_MS
            }
            lastSampleAt = lastSample?.ts ?: 0L
            serviceScope.launch {
                repository.prune(now - RETENTION_MS)
                // Si el proceso murió con una sesión abierta, se cierra para no
                // mezclarla con la sesión nueva.
                repository.closeStaleGameSessions(now)
            }
            triggerSample(force = true)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        gameTracker.finish(System.currentTimeMillis())
        Notifications.cancelGame(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        receiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrDefault(false)
    }

    private fun startAsForeground(reading: BatteryReading) {
        val notification = Notifications.monitorNotification(this, reading)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    Notifications.NOTIFICATION_MONITOR,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Notifications.NOTIFICATION_MONITOR, notification)
            }
        }.onFailure {
            stopSelf()
        }
    }

    private fun triggerSample(force: Boolean = false, minGapMs: Long = 0L) {
        if (sampling || sampleScheduled) return
        if (!force && minGapMs > 0L && System.currentTimeMillis() - lastSampleAt < minGapMs) {
            return
        }
        sampleScheduled = true
        handler.postDelayed(sampleRunnable, if (force) 300L else 800L)
    }

    private fun performSample() {
        sampleScheduled = false
        if (sampling) return
        if (!settings.monitoringEnabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        sampling = true
        serviceScope.launch {
            try {
                val reading = reader.read()
                val hardware = systemInfo.snapshot()
                val sample = reading.toSample(hardware.screenOn, hardware.wifiEnabled, hardware.mobileDataActive, hardware.brightness)
                val previous = lastSample
                var appDrains: Map<String, Long> = emptyMap()

                if (previous != null && !previous.isChargingConnection && !sample.isChargingConnection) {
                    val gap = sample.ts - previous.ts
                    if (gap in 10_000L..Analysis.MAX_ATTRIBUTION_GAP_MS) {
                        val drain = Analysis.drainUah(previous, sample, settings.designCapacityMah)
                        if (drain != null && drain > 0L && (previous.screenOn || sample.screenOn)) {
                            val active = foregroundDurations(previous.ts, sample.ts, previous.screenOn)
                            appDrains = Analysis.attributeDrain(drain, active, gap)
                        }
                    }
                }

                repository.record(sample, appDrains)
                updateGameMode(sample, previous, reading)
                lastSample = sample
                lastSampleAt = sample.ts
                withContext(Dispatchers.Main) {
                    Notifications.updateMonitor(this@BatteryMonitorService, reading)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // El servicio nunca debe caerse por un dato no disponible del fabricante.
            } finally {
                sampling = false
                withContext(Dispatchers.Main) {
                    if (settings.monitoringEnabled) {
                        scheduleNext()
                    } else {
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun updateGameMode(
        sample: Sample,
        previous: Sample?,
        reading: BatteryReading
    ) {
        if (!settings.gameModeEnabled) {
            gameTracker.finish(sample.ts)
            Notifications.cancelGame(this)
            return
        }
        val game = if (sample.screenOn) gameDetector.currentGame() else null
        val result = gameTracker.update(
            sample = sample,
            previous = previous,
            game = game,
            enabled = true
        )
        val session = result.session
        if (session == null) {
            Notifications.cancelGame(this)
        } else {
            Notifications.updateGame(this, session, reading)
            if (result.warningTriggered) {
                Notifications.notifyChargerWarning(this, session)
            }
        }
    }

    private fun scheduleNext() {
        handler.removeCallbacks(sampleRunnable)
        val screenOn = powerManager.isInteractive
        val delayMs = if (screenOn) {
            (settings.samplingSeconds * 1000L).coerceAtLeast(15_000L)
        } else {
            BACKGROUND_SAMPLE_MS
        }
        handler.postDelayed(sampleRunnable, delayMs)
    }

    /**
     * Duración en primer plano por paquete dentro del intervalo. Se consulta un
     * margen anterior para conocer la app que ya estaba abierta al empezar.
     */
    private fun foregroundDurations(
        startTs: Long,
        endTs: Long,
        initialScreenOn: Boolean
    ): Map<String, Long> {
        if (!Permissions.hasUsageAccess(this)) return emptyMap()
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val queryStart = (startTs - 60 * 60_000L).coerceAtLeast(0L)
        val events = runCatching { manager.queryEvents(queryStart, endTs) }.getOrNull()
            ?: return emptyMap()
        val durations = HashMap<String, Long>()
        var currentPackage: String? = null
        var screenOn = initialScreenOn
        var lastTs = startTs
        val event = UsageEvents.Event()

        fun applyState(type: Int, packageName: String?) {
            when (type) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    screenOn = false
                    currentPackage = null
                }
                UsageEvents.Event.ACTIVITY_RESUMED -> currentPackage = packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (currentPackage == packageName) currentPackage = null
                }
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    currentPackage = null
                    screenOn = false
                }
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(queryStart, endTs)
            if (ts < startTs) {
                applyState(event.eventType, event.packageName)
                lastTs = startTs
                continue
            }
            if (lastTs < startTs) lastTs = startTs
            if (screenOn) {
                currentPackage?.let { pkg ->
                    val duration = (ts - lastTs).coerceAtLeast(0L)
                    if (duration > 0L) durations[pkg] = (durations[pkg] ?: 0L) + duration
                }
            }
            applyState(event.eventType, event.packageName)
            lastTs = ts
        }
        if (screenOn) {
            currentPackage?.let { pkg ->
                val duration = (endTs - lastTs).coerceAtLeast(0L)
                if (duration > 0L) durations[pkg] = (durations[pkg] ?: 0L) + duration
            }
        }
        return durations
    }

    private fun BatteryReading.toSample(
        screenOn: Boolean,
        wifiOn: Boolean,
        mobileOn: Boolean,
        brightness: Int?
    ): Sample = Sample(
        ts = readAt,
        level = percent,
        currentUa = usableCurrentUa,
        voltageMv = voltageMv,
        temperatureC = temperatureC,
        status = status,
        plugged = plugged,
        chargeCounterUah = chargeCounterUah,
        energyCounterNwh = energyCounterNwh,
        screenOn = screenOn,
        wifiOn = wifiOn,
        mobileOn = mobileOn,
        brightness = brightness
    )

    companion object {
        private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private const val BACKGROUND_SAMPLE_MS = 5L * 60L * 1000L
    }
}
