package com.batteriatotal.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.Permissions
import com.batteriatotal.data.ThemeMode
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.components.AppCard
import com.batteriatotal.ui.components.AppConsumptionItem
import com.batteriatotal.ui.components.BatteryGauge
import com.batteriatotal.ui.components.EmptyState
import com.batteriatotal.ui.components.HardwareNowCard
import com.batteriatotal.ui.components.InfoRow
import com.batteriatotal.ui.components.LinearBar
import com.batteriatotal.ui.components.NoteCard
import com.batteriatotal.ui.components.QuickStatCard
import com.batteriatotal.ui.components.SectionHeader
import com.batteriatotal.ui.components.SetupMonitorCard
import com.batteriatotal.ui.components.StatusPill
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onOpenConsumption: () -> Unit
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val notificationPermission by viewModel.notificationPermission.collectAsStateWithLifecycle()
    val ignoringOptimizations by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val capacity by viewModel.designCapacityMah.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val reading = live.reading
    val setupNeeded = !usageAccess || !notificationPermission || !ignoringOptimizations || !monitoringEnabled

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        if (setupNeeded) {
            item {
                SetupMonitorCard(
                    monitoringEnabled = monitoringEnabled,
                    usageAccess = usageAccess,
                    notificationPermission = notificationPermission,
                    ignoringOptimizations = ignoringOptimizations,
                    onEnableMonitoring = { viewModel.setMonitoringEnabled(true) },
                    onOpenUsageAccess = { Permissions.openUsageAccess(context) },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Permissions.openNotificationSettings(context)
                        }
                    },
                    onRequestBatteryExemption = { Permissions.requestIgnoreBatteryOptimizations(context) },
                    onOpenAutoStart = { Permissions.openAutoStart(context) }
                )
            }
        }

        item {
            LiveHero(
                reading = reading,
                monitoringEnabled = monitoringEnabled,
                dischargeEtaMs = live.dischargeEtaMs,
                chargeEtaMs = live.chargeEtaMs,
                avgDischargeMa = live.avgDischargeMa,
                avgChargeMa = live.avgChargeMa
            )
        }

        dashboard.activeGame?.let { game ->
            item {
                AppCard {
                    SectionHeader(
                        title = "Modo juego · ${game.label}",
                        subtitle = if (game.plugged) {
                            "Cargador conectado · ${Fmt.formatDuration(game.durationMs)}"
                        } else {
                            "Sin cargador · ${Fmt.formatDuration(game.durationMs)}"
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(20.dp).height(20.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    InfoRow(
                        icon = Icons.Default.Bolt,
                        label = "Batería durante la partida",
                        value = "${game.startLevel} % → ${game.endLevel} %",
                        supporting = "Máx ${Fmt.formatTemperature(game.maxTempC)} · " +
                            "media ${Fmt.formatCurrentMa(game.avgCurrentMa)}"
                    )
                    if (game.insufficientCharger) {
                        Text(
                            text = "⚠ La batería está bajando aunque el cargador está conectado. " +
                                "Usa el cargador original o reduce la carga gráfica.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Sesión en curso. HyperOS/MIUI decide la fuente de energía; esta app solo mide.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                val isCharging = reading.isPlugged
                val eta = if (isCharging) live.chargeEtaMs else live.dischargeEtaMs
                QuickStatCard(
                    title = if (isCharging) "Carga restante" else "Autonomía",
                    value = when {
                        reading.status == android.os.BatteryManager.BATTERY_STATUS_FULL -> "Completa"
                        eta != null -> Fmt.formatDuration(eta)
                        else -> "—"
                    },
                    subtitle = if (isCharging) {
                        "Estimación hasta el 100 %"
                    } else if (live.avgDischargeMa != null) {
                        "Según media reciente"
                    } else {
                        "Según corriente actual"
                    },
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    title = "Salud",
                    value = Fmt.healthLabel(reading.health),
                    subtitle = reading.cycleCount?.let { "$it ciclos de carga" } ?: "Ciclos no expuestos",
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                QuickStatCard(
                    title = "Carga actual",
                    value = live.remainingCapacityMah?.let {
                        String.format(Locale.getDefault(), "%.0f mAh", it)
                    } ?: "—",
                    subtitle = "Capacidad de diseño $capacity mAh",
                    icon = Icons.Default.BatteryChargingFull,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary
                )
                QuickStatCard(
                    title = "Voltaje",
                    value = Fmt.formatVoltage(reading.voltageMv),
                    subtitle = "Temp. ${Fmt.formatTemperature(reading.temperatureC)}",
                    icon = Icons.Default.Bolt,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Hoy",
                    subtitle = "Medición real desde que monitorizas"
                )
                val analysis = dashboard.analysis
                val percent = if (capacity > 0) {
                    analysis.dischargeUah / 1000f / capacity * 100f
                } else {
                    0f
                }
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    icon = Icons.Default.PowerSettingsNew,
                    label = "Energía consumida",
                    value = if (analysis.dischargeUah > 0) {
                        "${Fmt.formatMahFromUah(analysis.dischargeUah)} · ${String.format(Locale.getDefault(), "%.1f %%", percent)}"
                    } else "—",
                    supporting = "${analysis.samples} muestras registradas"
                )
                InfoRow(
                    icon = Icons.Default.PhoneAndroid,
                    label = "Tiempo de pantalla",
                    value = Fmt.formatDuration(dashboard.screenOnMs),
                    supporting = "Uso real del dispositivo"
                )
                InfoRow(
                    icon = Icons.Default.Schedule,
                    label = "Tiempo monitorizado",
                    value = Fmt.formatDuration(analysis.monitoredMs),
                    supporting = "Pantalla encendida ${Fmt.formatDuration(analysis.screenOnMs)}"
                )
                InfoRow(
                    icon = Icons.Default.Thermostat,
                    label = "Temperatura",
                    value = Fmt.formatTemperature(analysis.avgTempC),
                    supporting = "Máxima ${Fmt.formatTemperature(analysis.maxTempC)}"
                )
                Text(
                    text = "Última muestra ${Fmt.formatRelative(dashboard.lastSampleTs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Consumo por app · hoy",
                    subtitle = if (usageAccess) {
                        "Reparto de caídas reales entre apps en primer plano"
                    } else {
                        "Concede acceso de uso para identificar aplicaciones"
                    },
                    trailing = {
                        TextButton(onClick = onOpenConsumption) { Text("Ver todo") }
                    }
                )
                when {
                    !usageAccess -> {
                        EmptyState(
                            icon = Icons.Default.Apps,
                            title = "Acceso de uso pendiente",
                            message = "Sin este permiso Android no deja saber qué app está abierta. El resto de datos de batería sigue funcionando."
                        )
                        TextButton(
                            onClick = { Permissions.openUsageAccess(context) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Conceder acceso de uso") }
                    }
                    dashboard.apps.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Apps,
                            title = "Aún sin consumo atribuido",
                            message = "El servicio reparte la batería consumida entre las apps que están en pantalla. Usa el móvil con normalidad y aparecerán aquí."
                        )
                    }
                    else -> {
                        val maxDrain = dashboard.apps.maxOfOrNull { it.drainUah } ?: 0L
                        val totalMeasured = dashboard.analysis.dischargeUah
                        dashboard.apps.forEach { row ->
                            AppConsumptionItem(
                                row = row,
                                maxDrainUah = maxDrain,
                                totalDrainUah = totalMeasured
                            )
                        }
                        if (totalMeasured > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearBar(
                                fraction = dashboard.attributedUah.toFloat() / totalMeasured.toFloat(),
                                color = MaterialTheme.colorScheme.primary,
                                height = 5.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Atribuido ${Fmt.formatMahFromUah(dashboard.attributedUah)} de " +
                                    "${Fmt.formatMahFromUah(totalMeasured)} medidos · " +
                                    "el resto es sistema y reposo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            HardwareNowCard(hardware = live.hardware)
        }

        item {
            NoteCard(
                text = "Android no expone a las apps normales el reparto de energía por componente " +
                    "(CPU, pantalla, radios). Esta app mide el consumo real de la batería y lo cruza " +
                    "con el estado del hardware; el consumo por app es una estimación a partir del " +
                    "tiempo en primer plano y las caídas de nivel."
            )
        }
    }
}

@Composable
private fun LiveHero(
    reading: com.batteriatotal.data.BatteryReading,
    monitoringEnabled: Boolean,
    dischargeEtaMs: Long?,
    chargeEtaMs: Long?,
    avgDischargeMa: Float?,
    avgChargeMa: Float?
) {
    val shape = RoundedCornerShape(30.dp)
    val primary = MaterialTheme.colorScheme.primary
    val gaugeStatus = when {
        reading.status == android.os.BatteryManager.BATTERY_STATUS_FULL -> "Completa"
        reading.isCharging -> "Cargando"
        reading.isPlugged -> "Conectado"
        else -> "Descargando"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = shape
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EN TIEMPO REAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    text = if (monitoringEnabled) "Monitor activo" else "Monitor en pausa",
                    active = monitoringEnabled
                )
            }
            BatteryGauge(
                percent = reading.percent,
                statusLine = gaugeStatus,
                charging = reading.isCharging
            )
            Text(
                text = Fmt.chargingSummary(reading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Actualizado ${Fmt.formatRelative(reading.readAt)} · ${Fmt.pluggedLabel(reading.plugged)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroMetric(
                    label = if (reading.isPlugged) "Potencia de carga" else "Consumo",
                    value = Fmt.formatPower(reading.powerW),
                    supporting = Fmt.formatCurrent(reading.usableCurrentUa),
                    modifier = Modifier.weight(1f)
                )
                HeroDivider()
                HeroMetric(
                    label = "Temperatura",
                    value = Fmt.formatTemperature(reading.temperatureC),
                    supporting = Fmt.healthLabel(reading.health),
                    modifier = Modifier.weight(1f)
                )
                HeroDivider()
                HeroMetric(
                    label = "Voltaje",
                    value = Fmt.formatVoltage(reading.voltageMv),
                    supporting = reading.technology ?: "Tecnología no expuesta",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (reading.isPlugged) Icons.Default.Bolt else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (reading.isPlugged) "Tiempo hasta carga completa" else "Autonomía estimada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (reading.status == android.os.BatteryManager.BATTERY_STATUS_FULL) {
                            "Batería completa"
                        } else {
                            Fmt.formatDuration(if (reading.isPlugged) chargeEtaMs else dischargeEtaMs)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val avg = if (reading.isPlugged) avgChargeMa else avgDischargeMa
                if (avg != null) {
                    StatusPill(
                        text = Fmt.formatCurrentMa(avg),
                        active = false
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(38.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
