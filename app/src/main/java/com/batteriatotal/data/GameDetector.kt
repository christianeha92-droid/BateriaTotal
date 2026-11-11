package com.batteriatotal.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Detector de juegos basado en UsageStats. Android no tiene una API para
 * preguntar "¿hay un juego en primer plano?", así que se usa el último
 * ACTIVITY_RESUMED y la categoría declarada por la app (CATEGORY_GAME).
 *
 * Es una detección best-effort: si un juego no declara `android:appCategory`
 * como "game", no se reconocerá. No se usan listas privadas de Game Turbo.
 */
class GameDetector(context: Context) {

    data class GameApp(val packageName: String, val label: String)

    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = appContext.packageManager
    private val cache = HashMap<String, GameApp?>()

    private var lastLookupAt = 0L
    private var lastResult: GameApp? = null

    fun currentGame(now: Long = System.currentTimeMillis()): GameApp? {
        if (!Permissions.hasUsageAccess(appContext)) return null
        if (now - lastLookupAt < LOOKUP_CACHE_MS) return lastResult
        lastLookupAt = now
        val manager = usageStatsManager ?: return null
        val events = runCatching {
            manager.queryEvents((now - LOOKBACK_MS).coerceAtLeast(0L), now)
        }.getOrNull() ?: return lastResult
        val event = UsageEvents.Event()
        var foregroundPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foregroundPackage = event.packageName
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    foregroundPackage = null
                }
            }
        }
        lastResult = foregroundPackage?.let { resolveGame(it) }
        return lastResult
    }

    private fun resolveGame(packageName: String): GameApp? {
        if (cache.containsKey(packageName)) return cache[packageName]
        val app = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            if (info.category != ApplicationInfo.CATEGORY_GAME) {
                null
            } else {
                val label = packageManager.getApplicationLabel(info).toString()
                GameApp(packageName, label)
            }
        }.getOrNull()
        cache[packageName] = app
        return app
    }

    companion object {
        private const val LOOKBACK_MS = 6L * 60L * 60L * 1000L
        private const val LOOKUP_CACHE_MS = 10_000L
    }
}
