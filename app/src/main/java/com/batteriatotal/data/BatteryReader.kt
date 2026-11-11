package com.batteriatotal.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale

class BatteryReader(context: Context) {

    private val appContext = context.applicationContext
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    fun read(): BatteryReading {
        val sticky = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val extras = sticky?.extras

        val level = extras?.getInt(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
            ?: intProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: 0
        val scale = extras?.getInt(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val status = extras?.getInt(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val health = extras?.getInt(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val plugged = extras?.getInt(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val present = extras?.getBoolean(BatteryManager.EXTRA_PRESENT, true) ?: true
        val voltageMv = extras?.getInt(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val temperatureRaw = extras?.getInt(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperatureC = temperatureRaw
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10f }
        val technology = extras?.getString(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }

        val chargeCounterFromExtra = extras?.getInt("charge_counter", Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
            ?.toLong()
        val chargeCounterUah = chargeCounterFromExtra
            ?: extraLong(extras, "charge_counter")?.takeIf { it > 0 }
            ?: intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toLong()?.takeIf { it > 0 }

        val currentNowUa = longProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != 0L }
        val currentAverageUa = longProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.takeIf { it != 0L }
        val energyCounterNwh = longProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            ?.takeIf { it > 0L }
        val capacityProperty = intProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val cycleCount = extras?.getInt("cycle_count", -1)?.takeIf { it > 0 }
        // Estos extras son propios del fabricante y pueden venir en mA/mV o en
        // µA/µV. Se normalizan a µA/µV para mostrarlos siempre bien.
        val maxChargingCurrentRaw = extras?.getInt("max_charging_current", -1)?.takeIf { it > 0 }
        val maxChargingCurrent = maxChargingCurrentRaw?.let { raw ->
            if (raw <= 30_000) raw * 1000L else raw.toLong()
        }
        val maxChargingVoltageRaw = extras?.getInt("max_charging_voltage", -1)?.takeIf { it > 0 }
        val maxChargingVoltage = maxChargingVoltageRaw?.let { raw ->
            if (raw <= 30_000) raw * 1000L else raw.toLong()
        }
        val chargingStatus = extras?.getInt("charging_status", -1)?.takeIf { it >= 0 }

        val rawExtras = LinkedHashMap<String, String>()
        extras?.keySet()?.sorted()?.forEach { key ->
            val value = runCatching { extras.get(key) }.getOrNull()
            rawExtras[key] = formatExtraValue(value)
        }

        val properties = listOf(
            propertyValue(
                "BatteryManager.CAPACITY",
                "Capacidad del sistema",
                capacityProperty?.toString(),
                capacityProperty != null,
                "%"
            ),
            propertyValue(
                "BatteryManager.CHARGE_COUNTER",
                "Contador de carga",
                chargeCounterUah?.toString(),
                chargeCounterUah != null,
                "µAh"
            ),
            propertyValue(
                "BatteryManager.CURRENT_NOW",
                "Corriente instantánea",
                currentNowUa?.toString(),
                currentNowUa != null,
                "µA"
            ),
            propertyValue(
                "BatteryManager.CURRENT_AVERAGE",
                "Corriente media",
                currentAverageUa?.toString(),
                currentAverageUa != null,
                "µA"
            ),
            propertyValue(
                "BatteryManager.ENERGY_COUNTER",
                "Contador de energía",
                energyCounterNwh?.toString(),
                energyCounterNwh != null,
                "nWh"
            )
        )

        return BatteryReading(
            readAt = System.currentTimeMillis(),
            level = level,
            scale = scale,
            status = status,
            health = health,
            plugged = plugged,
            present = present,
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            technology = technology,
            chargeCounterUah = chargeCounterUah,
            currentNowUa = currentNowUa,
            currentAverageUa = currentAverageUa,
            energyCounterNwh = energyCounterNwh,
            capacityProperty = capacityProperty,
            cycleCount = cycleCount,
            maxChargingCurrentUa = maxChargingCurrent,
            maxChargingVoltageUv = maxChargingVoltage,
            chargingStatus = chargingStatus,
            rawExtras = rawExtras,
            properties = properties
        )
    }

    private fun intProperty(property: Int): Int? {
        val manager = batteryManager ?: return null
        return runCatching {
            manager.getIntProperty(property).takeIf { it != Int.MIN_VALUE }
        }.getOrNull()
    }

    private fun longProperty(property: Int): Long? {
        val manager = batteryManager ?: return null
        return runCatching {
            manager.getLongProperty(property).takeIf { it != Long.MIN_VALUE }
        }.getOrNull()
    }

    private fun extraLong(extras: android.os.Bundle?, key: String): Long? {
        if (extras == null) return null
        return runCatching {
            when (val value = extras.get(key)) {
                is Long -> value
                is Int -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }.getOrNull()
    }

    private fun propertyValue(
        key: String,
        label: String,
        value: String?,
        supported: Boolean,
        unit: String
    ) = PropertyValue(key = key, label = label, value = value, supported = supported, unit = unit)

    private fun formatExtraValue(value: Any?): String {
        val text = when (value) {
            null -> "null"
            is IntArray -> value.joinToString(", ")
            is LongArray -> value.joinToString(", ")
            is FloatArray -> value.joinToString(", ")
            is DoubleArray -> value.joinToString(", ")
            is BooleanArray -> value.joinToString(", ")
            is ByteArray -> value.joinToString(", ") { String.format(Locale.US, "%02X", it) }
            is Array<*> -> value.joinToString(", ") { formatExtraValue(it) }
            is CharSequence -> value.toString()
            else -> value.toString()
        }
        return if (text.length > 400) text.take(400) + "…" else text
    }
}
