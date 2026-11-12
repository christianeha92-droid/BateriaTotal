package com.batteriatotal.data

import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.roundToInt

data class PropertyValue(
    val key: String,
    val label: String,
    val value: String?,
    val supported: Boolean,
    val unit: String
)

data class BatteryReading(
    val readAt: Long,
    val level: Int,
    val scale: Int,
    val status: Int,
    val health: Int,
    val plugged: Int,
    val present: Boolean,
    val voltageMv: Int?,
    val temperatureC: Float?,
    val technology: String?,
    val chargeCounterUah: Long?,
    val currentNowUa: Long?,
    val currentAverageUa: Long?,
    val energyCounterNwh: Long?,
    val capacityProperty: Int?,
    val cycleCount: Int?,
    val maxChargingCurrentUa: Long?,
    val maxChargingVoltageUv: Long?,
    val chargingStatus: Int?,
    val rawExtras: Map<String, String>,
    val properties: List<PropertyValue>
) {
    val percent: Int
        get() = if (scale > 0) {
            (level * 100f / scale).roundToInt().coerceIn(0, 100)
        } else {
            level.coerceIn(0, 100)
        }

    val isPlugged: Boolean get() = plugged != 0

    val isCharging: Boolean
        get() = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

    val usableCurrentUa: Long? get() = currentNowUa ?: currentAverageUa

    /** Potencia instantánea en vatios, usando corriente y voltaje. */
    val powerW: Float?
        get() {
            val current = usableCurrentUa ?: return null
            val voltage = voltageMv ?: return null
            if (current == 0L || voltage <= 0) return null
            return abs(current) / 1_000_000f * (voltage / 1000f)
        }

    /** Corriente absoluta en mA. */
    val currentMa: Float?
        get() = usableCurrentUa?.let { abs(it) / 1000f }?.takeIf { it > 0f }
}

data class Sample(
    val ts: Long,
    val level: Int,
    val currentUa: Long?,
    val voltageMv: Int?,
    val temperatureC: Float?,
    val status: Int,
    val plugged: Int,
    val chargeCounterUah: Long?,
    val energyCounterNwh: Long?,
    val screenOn: Boolean,
    val wifiOn: Boolean,
    val mobileOn: Boolean,
    val brightness: Int?
) {
    val isChargingConnection: Boolean get() = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING

    val powerW: Float?
        get() {
            val current = currentUa ?: return null
            val voltage = voltageMv ?: return null
            if (current == 0L || voltage <= 0) return null
            return abs(current) / 1_000_000f * (voltage / 1000f)
        }
}

data class AppUsageInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val foregroundMs: Long,
    val launches: Int,
    val lastUsed: Long
)

data class AppConsumptionRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val foregroundMs: Long,
    val launches: Int,
    val lastUsed: Long,
    val drainUah: Long
)

/** Estado aproximado de una app a partir de los eventos públicos de uso. */
enum class AppProcessState(val label: String) {
    FOREGROUND("Primer plano"),
    VISIBLE("Visible"),
    SERVICE("Servicio"),
    BACKGROUND("Segundo plano"),
    CACHED("Sin actividad reciente"),
    UNKNOWN("Sin datos")
}

/** Cómo puede detenerse una app desde Batería Total. */
enum class ProcessStopMode {
    /** killBackgroundProcesses funciona para otras apps (Android 8–13). */
    DIRECT,

    /** Android 14+ o app en primer plano: hay que usar «Forzar detención». */
    MANUAL,

    /** Apps del sistema necesarias o la propia app: no se ofrece detener. */
    NONE
}

/**
 * Fila de la pantalla de procesos. Android no expone a las apps normales la
 * energía exacta ni la lista completa de procesos de otras apps, así que el
 * estado se deduce de UsageStats y el consumo es el atribuido por el monitor.
 */
data class ProcessRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val state: AppProcessState,
    val foregroundMs: Long,
    val lastActivityAt: Long,
    val drainUah: Long,
    val isSelf: Boolean,
    val isLauncher: Boolean,
    val stopMode: ProcessStopMode
) {
    val isForeground: Boolean get() = state == AppProcessState.FOREGROUND || state == AppProcessState.VISIBLE
    val canStopDirectly: Boolean get() = stopMode == ProcessStopMode.DIRECT
    val canRequestStop: Boolean get() = stopMode != ProcessStopMode.NONE
}

data class ThermalZone(
    val name: String,
    val temperatureC: Float
)

data class HardwareState(
    val cpuLoadPercent: Float? = null,
    val cpuFrequenciesMhz: List<Int> = emptyList(),
    val cpuCores: Int = 0,
    val memTotalBytes: Long = 0,
    val memAvailableBytes: Long = 0,
    val thermalStatus: String = "—",
    val thermalZones: List<ThermalZone> = emptyList(),
    val screenOn: Boolean = false,
    val brightness: Int? = null,
    val autoBrightness: Boolean = false,
    val screenResolution: String = "—",
    val refreshRateHz: Float = 0f,
    val wifiEnabled: Boolean = false,
    val mobileDataActive: Boolean = false,
    val networkLabel: String = "—",
    val bluetoothEnabled: Boolean? = null,
    val gpsEnabled: Boolean? = null,
    val nfcEnabled: Boolean? = null,
    val audioActive: Boolean = false,
    val sensorCount: Int = 0,
    val wakeSensorCount: Int = 0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxRateBps: Long = 0,
    val txRateBps: Long = 0,
    val deepSleepMs: Long = 0,
    val uptimeMs: Long = 0
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val typeName: String,
    val powerMa: Float,
    val wakeUp: Boolean,
    val maxRange: Float,
    val resolution: Float
)

data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val androidRelease: String,
    val sdk: Int,
    val securityPatch: String,
    val buildId: String,
    val fingerprint: String,
    val hyperOsVersion: String?,
    val miuiVersion: String?,
    val soc: String?,
    val abi: String?,
    val systemProps: Map<String, String>,
    val kernelBattery: Map<String, String>
)

data class StateBucket(
    val label: String,
    val avgMa: Float?,
    val timeMs: Long,
    val drainUah: Long
)

data class HardwareAnalysis(
    val periodStart: Long = 0,
    val periodEnd: Long = 0,
    val monitoredMs: Long = 0,
    val dischargeMs: Long = 0,
    val dischargeUah: Long = 0,
    val chargeMs: Long = 0,
    val chargeGainUah: Long = 0,
    val screenOnMs: Long = 0,
    val screenOffMs: Long = 0,
    val screenOnAvgMa: Float? = null,
    val screenOffAvgMa: Float? = null,
    val overallAvgMa: Float? = null,
    val wifiAvgMa: Float? = null,
    val mobileAvgMa: Float? = null,
    val offlineAvgMa: Float? = null,
    val avgTempC: Float? = null,
    val maxTempC: Float? = null,
    val stateBuckets: List<StateBucket> = emptyList(),
    val samples: Int = 0
) {
    val dischargeMah: Float get() = dischargeUah / 1000f
}

data class ChargingSession(
    val startTs: Long,
    val endTs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val addedUah: Long?,
    val durationMs: Long,
    val avgCurrentMa: Float?,
    val maxTempC: Float?,
    val plugged: Int
)

/**
 * Sesión de juego detectada con UsageStats. La energía no se mide por juego:
 * solo se registra la batería, corriente y temperatura reales del dispositivo
 * mientras una app marcada como juego está en primer plano.
 */
data class GameSession(
    val id: Long = 0L,
    val startTs: Long,
    val lastTs: Long,
    val endTs: Long?,
    val packageName: String,
    val label: String,
    val startLevel: Int,
    val endLevel: Int,
    val startChargeUah: Long?,
    val endChargeUah: Long?,
    val minTempC: Float?,
    val maxTempC: Float?,
    val avgCurrentMa: Float?,
    val maxCurrentMa: Float?,
    val pluggedMs: Long,
    val plugged: Boolean,
    val insufficientCharger: Boolean,
    val sampleCount: Int
) {
    val durationMs: Long get() = ((endTs ?: lastTs) - startTs).coerceAtLeast(0L)
    val hadCharger: Boolean get() = pluggedMs > 0L
}

enum class ConsumptionPeriod(val label: String) {
    TODAY("Hoy"),
    LAST_24H("24 h"),
    LAST_7D("7 días")
}

enum class HistoryRange(val label: String, val durationMs: Long) {
    SIX_HOURS("6 h", 6 * 60 * 60 * 1000L),
    DAY("24 h", 24 * 60 * 60 * 1000L),
    WEEK("7 días", 7 * 24 * 60 * 60 * 1000L)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class DashboardData(
    val periodStart: Long = 0,
    val analysis: HardwareAnalysis = HardwareAnalysis(),
    val attributedUah: Long = 0,
    val apps: List<AppConsumptionRow> = emptyList(),
    val screenOnMs: Long = 0,
    val lastSampleTs: Long = 0,
    val sampleCount: Int = 0,
    val activeGame: GameSession? = null
)

data class ConsumptionData(
    val period: ConsumptionPeriod = ConsumptionPeriod.TODAY,
    val periodStart: Long = 0,
    val analysis: HardwareAnalysis = HardwareAnalysis(),
    val apps: List<AppConsumptionRow> = emptyList(),
    val attributedUah: Long = 0
)
