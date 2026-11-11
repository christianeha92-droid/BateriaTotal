package com.batteriatotal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.HardwareAnalysis
import java.util.Locale

@Composable
fun MeasuredConsumptionCard(
    analysis: HardwareAnalysis,
    capacityMah: Int,
    modifier: Modifier = Modifier
) {
    val screenOn = analysis.screenOnAvgMa
    val screenOff = analysis.screenOffAvgMa
    val networkRows = listOf(
        Triple("WiFi", analysis.wifiAvgMa, analysis.stateBuckets.firstOrNull { it.label == "WiFi" }?.timeMs ?: 0L),
        Triple("Datos móviles", analysis.mobileAvgMa, analysis.stateBuckets.firstOrNull { it.label == "Datos móviles" }?.timeMs ?: 0L),
        Triple("Sin red", analysis.offlineAvgMa, analysis.stateBuckets.firstOrNull { it.label == "Sin red" }?.timeMs ?: 0L)
    )
    val barValues = buildList {
        screenOn?.let { add("Pantalla encendida" to it) }
        screenOff?.let { add("En reposo" to it) }
        networkRows.forEach { (label, ma, _) -> ma?.let { add(label to it) } }
    }
    val barMax = barValues.maxOfOrNull { it.second } ?: 0f

    AppCard(modifier = modifier) {
        SectionHeader(
            title = "Consumo medido",
            subtitle = "Calculado con caídas reales de batería y estado del sistema"
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasuredBigStat(
                label = "Descarga media",
                value = Fmt.formatCurrentMa(analysis.overallAvgMa),
                supporting = Fmt.formatPerHour(analysis.overallAvgMa, capacityMah),
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f)
            )
            MeasuredBigStat(
                label = "Batería consumida",
                value = String.format(Locale.getDefault(), "%.1f mAh", analysis.dischargeMah),
                supporting = Fmt.formatDuration(analysis.dischargeMs),
                icon = Icons.Default.PhoneAndroid,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(4.dp))
        StateRow(
            label = "Pantalla encendida",
            ma = screenOn,
            timeMs = analysis.screenOnMs,
            capacityMah = capacityMah,
            icon = Icons.Default.PhoneAndroid
        )
        StateRow(
            label = "Pantalla apagada / reposo",
            ma = screenOff,
            timeMs = analysis.screenOffMs,
            capacityMah = capacityMah,
            icon = Icons.Default.BatteryChargingFull
        )
        networkRows.forEach { (label, ma, timeMs) ->
            StateRow(
                label = label,
                ma = ma,
                timeMs = timeMs,
                capacityMah = capacityMah,
                icon = Icons.Default.Wifi
            )
        }
        if (barValues.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Comparativa de corriente media",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            barValues.forEach { (label, ma) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Fmt.formatCurrentMa(ma),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                    ) {
                        LinearBar(
                            fraction = if (barMax > 0f) ma / barMax else 0f,
                            color = if (label.contains("encendida")) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            height = 5.dp
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Aún no hay suficientes muestras. Deja el monitor activo para comparar estados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (analysis.chargeMs > 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Carga registrada: ${Fmt.formatDuration(analysis.chargeMs)} · " +
                    "energía añadida ≈ ${Fmt.formatMahFromUah(analysis.chargeGainUah)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MeasuredBigStat(
    label: String,
    value: String,
    supporting: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StateRow(
    label: String,
    ma: Float?,
    timeMs: Long,
    capacityMah: Int,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (timeMs > 0) "${Fmt.formatDuration(timeMs)} registrados" else "Sin tiempo registrado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Fmt.formatCurrentMa(ma),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = Fmt.formatPerHour(ma, capacityMah),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
