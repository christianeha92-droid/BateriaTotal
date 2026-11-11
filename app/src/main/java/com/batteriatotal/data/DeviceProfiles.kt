package com.batteriatotal.data

import android.os.Build
import java.util.Locale

/**
 * Valores por defecto para modelos conocidos. La capacidad real se intenta leer
 * antes de /sys/class/power_supply; si HyperOS la bloquea, se usa esta tabla o
 * el valor genérico. El usuario siempre puede cambiarla en Ajustes.
 */
object DeviceProfiles {

    private val knownCapacitiesMah = mapOf(
        // Xiaomi 14 / 14 Pro / 14 Ultra
        "houji" to 4610,
        "shennong" to 4880,
        "aurora" to 5300
    )

    private val knownNames = mapOf(
        "23127pn0cg" to "Xiaomi 14",
        "23116pn5bc" to "Xiaomi 14 Pro",
        "2405cpx3dg" to "Xiaomi 14 Ultra"
    )

    fun defaultCapacityMah(): Int {
        val device = Build.DEVICE.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)
        knownCapacitiesMah[device]?.let { return it }
        return when {
            model == "23127pn0cg" -> 4610
            model == "23116pn5bc" -> 4880
            model == "2405cpx3dg" -> 5300
            else -> SettingsStore.DEFAULT_CAPACITY_MAH
        }
    }

    fun displayName(): String {
        knownNames[Build.MODEL.lowercase(Locale.ROOT)]?.let { return it }
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        return when {
            manufacturer.isBlank() -> model.ifBlank { "Android" }
            model.isBlank() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }
}
