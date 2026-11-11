package com.batteriatotal.data

import java.io.File
import java.util.Locale

/**
 * Lectura best-effort del nodo de batería del kernel. En muchas versiones de
 * Android (incluido HyperOS) SELinux bloquea estos ficheros para apps normales,
 * así que todos los fallos se ignoran y se muestran como "no accesible".
 */
object SysfsBattery {

    private val directories = listOf(
        "/sys/class/power_supply/battery",
        "/sys/class/power_supply/bms"
    )

    private val keys = listOf(
        "capacity",
        "status",
        "health",
        "present",
        "voltage_now",
        "current_now",
        "temp",
        "charge_counter",
        "charge_full",
        "charge_full_design",
        "cycle_count",
        "technology",
        "power_now",
        "input_current_limit",
        "constant_charge_current"
    )

    fun readAll(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (dir in directories) {
            keys.forEach { key ->
                runCatching {
                    val file = File(dir, key)
                    if (file.exists() && file.canRead()) {
                        result["${dir.substringAfterLast('/')}.$key"] = file.readText().trim()
                    }
                }
            }
        }
        return result
    }

    fun designCapacityMah(): Int? {
        val raw = readFirst("charge_full_design") ?: return null
        val value = raw.toLongOrNull() ?: return null
        if (value <= 0L) return null
        val mah = if (value > 20_000L) value / 1000 else value
        return mah.toInt().takeIf { it in 1000..20_000 }
    }

    fun chargeFullMah(): Int? {
        val raw = readFirst("charge_full") ?: return null
        val value = raw.toLongOrNull() ?: return null
        if (value <= 0L) return null
        val mah = if (value > 20_000L) value / 1000 else value
        return mah.toInt().takeIf { it in 1000..20_000 }
    }

    private fun readFirst(key: String): String? {
        for (dir in directories) {
            val value = runCatching {
                val file = File(dir, key)
                if (file.exists() && file.canRead()) file.readText().trim() else null
            }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    fun formatValue(key: String, value: String): String {
        val numeric = value.toLongOrNull() ?: return value
        return when {
            key.endsWith("voltage_now") -> String.format(Locale.getDefault(), "%.3f V", numeric / 1_000_000f)
            key.endsWith("current_now") || key.endsWith("input_current_limit") ||
                key.endsWith("constant_charge_current") ->
                String.format(Locale.getDefault(), "%.0f mA", numeric / 1000f)
            key.endsWith("temp") -> String.format(Locale.getDefault(), "%.1f °C", numeric / 10f)
            key.endsWith("charge_counter") || key.endsWith("charge_full") ||
                key.endsWith("charge_full_design") ->
                String.format(Locale.getDefault(), "%.0f mAh", numeric / 1000f)
            key.endsWith("power_now") -> String.format(Locale.getDefault(), "%.2f W", numeric / 1_000_000f)
            else -> value
        }
    }
}
