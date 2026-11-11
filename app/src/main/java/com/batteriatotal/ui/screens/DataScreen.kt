package com.batteriatotal.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.data.BatteryReading
import com.batteriatotal.data.DeviceInfo
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.SensorInfo
import com.batteriatotal.data.SysfsBattery
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.components.AppCard
import com.batteriatotal.ui.components.InfoRow
import com.batteriatotal.ui.components.NoteCard
import com.batteriatotal.ui.components.SectionHeader
import com.batteriatotal.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataScreen(viewModel: AppViewModel) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val sensors by viewModel.sensors.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val designCapacity by viewModel.designCapacityMah.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reading = live.reading
    var showAllSensors by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            AppCard {
                SectionHeader(
                    title = "Batería en tiempo real",
                    subtitle = "Datos publicados por Android y el fabricante"
                )
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Nivel y estado",
                    value = "${reading.percent} % · ${Fmt.statusLabel(reading.status)}",
                    supporting = "${Fmt.pluggedLabel(reading.plugged)} · ${if (reading.present) "Batería presente" else "Sin batería"}"
                )
                InfoRow(
                    icon = Icons.Default.Bolt,
                    label = "Corriente y potencia",
                    value = Fmt.formatCurrent(reading.usableCurrentUa),
                    supporting = "Potencia ${Fmt.formatPower(reading.powerW)} · Voltaje ${Fmt.formatVoltage(reading.voltageMv)}"
                )
                InfoRow(
                    icon = Icons.Default.Thermostat,
                    label = "Temperatura",
                    value = Fmt.formatTemperature(reading.temperatureC),
                    supporting = "Salud: ${Fmt.healthLabel(reading.health)}"
                )
                InfoRow(
                    icon = Icons.Default.Memory,
                    label = "Contador de carga",
                    value = Fmt.formatChargePrecise(reading.chargeCounterUah),
                    supporting = "Energía ${Fmt.formatEnergy(reading.energyCounterNwh)} · diseño $designCapacity mAh"
                )
                InfoRow(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Ciclos de carga",
                    value = reading.cycleCount?.toString() ?: "No expuesto",
                    supporting = "Tecnología ${reading.technology ?: "no expuesta"}"
                )
                InfoRow(
                    icon = Icons.Default.Bolt,
                    label = "Carga máxima declarada",
                    value = reading.maxChargingCurrentUa?.let { Fmt.formatCurrent(it) } ?: "—",
                    supporting = "Voltaje máx. ${reading.maxChargingVoltageUv?.let { Fmt.formatVoltage((it / 1000).toInt()) } ?: "—"}" +
                        (reading.chargingStatus?.let { " · estado $it" } ?: "")
                )
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Propiedades BatteryManager",
                    subtitle = "Soporte real del dispositivo"
                )
                Spacer(Modifier.height(6.dp))
                reading.properties.forEach { property ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = property.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = property.key,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (property.supported) {
                                "${property.value} ${property.unit}"
                            } else {
                                "No soportado"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (property.supported) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Capacidades detectadas",
                    subtitle = "Lo que el sistema permite leer"
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill("Temperatura", reading.temperatureC != null)
                    StatusPill("Voltaje", reading.voltageMv != null)
                    StatusPill("Corriente", reading.usableCurrentUa != null)
                    StatusPill("Contador de carga", reading.chargeCounterUah != null)
                    StatusPill("Contador de energía", reading.energyCounterNwh != null)
                    StatusPill("Ciclos", reading.cycleCount != null)
                    StatusPill("Acceso de uso", usageAccess)
                    StatusPill("Monitor activo", monitoringEnabled)
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Extras de ACTION_BATTERY_CHANGED",
                    subtitle = "${reading.rawExtras.size} claves publicadas por el sistema"
                )
                Spacer(Modifier.height(8.dp))
                if (reading.rawExtras.isEmpty()) {
                    Text(
                        text = "El fabricante no publica extras en este dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    reading.rawExtras.entries.forEachIndexed { index, entry ->
                        RawExtraRow(
                            key = entry.key,
                            value = entry.value,
                            label = Fmt.extraLabel(entry.key)
                        )
                        if (index != reading.rawExtras.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Se muestran todas las claves del broadcast, incluidas las propias del fabricante " +
                        "si HyperOS/MIUI las expone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Dispositivo y sistema",
                    subtitle = "Identificación del teléfono y del sistema"
                )
                Spacer(Modifier.height(6.dp))
                if (deviceInfo == null) {
                    Text(
                        text = "Leyendo información del sistema…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DeviceInfoBody(deviceInfo!!)
                }
            }
        }

        if (deviceInfo != null) {
            val info = deviceInfo!!
            if (info.systemProps.isNotEmpty()) {
                item {
                    AppCard {
                        SectionHeader(
                            title = "Propiedades getprop",
                            subtitle = "Valores leídos del sistema"
                        )
                        Spacer(Modifier.height(6.dp))
                        info.systemProps.entries.forEachIndexed { index, entry ->
                            RawExtraRow(
                                key = entry.key,
                                value = entry.value,
                                label = Fmt.humanizeKey(entry.key.substringAfterLast('.'))
                            )
                            if (index != info.systemProps.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
            item {
                AppCard {
                    SectionHeader(
                        title = "Batería desde el kernel",
                        subtitle = "sysfs · puede estar bloqueado por SELinux"
                    )
                    Spacer(Modifier.height(8.dp))
                    if (info.kernelBattery.isEmpty()) {
                        Text(
                            text = "No accesible para una app normal en este dispositivo. HyperOS limita /sys/class/power_supply; los datos principales siguen llegando por BatteryManager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        info.kernelBattery.entries.forEachIndexed { index, entry ->
                            InfoRow(
                                label = Fmt.humanizeKey(entry.key.substringAfter('.')),
                                value = SysfsBattery.formatValue(entry.key, entry.value),
                                supporting = entry.key
                            )
                            if (index != info.kernelBattery.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Sensores",
                    subtitle = "${sensors.size} sensores · ${sensors.count { it.wakeUp }} de impulso"
                )
                Spacer(Modifier.height(8.dp))
                if (sensors.isEmpty()) {
                    Text(
                        text = "El sistema no devolvió la lista de sensores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val visible = if (showAllSensors) sensors else sensors.take(6)
                    visible.forEachIndexed { index, sensor ->
                        SensorRow(sensor)
                        if (index != visible.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                    if (sensors.size > 6) {
                        TextButton(onClick = { showAllSensors = !showAllSensors }) {
                            Text(if (showAllSensors) "Mostrar menos" else "Mostrar los ${sensors.size}")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "La potencia es la declarada por el fabricante para uso activo; no indica si el sensor está encendido ahora.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Informe completo",
                    subtitle = "Comparte todos los datos en texto"
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Incluye batería, extras, propiedades de Android, HyperOS/MIUI, kernel y sensores. " +
                        "Útil para diagnosticar tu Xiaomi/Redmi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val report = buildReport(
                            reading = reading,
                            deviceInfo = deviceInfo,
                            sensors = sensors,
                            usageAccess = usageAccess,
                            monitoringEnabled = monitoringEnabled,
                            designCapacity = designCapacity
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Informe de Batería Total")
                            putExtra(Intent.EXTRA_TEXT, report)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir informe"))
                    }
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Compartir informe")
                }
            }
        }

        item {
            NoteCard(
                text = "Esta app es local: no tiene permiso de Internet ni envía datos. Algunos valores, como el " +
                "reparto de energía por componente o el estado de salud exacto de la batería, solo los conoce " +
                "el sistema privilegiado del fabricante."
            )
        }
    }
}

@Composable
private fun RawExtraRow(key: String, value: String, label: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceInfoBody(info: DeviceInfo) {
    InfoRow(
        icon = Icons.Default.PhoneAndroid,
        label = "${info.manufacturer} ${info.model}",
        value = "Android ${info.androidRelease}",
        supporting = "Marca ${info.brand} · dispositivo ${info.device} · producto ${info.product}"
    )
    val systemValue = info.hyperOsVersion
        ?: info.miuiVersion?.let { "MIUI $it" }
        ?: "No expuesto"
    val systemSupporting = when {
        info.hyperOsVersion != null && info.miuiVersion != null -> "MIUI ${info.miuiVersion}"
        info.hyperOsVersion != null -> "Capa HyperOS"
        info.miuiVersion != null -> "Capa MIUI"
        else -> "El fabricante no publica la versión"
    }
    InfoRow(
        icon = Icons.Default.BatteryChargingFull,
        label = "HyperOS / MIUI",
        value = systemValue,
        supporting = systemSupporting
    )
    InfoRow(
        icon = Icons.Default.Memory,
        label = "SoC y ABI",
        value = info.soc ?: "—",
        supporting = info.abi ?: "—"
    )
    InfoRow(
        icon = Icons.Default.PhoneAndroid,
        label = "Compilación",
        value = "API ${info.sdk}",
        supporting = "Parche de seguridad ${info.securityPatch} · Build ${info.buildId}"
    )
    Text(
        text = "Fingerprint: ${info.fingerprint}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SensorRow(sensor: SensorInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${sensor.typeName} · ${sensor.vendor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = if (sensor.powerMa > 0f) {
                        String.format(Locale.getDefault(), "%.2f mA", sensor.powerMa)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (sensor.wakeUp) "wake-up" else "no wake-up",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildReport(
    reading: BatteryReading,
    deviceInfo: DeviceInfo?,
    sensors: List<SensorInfo>,
    usageAccess: Boolean,
    monitoringEnabled: Boolean,
    designCapacity: Int
): String = buildString {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    appendLine("Batería Total · informe")
    appendLine("Fecha: $date")
    appendLine()
    appendLine("== BATERÍA ==")
    appendLine("Nivel: ${reading.percent} % | Estado: ${Fmt.statusLabel(reading.status)} | Salud: ${Fmt.healthLabel(reading.health)}")
    appendLine("Fuente: ${Fmt.pluggedLabel(reading.plugged)} | Tecnología: ${reading.technology ?: "—"}")
    appendLine("Voltaje: ${Fmt.formatVoltage(reading.voltageMv)} | Corriente: ${Fmt.formatCurrent(reading.usableCurrentUa)} | Potencia: ${Fmt.formatPower(reading.powerW)}")
    appendLine("Temperatura: ${Fmt.formatTemperature(reading.temperatureC)} | Ciclos: ${reading.cycleCount ?: "no expuesto"}")
    appendLine("Contador de carga: ${Fmt.formatChargePrecise(reading.chargeCounterUah)} | Energía: ${Fmt.formatEnergy(reading.energyCounterNwh)}")
    appendLine("Capacidad de diseño configurada: $designCapacity mAh")
    appendLine("Acceso de uso: $usageAccess | Monitorización: $monitoringEnabled")
    appendLine()
    appendLine("== EXTRAS ACTION_BATTERY_CHANGED (${reading.rawExtras.size}) ==")
    reading.rawExtras.forEach { (key, value) -> appendLine("$key = $value") }
    appendLine()
    appendLine("== PROPIEDADES BATTERYMANAGER ==")
    reading.properties.forEach { property ->
        appendLine("${property.key} (${property.label}) = ${if (property.supported) "${property.value} ${property.unit}" else "no soportado"}")
    }
    appendLine()
    if (deviceInfo != null) {
        appendLine("== DISPOSITIVO ==")
        appendLine("${deviceInfo.manufacturer} ${deviceInfo.model} (${deviceInfo.brand}/${deviceInfo.device}/${deviceInfo.product})")
        appendLine("Android ${deviceInfo.androidRelease} · API ${deviceInfo.sdk} · parche ${deviceInfo.securityPatch}")
        appendLine("HyperOS: ${deviceInfo.hyperOsVersion ?: "no expuesto"} · MIUI: ${deviceInfo.miuiVersion ?: "—"}")
        appendLine("SoC: ${deviceInfo.soc ?: "—"} · ABI: ${deviceInfo.abi ?: "—"}")
        appendLine("Build: ${deviceInfo.buildId} · ${deviceInfo.fingerprint}")
        appendLine()
        appendLine("== GETPROP ==")
        deviceInfo.systemProps.forEach { (key, value) -> appendLine("$key = $value") }
        appendLine()
        appendLine("== KERNEL / SYSFS ==")
        if (deviceInfo.kernelBattery.isEmpty()) {
            appendLine("No accesible por SELinux")
        } else {
            deviceInfo.kernelBattery.forEach { (key, value) ->
                appendLine("$key = ${SysfsBattery.formatValue(key, value)}")
            }
        }
        appendLine()
    }
    appendLine("== SENSORES (${sensors.size}) ==")
    sensors.forEach { sensor ->
        appendLine("${sensor.name} | ${sensor.typeName} | ${sensor.vendor} | ${String.format(Locale.US, "%.2f", sensor.powerMa)} mA | ${if (sensor.wakeUp) "wake-up" else "no wake-up"}")
    }
    appendLine()
    appendLine("Los valores de consumo por app y por estado son estimaciones basadas en datos públicos de Android.")
}
