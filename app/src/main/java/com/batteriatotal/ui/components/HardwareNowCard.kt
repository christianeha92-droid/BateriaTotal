package com.batteriatotal.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.HardwareState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareNowCard(
    hardware: HardwareState,
    modifier: Modifier = Modifier
) {
    val usedRam = (hardware.memTotalBytes - hardware.memAvailableBytes).coerceAtLeast(0L)
    val maxZone = hardware.thermalZones.maxByOrNull { it.temperatureC }
    val refresh = if (hardware.refreshRateHz > 0f) {
        String.format(Locale.getDefault(), "%.0f Hz", hardware.refreshRateHz)
    } else {
        "—"
    }
    val brightness = Fmt.formatBrightness(hardware.brightness, hardware.autoBrightness)
    val cpuLoad = hardware.cpuLoadPercent?.let {
        String.format(Locale.getDefault(), "%.0f %%", it)
    } ?: "—"

    AppCard(modifier = modifier) {
        SectionHeader(
            title = "Hardware ahora",
            subtitle = "Estado del sistema en tiempo real"
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HardwareTile(
                icon = Icons.Default.Speed,
                label = "CPU",
                value = cpuLoad,
                supporting = Fmt.formatCpuFrequencies(hardware.cpuFrequenciesMhz),
                modifier = Modifier.weight(1f)
            )
            HardwareTile(
                icon = Icons.Default.Memory,
                label = "Memoria",
                value = if (hardware.memTotalBytes > 0) Fmt.formatBytes(usedRam) else "—",
                supporting = if (hardware.memTotalBytes > 0) {
                    "de ${Fmt.formatBytes(hardware.memTotalBytes)}"
                } else "No disponible",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HardwareTile(
                icon = Icons.Default.PhoneAndroid,
                label = "Pantalla",
                value = if (hardware.screenOn) "Encendida" else "Apagada",
                supporting = "$refresh · Brillo $brightness",
                modifier = Modifier.weight(1f)
            )
            HardwareTile(
                icon = Icons.Default.Wifi,
                label = "Red",
                value = hardware.networkLabel,
                supporting = "WiFi ${if (hardware.wifiEnabled) "sí" else "no"} · Móvil ${if (hardware.mobileDataActive) "sí" else "no"}",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HardwareTile(
                icon = Icons.Default.Thermostat,
                label = "Térmica",
                value = hardware.thermalStatus,
                supporting = maxZone?.let {
                    "${it.name}: ${Fmt.formatTemperature(it.temperatureC)}"
                } ?: "Sin zonas accesibles",
                modifier = Modifier.weight(1f)
            )
            HardwareTile(
                icon = Icons.Default.Sensors,
                label = "Sensores",
                value = "${hardware.sensorCount}",
                supporting = "${hardware.wakeSensorCount} de impulso",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill("WiFi", hardware.wifiEnabled)
            StatusPill("Datos", hardware.mobileDataActive)
            StatusPill("Bluetooth", hardware.bluetoothEnabled == true)
            StatusPill("GPS", hardware.gpsEnabled == true)
            StatusPill("NFC", hardware.nfcEnabled == true)
            StatusPill("Audio", hardware.audioActive)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = Fmt.formatBytes(hardware.rxBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = Fmt.formatBytes(hardware.txBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "↓ ${Fmt.formatRate(hardware.rxRateBps)} · ↑ ${Fmt.formatRate(hardware.txRateBps)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tráfico desde el arranque · Sueño profundo ${Fmt.formatDuration(hardware.deepSleepMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HardwareTile(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
