package com.batteriatotal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.batteriatotal.data.BatteryReading
import com.batteriatotal.data.BatteryRepository
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.GameSession
import com.batteriatotal.data.Permissions
import com.batteriatotal.data.SettingsStore
import com.batteriatotal.monitor.BatteryMonitorService

class BatteryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        SettingsStore.get(this)
        BatteryRepository.get(this)
    }
}

object Notifications {
    const val CHANNEL_MONITOR = "battery_monitor"
    const val CHANNEL_GAME = "game_mode"
    const val NOTIFICATION_MONITOR = 41
    const val NOTIFICATION_GAME = 42
    const val NOTIFICATION_GAME_ALERT = 43

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_MONITOR,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)

            val gameChannel = NotificationChannel(
                CHANNEL_GAME,
                "Modo juego",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de batería y temperatura mientras juegas"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(gameChannel)
        }
    }

    fun monitorNotification(context: Context, reading: BatteryReading): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val status = when {
            reading.status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                reading.status == android.os.BatteryManager.BATTERY_STATUS_FULL ->
                "Cargando · ${Fmt.formatPower(reading.powerW)}"
            reading.isPlugged -> "Conectado · ${Fmt.formatPower(reading.powerW)}"
            else -> "Consumo ${Fmt.formatPower(reading.powerW)} · ${Fmt.formatTemperature(reading.temperatureC)}"
        }
        val attribution = if (hasUsageAccess(context)) {
            "Análisis por app activo"
        } else {
            "Concede acceso de uso para analizar apps"
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setColor(0xFF62D88A.toInt())
            .setContentTitle("Batería ${reading.percent} % · ${Fmt.statusLabel(reading.status)}")
            .setContentText(status)
            .setSubText(attribution)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateGame(context: Context, session: GameSession, reading: BatteryReading) {
        if (!hasNotificationPermission(context)) return
        val contentIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val chargeIntent = PendingIntent.getActivity(
            context,
            11,
            Permissions.chargeSettingsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val gameTurboIntent = PendingIntent.getActivity(
            context,
            12,
            Permissions.gameTurboIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val connection = if (session.plugged) "Cargador conectado" else "Sin cargador"
        val warning = if (session.insufficientCharger) " · ⚠ Cargador insuficiente" else ""
        val title = "Modo juego · ${session.label}"
        val text = "$connection · ${reading.percent} % · ${Fmt.formatTemperature(reading.temperatureC)}$warning"
        val support = buildString {
            append("Corriente media ${Fmt.formatCurrentMa(session.avgCurrentMa)}")
            append(" · máx ${Fmt.formatTemperature(session.maxTempC)}")
            if (session.durationMs > 0) append(" · ${Fmt.formatDuration(session.durationMs)}")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_GAME)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setColor(0xFF62D88A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(support)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stat_battery, "Opciones de carga", chargeIntent)
            .addAction(R.drawable.ic_stat_battery, "Game Turbo", gameTurboIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_GAME, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyChargerWarning(context: Context, session: GameSession) {
        if (!hasNotificationPermission(context)) return
        val contentIntent = PendingIntent.getActivity(
            context,
            13,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_GAME)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setColor(0xFFFFA726.toInt())
            .setContentTitle("Cargador insuficiente")
            .setContentText(
                "La batería baja mientras juegas enchufado. Usa el cargador original " +
                    "o reduce la carga gráfica."
            )
            .setSubText(session.label)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_GAME_ALERT, notification)
        }
    }

    fun cancelGame(context: Context) {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(NOTIFICATION_GAME)
            manager.cancel(NOTIFICATION_GAME_ALERT)
        }
    }

    fun updateMonitor(context: Context, reading: BatteryReading) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_MONITOR, monitorNotification(context, reading))
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageAccess(context: Context): Boolean =
        Permissions.hasUsageAccess(context)
}
