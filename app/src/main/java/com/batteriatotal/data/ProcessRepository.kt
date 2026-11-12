package com.batteriatotal.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import java.util.Locale

/**
 * Lista las apps con actividad reciente y estima si están en primer o segundo
 * plano.
 *
 * Android no ofrece a las apps normales la lista real de procesos de otras
 * aplicaciones (`getRunningAppProcesses` solo devuelve los propios desde
 * Android 5.1). Por eso el estado se deduce de los eventos públicos de
 * `UsageStatsManager`:
 *
 * - ACTIVITY_RESUMED / MOVE_TO_FOREGROUND: la app pasa a primer plano.
 * - ACTIVITY_PAUSED / MOVE_TO_BACKGROUND: deja el foco o pasa a segundo plano.
 * - SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE: con la pantalla apagada no
 *   hay ninguna app en primer plano.
 *
 * El consumo asociado es el que ya calcula el monitor a partir de caídas
 * reales de batería y tiempo en primer plano; es una estimación.
 */
class ProcessRepository(context: Context) {

    private val appContext = context.applicationContext
    private val usageRepository = UsageRepository(appContext)
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val packageManager = appContext.packageManager

    data class ProcessSnapshot(
        val rows: List<ProcessRow>,
        val hasAccess: Boolean,
        val foregroundPackage: String?,
        val generatedAt: Long
    )

    private data class EventSummary(
        val currentForeground: String?,
        val lastEventAt: Map<String, Long>,
        val screenInteractive: Boolean
    )

    fun hasAccess(): Boolean = Permissions.hasUsageAccess(appContext)

    fun load(now: Long, drains: Map<String, Long>): ProcessSnapshot {
        val manager = usageStatsManager
        if (!hasAccess() || manager == null) {
            return ProcessSnapshot(emptyList(), false, null, now)
        }

        val windowStart = maxOf(
            now - SystemClock.elapsedRealtime(),
            now - WINDOW_MS
        )
        val stats = runCatching {
            manager.queryAndAggregateUsageStats(windowStart, now)
        }.getOrDefault(emptyMap())
        val events = readEvents(manager, windowStart, now)
        val foreground = if (events.screenInteractive) events.currentForeground else null
        val launchers = launcherPackages()

        val packages = LinkedHashSet<String>()
        packages.addAll(stats.keys)
        packages.addAll(events.lastEventAt.keys)
        packages.addAll(drains.keys)
        packages.remove("")

        val rows = packages.mapNotNull { packageName ->
            if (packageName.isNullOrBlank()) return@mapNotNull null
            val meta = usageRepository.resolve(packageName)
                ?: UsageRepository.AppMeta(packageName, false)
            val usage = stats[packageName]
            val lastEventAt = events.lastEventAt[packageName] ?: 0L
            val lastUsed = maxOf(usage?.lastTimeUsed ?: 0L, lastEventAt)
            val lastActivityAt = maxOf(lastEventAt, lastUsed)
            val drainUah = drains[packageName] ?: 0L
            // Sin eventos ni consumo no hay nada que mostrar: evita listar
            // componentes instalados que nunca se han usado.
            if (lastActivityAt <= 0L && drainUah <= 0L) return@mapNotNull null
            val state = when {
                packageName == foreground -> AppProcessState.FOREGROUND
                lastActivityAt <= 0L -> AppProcessState.UNKNOWN
                now - lastActivityAt <= RECENT_BACKGROUND_MS -> AppProcessState.BACKGROUND
                else -> AppProcessState.CACHED
            }
            val isSelf = packageName == appContext.packageName
            val isLauncher = packageName in launchers
            val isProtected = isSelf || isLauncher || packageName in protectedPackages
            val stopMode = when {
                isProtected -> ProcessStopMode.NONE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ProcessStopMode.MANUAL
                state == AppProcessState.FOREGROUND || state == AppProcessState.VISIBLE ->
                    ProcessStopMode.MANUAL
                else -> ProcessStopMode.DIRECT
            }
            ProcessRow(
                packageName = packageName,
                label = meta.label,
                isSystem = meta.isSystem,
                state = state,
                foregroundMs = usage?.totalTimeInForeground ?: 0L,
                lastActivityAt = lastActivityAt,
                drainUah = drainUah,
                isSelf = isSelf,
                isLauncher = isLauncher,
                stopMode = stopMode
            )
        }.sortedWith(
            compareBy<ProcessRow> { stateRank(it.state) }
                .thenByDescending { it.drainUah }
                .thenByDescending { it.foregroundMs }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
        )

        return ProcessSnapshot(
            rows = rows,
            hasAccess = true,
            foregroundPackage = foreground,
            generatedAt = now
        )
    }

    @Suppress("DEPRECATION")
    private fun readEvents(
        manager: UsageStatsManager,
        startTs: Long,
        endTs: Long
    ): EventSummary {
        val lastEventAt = HashMap<String, Long>()
        var currentForeground: String? = null
        var currentForegroundAt = 0L
        var screenInteractive = powerManager?.isInteractive ?: true

        val events = runCatching { manager.queryEvents(startTs, endTs) }.getOrNull()
            ?: return EventSummary(
                currentForeground = null,
                lastEventAt = lastEventAt,
                screenInteractive = screenInteractive
            )
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName
            val ts = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (!packageName.isNullOrBlank()) {
                        lastEventAt[packageName] = maxOf(lastEventAt[packageName] ?: 0L, ts)
                        if (ts >= currentForegroundAt) {
                            currentForeground = packageName
                            currentForegroundAt = ts
                        }
                    }
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (!packageName.isNullOrBlank()) {
                        lastEventAt[packageName] = maxOf(lastEventAt[packageName] ?: 0L, ts)
                        if (packageName == currentForeground) currentForeground = null
                    }
                }

                UsageEvents.Event.SCREEN_INTERACTIVE -> screenInteractive = true

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    screenInteractive = false
                    currentForeground = null
                }
            }
        }

        if (powerManager?.isInteractive == false) {
            screenInteractive = false
            currentForeground = null
        }
        return EventSummary(currentForeground, lastEventAt, screenInteractive)
    }

    @Suppress("DEPRECATION")
    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val current = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
        if (!current.isNullOrBlank()) return setOf(current)
        return runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .filterNot { it.contains("FallbackHome", ignoreCase = true) }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun stateRank(state: AppProcessState): Int = when (state) {
        AppProcessState.FOREGROUND -> 0
        AppProcessState.VISIBLE -> 1
        AppProcessState.SERVICE -> 2
        AppProcessState.BACKGROUND -> 3
        AppProcessState.CACHED -> 4
        AppProcessState.UNKNOWN -> 5
    }

    private companion object {
        const val WINDOW_MS = 24L * 60L * 60L * 1000L
        const val RECENT_BACKGROUND_MS = 30L * 60L * 1000L

        /** Apps que el sistema necesita para funcionar; no se ofrece detenerlas. */
        val protectedPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.miui.securitycenter"
        )
    }
}
