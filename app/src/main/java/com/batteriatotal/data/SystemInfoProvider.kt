package com.batteriatotal.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SystemInfoProvider(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = appContext.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    private val nfcAdapter by lazy { runCatching { NfcAdapter.getDefaultAdapter(appContext) }.getOrNull() }
    private val bluetoothManager by lazy {
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val sensorManager by lazy {
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    private var previousCpuTotal = -1L
    private var previousCpuIdle = -1L
    private var previousRx = -1L
    private var previousTx = -1L
    private var previousTrafficAt = 0L
    private var cachedThermalZones: List<ThermalZone> = emptyList()
    private var lastThermalRead = 0L
    private var cachedSensors: List<SensorInfo>? = null
    private var cachedDeviceInfo: DeviceInfo? = null

    fun snapshot(): HardwareState {
        val memoryInfo = getMemoryInfo()
        val thermalZones = readThermalZones()
        val traffic = readTraffic()
        val displayMetrics = appContext.resources.displayMetrics
        val refreshRate = readRefreshRate()
        val network = readNetwork()
        val brightness = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        }.getOrDefault(-1).takeIf { it >= 0 }
        val autoBrightness = runCatching {
            Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)
        val sensors = readSensors()
        val bluetoothOn = runCatching {
            Settings.Global.getInt(appContext.contentResolver, "bluetooth_on", -1)
        }.getOrDefault(-1).let { if (it == -1) null else it == 1 }
        val gpsOn = runCatching {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrNull()
        val nfcOn = runCatching { nfcAdapter?.isEnabled }.getOrNull()

        return HardwareState(
            cpuLoadPercent = readCpuLoad(),
            cpuFrequenciesMhz = readCpuFrequencies(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            memTotalBytes = memoryInfo?.totalMem ?: 0L,
            memAvailableBytes = memoryInfo?.availMem ?: 0L,
            thermalStatus = readThermalStatus(),
            thermalZones = thermalZones,
            screenOn = powerManager?.isInteractive ?: false,
            brightness = brightness,
            autoBrightness = autoBrightness,
            screenResolution = "${displayMetrics.widthPixels} × ${displayMetrics.heightPixels}",
            refreshRateHz = refreshRate,
            wifiEnabled = runCatching { wifiManager?.isWifiEnabled }.getOrDefault(false) ?: false,
            mobileDataActive = network.mobileActive,
            networkLabel = network.label,
            bluetoothEnabled = bluetoothOn,
            gpsEnabled = gpsOn,
            nfcEnabled = nfcOn,
            audioActive = runCatching { audioManager?.isMusicActive }.getOrDefault(false) ?: false,
            sensorCount = sensors.size,
            wakeSensorCount = sensors.count { it.wakeUp },
            rxBytes = traffic.rx,
            txBytes = traffic.tx,
            rxRateBps = traffic.rxRate,
            txRateBps = traffic.txRate,
            deepSleepMs = (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()).coerceAtLeast(0L),
            uptimeMs = SystemClock.elapsedRealtime()
        )
    }

    fun sensors(): List<SensorInfo> = readSensors()

    fun deviceInfo(refresh: Boolean = false): DeviceInfo {
        if (!refresh) cachedDeviceInfo?.let { return it }
        val props = readSelectedSystemProps()
        val info = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT,
            hyperOsVersion = buildString {
                val name = props["ro.mi.os.version.name"]
                val incremental = props["ro.mi.os.version.incremental"]
                if (!name.isNullOrBlank()) append(name)
                if (!incremental.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(incremental)
                }
            }.takeIf { it.isNotBlank() },
            miuiVersion = props["ro.miui.ui.version.name"]?.takeIf { it.isNotBlank() },
            soc = props["ro.soc.model"] ?: props["ro.board.platform"] ?: props["ro.hardware"],
            abi = Build.SUPPORTED_ABIS.joinToString(", "),
            systemProps = props,
            kernelBattery = SysfsBattery.readAll()
        )
        cachedDeviceInfo = info
        return info
    }

    fun invalidateDeviceInfo() {
        cachedDeviceInfo = null
    }

    private fun getMemoryInfo(): ActivityManager.MemoryInfo? {
        val manager = activityManager ?: return null
        return runCatching {
            ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        }.getOrNull()
    }

    private fun readRefreshRate(): Float = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.display?.refreshRate ?: 0f
        } else {
            @Suppress("DEPRECATION")
            (appContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.refreshRate
        }
    }.getOrDefault(0f)

    private fun readNetwork(): NetworkState {
        val cm = connectivityManager ?: return NetworkState("—", false)
        return runCatching {
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            val label = when {
                caps == null -> "Sin conexión"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Conectado"
            }
            val mobileActive = runCatching {
                cm.allNetworks.any { network ->
                    val capabilities = cm.getNetworkCapabilities(network) ?: return@any false
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }.getOrDefault(false)
            NetworkState(label, mobileActive)
        }.getOrDefault(NetworkState("—", false))
    }

    private fun readTraffic(): TrafficState {
        val rx = runCatching { TrafficStats.getTotalRxBytes() }.getOrDefault(-1L)
        val tx = runCatching { TrafficStats.getTotalTxBytes() }.getOrDefault(-1L)
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return TrafficState(-1L, -1L, -1L, -1L)
        }
        val now = SystemClock.elapsedRealtime()
        val lastRx = previousRx
        val lastTx = previousTx
        val lastAt = previousTrafficAt
        val rxRate = if (lastRx >= 0 && lastAt > 0 && now > lastAt) {
            ((rx - lastRx).coerceAtLeast(0L) * 1000L / (now - lastAt))
        } else -1L
        val txRate = if (lastTx >= 0 && lastAt > 0 && now > lastAt) {
            ((tx - lastTx).coerceAtLeast(0L) * 1000L / (now - lastAt))
        } else -1L
        previousRx = rx
        previousTx = tx
        previousTrafficAt = now
        return TrafficState(rx, tx, rxRate, txRate)
    }

    private fun readCpuLoad(): Float? {
        val line = runCatching {
            File("/proc/stat").useLines { lines -> lines.firstOrNull { it.startsWith("cpu ") } }
        }.getOrNull() ?: return null
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.size < 5) return null
        val values = tokens.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val total = values.take(8).sum()
        val idle = (values.getOrNull(3) ?: 0L) + (values.getOrNull(4) ?: 0L)
        val previousTotal = previousCpuTotal
        val previousIdle = previousCpuIdle
        previousCpuTotal = total
        previousCpuIdle = idle
        if (previousTotal < 0 || total <= previousTotal) return null
        val deltaTotal = total - previousTotal
        val deltaIdle = (idle - previousIdle).coerceAtLeast(0L)
        if (deltaTotal <= 0L) return null
        return ((deltaTotal - deltaIdle).toDouble() / deltaTotal * 100.0)
            .toFloat()
            .coerceIn(0f, 100f)
    }

    private fun readCpuFrequencies(): List<Int> {
        val result = ArrayList<Int>()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtMost(16)
        for (core in 0 until cores) {
            val value = runCatching {
                File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                    .readText()
                    .trim()
                    .toLong()
            }.getOrNull() ?: continue
            val mhz = (value / 1000L).toInt()
            if (mhz > 0) result.add(mhz)
        }
        return result
    }

    private fun readThermalZones(): List<ThermalZone> {
        val now = SystemClock.elapsedRealtime()
        if (now - lastThermalRead < 15_000L) return cachedThermalZones
        lastThermalRead = now
        val zones = ArrayList<ThermalZone>()
        runCatching {
            val files = File("/sys/class/thermal").listFiles()
                ?.filter { it.name.startsWith("thermal_zone") }
                ?.take(24)
                ?: emptyList()
            files.forEach { zoneDir ->
                val type = runCatching { File(zoneDir, "type").readText().trim() }.getOrNull()
                if (type.isNullOrBlank()) return@forEach
                val normalized = type.lowercase(Locale.US)
                if (listOf("cpu", "gpu", "battery", "charger", "soc", "skin", "quiet", "apc")
                        .none { normalized.contains(it) }
                ) return@forEach
                val raw = runCatching { File(zoneDir, "temp").readText().trim().toLong() }
                    .getOrNull() ?: return@forEach
                val celsius = if (abs(raw) > 1000L) raw / 1000f else raw.toFloat()
                if (celsius in -20f..150f) {
                    zones.add(ThermalZone(type, celsius))
                }
            }
        }
        cachedThermalZones = zones.distinctBy { it.name }.take(8)
        return cachedThermalZones
    }

    private fun readThermalStatus(): String {
        val manager = powerManager ?: return "—"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "No disponible"
        return runCatching {
            when (manager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderada"
                PowerManager.THERMAL_STATUS_SEVERE -> "Grave"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Crítica"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado térmico"
                else -> "Desconocido"
            }
        }.getOrDefault("—")
    }

    private fun readSensors(): List<SensorInfo> {
        cachedSensors?.let { return it }
        val manager = sensorManager ?: return emptyList()
        val result = runCatching {
            manager.getSensorList(Sensor.TYPE_ALL).map { sensor ->
                SensorInfo(
                    name = sensor.name,
                    vendor = sensor.vendor,
                    typeName = sensor.stringType
                        ?.removePrefix("android.sensor.")
                        ?.replace('_', ' ')
                        ?: "Sensor ${sensor.type}",
                    powerMa = sensor.power,
                    wakeUp = sensor.isWakeUpSensor,
                    maxRange = sensor.maximumRange,
                    resolution = sensor.resolution
                )
            }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }.getOrDefault(emptyList())
        cachedSensors = result
        return result
    }

    private fun readSelectedSystemProps(): Map<String, String> {
        val keys = listOf(
            "ro.mi.os.version.name",
            "ro.mi.os.version.incremental",
            "ro.miui.ui.version.name",
            "ro.miui.ui.version.code",
            "ro.build.version.incremental",
            "ro.build.display.id",
            "ro.build.version.security_patch",
            "ro.product.marketname",
            "ro.soc.model",
            "ro.board.platform",
            "ro.hardware",
            "ro.product.cpu.abi",
            "ro.build.date",
            "ro.kernel.version"
        )
        val result = LinkedHashMap<String, String>()
        runCatching {
            val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(2, TimeUnit.SECONDS)
            val pattern = Regex("^\\[(.+?)]: \\[(.*)]$")
            output.lineSequence().forEach { line ->
                val match = pattern.find(line) ?: return@forEach
                val key = match.groupValues[1]
                if (key in keys) result[key] = match.groupValues[2]
            }
        }
        // Rellenar con valores conocidos si getprop no está accesible.
        result.putIfAbsent("ro.build.version.incremental", Build.VERSION.INCREMENTAL)
        result.putIfAbsent("ro.build.display.id", Build.DISPLAY)
        result.putIfAbsent("ro.build.version.security_patch", Build.VERSION.SECURITY_PATCH)
        result.putIfAbsent("ro.product.cpu.abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "—")
        return result
    }

    private data class NetworkState(val label: String, val mobileActive: Boolean)
    private data class TrafficState(val rx: Long, val tx: Long, val rxRate: Long, val txRate: Long)
}
