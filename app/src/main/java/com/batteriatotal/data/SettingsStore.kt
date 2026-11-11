package com.batteriatotal.data

import android.content.Context

class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
    private val detectedCapacityMah by lazy { SysfsBattery.designCapacityMah() }

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING, value).apply()

    var samplingSeconds: Int
        get() = prefs.getInt(KEY_SAMPLING, 30).coerceIn(15, 900)
        set(value) = prefs.edit().putInt(KEY_SAMPLING, value.coerceIn(15, 900)).apply()

    var designCapacityMah: Int
        get() {
            val stored = prefs.getInt(KEY_CAPACITY, 0)
            if (stored in 1000..20000) return stored
            return detectedCapacityMah ?: DeviceProfiles.defaultCapacityMah()
        }
        set(value) = prefs.edit().putInt(KEY_CAPACITY, value.coerceIn(1000, 20000)).apply()

    val suggestedCapacityMah: Int?
        get() = detectedCapacityMah

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var gameModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_GAME_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_GAME_MODE, value).apply()

    companion object {
        /** Valor genérico para HyperOS/MIUI cuando no se puede detectar el modelo. */
        const val DEFAULT_CAPACITY_MAH = 5000
        private const val KEY_MONITORING = "monitoring_enabled"
        private const val KEY_SAMPLING = "sampling_seconds"
        private const val KEY_CAPACITY = "design_capacity_mah"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_GAME_MODE = "game_mode_enabled"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
