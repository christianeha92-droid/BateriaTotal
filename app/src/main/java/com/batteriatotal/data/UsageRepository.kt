package com.batteriatotal.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo

class UsageRepository(context: Context) {

    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    data class UsageSnapshot(
        val apps: List<AppUsageInfo>,
        val screenOnMs: Long?,
        val hasAccess: Boolean
    )

    data class AppMeta(val label: String, val isSystem: Boolean)

    fun hasAccess(): Boolean = Permissions.hasUsageAccess(appContext)

    fun load(startTs: Long, endTs: Long, includeSystem: Boolean): UsageSnapshot {
        if (!hasAccess() || usageStatsManager == null) {
            return UsageSnapshot(emptyList(), null, false)
        }
        val manager = usageStatsManager ?: return UsageSnapshot(emptyList(), null, false)
        val stats = runCatching {
            manager.queryAndAggregateUsageStats(startTs, endTs)
        }.getOrDefault(emptyMap())
        val launchCounts = countLaunches(manager, startTs, endTs)

        val apps = ArrayList<AppUsageInfo>()
        stats.forEach { (packageName, usageStats) ->
            if (packageName.isNullOrBlank()) return@forEach
            val foregroundMs = usageStats.totalTimeInForeground
            val launches = launchCounts[packageName] ?: 0
            if (foregroundMs <= 0L && launches == 0) return@forEach
            val meta = resolve(packageName) ?: AppMeta(packageName, false)
            if (!includeSystem && meta.isSystem) return@forEach
            apps.add(
                AppUsageInfo(
                    packageName = packageName,
                    label = meta.label,
                    isSystem = meta.isSystem,
                    foregroundMs = foregroundMs,
                    launches = launches,
                    lastUsed = usageStats.lastTimeUsed
                )
            )
        }
        apps.sortByDescending { it.foregroundMs }
        return UsageSnapshot(apps, screenOnTime(startTs, endTs), true)
    }

    fun resolve(packageName: String): AppMeta? {
        val pm = appContext.packageManager
        return runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info).toString()
            val isSystem = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            AppMeta(label, isSystem)
        }.getOrNull()
    }

    /**
     * Número de veces que cada app pasó a primer plano dentro del periodo.
     * Se calcula con eventos porque UsageStats no expone un contador público
     * de aperturas en todas las versiones de Android.
     */
    private fun countLaunches(
        manager: UsageStatsManager,
        startTs: Long,
        endTs: Long
    ): Map<String, Int> {
        val events = runCatching { manager.queryEvents(startTs, endTs) }.getOrNull()
            ?: return emptyMap()
        val event = UsageEvents.Event()
        val counts = HashMap<String, Int>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val packageName = event.packageName
                if (!packageName.isNullOrBlank()) {
                    counts[packageName] = (counts[packageName] ?: 0) + 1
                }
            }
        }
        return counts
    }

    /**
     * Tiempo de pantalla encendida dentro del periodo usando los eventos del
     * sistema. Requiere el mismo acceso de uso. Si la pantalla ya estaba
     * encendida al empezar, se cuenta desde el primer evento disponible.
     */
    fun screenOnTime(startTs: Long, endTs: Long): Long? {
        if (!hasAccess() || usageStatsManager == null) return null
        val manager = usageStatsManager ?: return null
        val events = runCatching { manager.queryEvents(startTs, endTs) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var screenOn = false
        var lastTs = startTs
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(startTs, endTs)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!screenOn) {
                        screenOn = true
                        lastTs = ts
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (screenOn) {
                        total += (ts - lastTs).coerceAtLeast(0L)
                        screenOn = false
                    }
                    lastTs = ts
                }
            }
        }
        if (screenOn) total += (endTs - lastTs).coerceAtLeast(0L)
        return total
    }
}
