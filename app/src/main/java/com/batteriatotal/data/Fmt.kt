package com.batteriatotal.data

import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Fmt {

    fun statusLabel(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Sin carga"
        BatteryManager.BATTERY_STATUS_FULL -> "Carga completa"
        else -> "Desconocido"
    }

    fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Agotada"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fallo"
        BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
        else -> "Desconocida"
    }

    fun pluggedLabel(plugged: Int): String {
        if (plugged == 0) return "Batería"
        val sources = buildList {
            if (plugged and 1 != 0) add("Red eléctrica")
            if (plugged and 2 != 0) add("USB")
            if (plugged and 4 != 0) add("Inalámbrico")
            if (plugged and 8 != 0) add("Base")
        }
        return if (sources.isEmpty()) "Conectado" else sources.joinToString(" + ")
    }

    fun chargingSummary(reading: BatteryReading): String {
        if (!reading.isPlugged) {
            return when (reading.status) {
                BatteryManager.BATTERY_STATUS_FULL -> "Batería llena"
                else -> "Descargando"
            }
        }
        return when (reading.status) {
            BatteryManager.BATTERY_STATUS_FULL -> "Carga completa · ${pluggedLabel(reading.plugged)}"
            BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando · ${pluggedLabel(reading.plugged)}"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Carga en pausa · ${pluggedLabel(reading.plugged)}"
            else -> "Conectado · ${pluggedLabel(reading.plugged)}"
        }
    }

    fun formatPercent(percent: Int?): String = percent?.let { "$it %" } ?: "—"

    fun formatCurrent(ua: Long?): String {
        if (ua == null) return "—"
        val ma = abs(ua) / 1000f
        return if (ma >= 1000f) {
            String.format(Locale.getDefault(), "%.2f A", ma / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f mA", ma)
        }
    }

    fun formatCurrentMa(ma: Float?): String {
        if (ma == null || ma <= 0f) return "—"
        return if (ma >= 1000f) {
            String.format(Locale.getDefault(), "%.2f A", ma / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f mA", ma)
        }
    }

    fun formatPerHour(ma: Float?, capacityMah: Int): String {
        if (ma == null || ma <= 0f || capacityMah <= 0) return "—"
        return String.format(Locale.getDefault(), "%.1f %%/h", ma / capacityMah * 100f)
    }

    fun formatPower(watts: Float?): String =
        watts?.let { String.format(Locale.getDefault(), "%.2f W", it) } ?: "—"

    fun formatVoltage(mv: Int?): String =
        mv?.takeIf { it > 0 }?.let { String.format(Locale.getDefault(), "%.2f V", it / 1000f) } ?: "—"

    fun formatTemperature(celsius: Float?): String =
        celsius?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "—"

    fun formatCharge(uah: Long?): String {
        if (uah == null) return "—"
        val mah = uah / 1000f
        return String.format(Locale.getDefault(), "%.0f mAh", mah)
    }

    fun formatChargePrecise(uah: Long?): String {
        if (uah == null) return "—"
        val mah = uah / 1000f
        return if (mah >= 100f) {
            String.format(Locale.getDefault(), "%.0f mAh", mah)
        } else {
            String.format(Locale.getDefault(), "%.1f mAh", mah)
        }
    }

    fun formatEnergy(nwh: Long?): String {
        if (nwh == null) return "—"
        val mwh = nwh / 1_000_000f
        return if (mwh >= 1000f) {
            String.format(Locale.getDefault(), "%.2f Wh", mwh / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f mWh", mwh)
        }
    }

    fun formatMahFromUah(uah: Long): String =
        String.format(Locale.getDefault(), "%.1f mAh", uah / 1000f)

    fun formatDuration(ms: Long?): String {
        if (ms == null || ms <= 0L) return "—"
        val totalMinutes = ms / 60_000L
        if (totalMinutes < 1L) return "<1 min"
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h $minutes min"
            else -> "$minutes min"
        }
    }

    fun formatDurationCompact(ms: Long?): String {
        if (ms == null || ms <= 0L) return "—"
        val totalMinutes = ms / 60_000L
        if (totalMinutes < 60L) return "$totalMinutes min"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (minutes == 0L) "$hours h" else "$hours h $minutes min"
    }

    fun formatRelative(ts: Long, now: Long = System.currentTimeMillis()): String {
        if (ts <= 0) return "—"
        val diff = now - ts
        return when {
            diff < 10_000 -> "ahora"
            diff < 60_000 -> "hace ${diff / 1000} s"
            diff < 3_600_000 -> "hace ${diff / 60_000} min"
            diff < 86_400_000 -> "hace ${diff / 3_600_000} h"
            else -> "hace ${diff / 86_400_000} d"
        }
    }

    fun formatClock(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    fun formatDateTime(ts: Long): String =
        SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(ts))

    fun formatAxisTime(ts: Long, includeDate: Boolean): String =
        SimpleDateFormat(if (includeDate) "dd/MM" else "HH:mm", Locale.getDefault()).format(Date(ts))

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }

    fun formatRate(bytesPerSecond: Long): String =
        if (bytesPerSecond < 0) "—" else "${formatBytes(bytesPerSecond)}/s"

    fun formatCpuFrequencies(frequencies: List<Int>): String {
        if (frequencies.isEmpty()) return "No accesible"
        val max = frequencies.maxOrNull() ?: return "No accesible"
        val min = frequencies.minOrNull() ?: max
        val format = { mhz: Int ->
            if (mhz >= 1000) String.format(Locale.getDefault(), "%.2f GHz", mhz / 1000f)
            else "$mhz MHz"
        }
        return if (min == max) format(max) else "${format(min)} – ${format(max)}"
    }

    fun formatBrightness(brightness: Int?, auto: Boolean): String {
        if (brightness == null || brightness < 0) return "—"
        return if (auto) "Automático ($brightness)" else "$brightness"
    }

    fun extraLabel(key: String): String {
        val known = mapOf(
            "level" to "Nivel",
            "scale" to "Escala",
            "status" to "Estado",
            "health" to "Salud",
            "present" to "Presente",
            "plugged" to "Fuente de alimentación",
            "voltage" to "Voltaje",
            "temperature" to "Temperatura",
            "technology" to "Tecnología",
            "charge_counter" to "Contador de carga",
            "energy_counter" to "Contador de energía",
            "cycle_count" to "Ciclos de carga",
            "charging_status" to "Estado de carga",
            "max_charging_current" to "Corriente máxima de carga",
            "max_charging_voltage" to "Voltaje máximo de carga",
            "invalid_charger" to "Cargador no válido",
            "charger_type" to "Tipo de cargador",
            "current_now" to "Corriente actual",
            "current_average" to "Corriente media",
            "battery_soh" to "Estado de salud (SoH)",
            "state_of_health" to "Estado de salud (SoH)",
            "vbus_voltage" to "Voltaje VBUS",
            "input_current" to "Corriente de entrada",
            "battery_temperature" to "Temperatura de batería",
            "charger_temperature" to "Temperatura del cargador",
            "usb_present" to "USB presente",
            "wireless_present" to "Carga inalámbrica presente",
            "dock_present" to "Base presente"
        )
        return known[key] ?: key
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase(Locale.getDefault()) }
    }

    fun humanizeKey(key: String): String =
        key.replace('_', ' ')
            .replaceFirstChar { it.uppercase(Locale.getDefault()) }
}
