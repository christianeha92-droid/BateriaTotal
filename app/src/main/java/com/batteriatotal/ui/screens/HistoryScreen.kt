package com.batteriatotal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.data.Analysis
import com.batteriatotal.data.ChargingSession
import com.batteriatotal.data.Exporter
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.GameSession
import com.batteriatotal.data.HistoryRange
import com.batteriatotal.data.Sample
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.components.AppCard
import com.batteriatotal.ui.components.ChipSelector
import com.batteriatotal.ui.components.EmptyState
import com.batteriatotal.ui.components.InfoRow
import com.batteriatotal.ui.components.LineChart
import com.batteriatotal.ui.components.ChartPoint
import com.batteriatotal.ui.components.SectionHeader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HistoryMetric(val label: String, val unit: String) {
    LEVEL("Nivel", "%"),
    CURRENT("Corriente", "mA"),
    TEMPERATURE("Temperatura", "°C"),
    VOLTAGE("Voltaje", "V")
}

@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    val samples by viewModel.historySamples.collectAsStateWithLifecycle()
    val range by viewModel.historyRange.collectAsStateWithLifecycle()
    val sessions by viewModel.chargingSessions.collectAsStateWithLifecycle()
    val gameSessions by viewModel.gameSessions.collectAsStateWithLifecycle()
    val capacity by viewModel.designCapacityMah.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var metric by rememberSaveable { mutableStateOf(HistoryMetric.LEVEL.name) }
    val selectedMetric = HistoryMetric.valueOf(metric)
    val now = System.currentTimeMillis()
    val analysis = remember(samples, capacity, range) {
        Analysis.analyze(samples, capacity, now - range.durationMs, now)
    }
    val series = remember(samples, selectedMetric) {
        samples.mapNotNull { sample ->
            sample.metricValue(selectedMetric)?.let { ChartPoint(sample.ts, it) }
        }
    }
    val chartPoints = remember(series) { downsample(series, 320) }
    val values = remember(series) { series.map { it.value } }
    val accent = when (selectedMetric) {
        HistoryMetric.LEVEL -> MaterialTheme.colorScheme.primary
        HistoryMetric.CURRENT -> MaterialTheme.colorScheme.tertiary
        HistoryMetric.TEMPERATURE -> MaterialTheme.colorScheme.error
        HistoryMetric.VOLTAGE -> MaterialTheme.colorScheme.secondary
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            ChipSelector(
                options = HistoryRange.entries,
                selected = range,
                label = { it.label },
                onSelect = { viewModel.setHistoryRange(it) }
            )
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Historial",
                    subtitle = "Serie temporal registrada en el dispositivo"
                )
                Spacer(Modifier.height(12.dp))
                ChipSelector(
                    options = HistoryMetric.entries,
                    selected = selectedMetric,
                    label = { it.label },
                    onSelect = { metric = it.name }
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = values.lastOrNull()?.let {
                            formatMetricValue(selectedMetric, it, withUnit = false)
                        } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = " ${selectedMetric.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (values.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Mín ${formatMetricValue(selectedMetric, values.min())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Máx ${formatMetricValue(selectedMetric, values.max())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Media ${formatMetricValue(selectedMetric, values.average().toFloat())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (chartPoints.size < 2) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = "Sin historial suficiente",
                        message = "Deja el monitor activo durante unos minutos. Con la pantalla encendida se guarda una muestra cada 30 s."
                    )
                } else {
                    val includeDate = range == HistoryRange.WEEK
                    LineChart(
                        points = chartPoints,
                        color = accent,
                        valueFormatter = { formatMetricValue(selectedMetric, it, withUnit = false) },
                        startLabel = Fmt.formatAxisTime(chartPoints.first().timestamp, includeDate),
                        endLabel = Fmt.formatAxisTime(chartPoints.last().timestamp, includeDate)
                    )
                    Text(
                        text = if (selectedMetric == HistoryMetric.CURRENT) {
                            "La corriente incluye carga y descarga; su valor absoluto puede subir al enchufar."
                        } else {
                            "${chartPoints.size} puntos en pantalla de ${samples.size} muestras"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Resumen del periodo",
                    subtitle = range.label
                )
                Spacer(Modifier.height(6.dp))
                InfoRow(
                    icon = Icons.Default.Thermostat,
                    label = "Consumo medido",
                    value = Fmt.formatMahFromUah(analysis.dischargeUah),
                    supporting = "Temperatura media ${Fmt.formatTemperature(analysis.avgTempC)} · máx ${Fmt.formatTemperature(analysis.maxTempC)}"
                )
                InfoRow(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Carga registrada",
                    value = Fmt.formatMahFromUah(analysis.chargeGainUah),
                    supporting = "${Fmt.formatDuration(analysis.chargeMs)} conectado"
                )
                InfoRow(
                    icon = Icons.Default.History,
                    label = "Actividad",
                    value = Fmt.formatDuration(analysis.monitoredMs),
                    supporting = "Pantalla ${Fmt.formatDuration(analysis.screenOnMs)} · ${samples.size} muestras"
                )
                if (samples.isNotEmpty()) {
                    Text(
                        text = "Última muestra ${Fmt.formatDateTime(samples.last().ts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Sesiones de carga",
                    subtitle = "Detectadas al conectar y desconectar corriente"
                )
                Spacer(Modifier.height(8.dp))
                if (sessions.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Sin sesiones",
                        message = "Cuando conectes el cargador aparecerá aquí la duración, el nivel ganado y la energía añadida."
                    )
                } else {
                    sessions.take(20).forEachIndexed { index, session ->
                        ChargingSessionRow(session)
                        if (index != sessions.take(20).lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                    if (sessions.size > 20) {
                        Text(
                            text = "Mostrando las 20 sesiones más recientes de ${sessions.size}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Sesiones de juego",
                    subtitle = "Batería real mientras una app de juego está delante"
                )
                Spacer(Modifier.height(8.dp))
                if (gameSessions.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.SportsEsports,
                        title = "Sin partidas registradas",
                        message = "Abre un juego con el modo juego activo. Se guardará la duración, " +
                            "la batería inicial y final, la temperatura y si estaba conectado el cargador."
                    )
                } else {
                    gameSessions.take(20).forEachIndexed { index, session ->
                        GameSessionRow(session, capacity)
                        if (index != gameSessions.take(20).lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                    if (gameSessions.size > 20) {
                        Text(
                            text = "Mostrando las 20 partidas más recientes de ${gameSessions.size}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Text(
                        text = "Se detectan los juegos que declaran la categoría “juego” en Android. " +
                            "El consumo por juego no es exacto: es la batería real del dispositivo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Exportar datos",
                    subtitle = "CSV con todas las muestras del periodo"
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${samples.size} muestras · nivel, corriente, voltaje, temperatura, carga, pantalla y red.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val intent = runCatching {
                                withContext(Dispatchers.IO) {
                                    Exporter.buildCsvIntent(context, samples)
                                }
                            }.getOrNull()
                            if (intent != null) {
                                runCatching { context.startActivity(intent) }
                            }
                        }
                    },
                    enabled = samples.isNotEmpty()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Compartir CSV")
                }
            }
        }
    }
}

@Composable
private fun ChargingSessionRow(session: ChargingSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${session.startLevel}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "→ ${session.endLevel}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Fmt.formatDateTime(session.startTs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${Fmt.formatDuration(session.durationMs)} · " +
                    "media ${Fmt.formatCurrentMa(session.avgCurrentMa)} · " +
                    "máx ${Fmt.formatTemperature(session.maxTempC)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = session.addedUah?.let { Fmt.formatMahFromUah(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GameSessionRow(session: GameSession, capacity: Int) {
    val deltaUah = when {
        session.startChargeUah != null && session.endChargeUah != null ->
            session.endChargeUah - session.startChargeUah
        else -> (session.endLevel - session.startLevel).toLong() * capacity.toLong() * 1000L
    }
    val deltaText = when {
        deltaUah > 0L -> "+${Fmt.formatMahFromUah(deltaUah)}"
        deltaUah < 0L -> "-${Fmt.formatMahFromUah(-deltaUah)}"
        else -> "—"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = "${session.startLevel} %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${Fmt.formatDateTime(session.startTs)} · ${Fmt.formatDuration(session.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${session.startLevel} % → ${session.endLevel} % · " +
                    "máx ${Fmt.formatTemperature(session.maxTempC)} · " +
                    "media ${Fmt.formatCurrentMa(session.avgCurrentMa)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.insufficientCharger) {
                Text(
                    text = "⚠ La batería bajó con el cargador conectado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (session.hadCharger) "Con cargador" else "Sin cargador",
                style = MaterialTheme.typography.labelSmall,
                color = if (session.hadCharger) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = deltaText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (deltaUah >= 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            )
        }
    }
}

private fun Sample.metricValue(metric: HistoryMetric): Float? = when (metric) {
    HistoryMetric.LEVEL -> level.toFloat()
    HistoryMetric.CURRENT -> currentUa?.let { abs(it) / 1000f }?.takeIf { it > 0f }
    HistoryMetric.TEMPERATURE -> temperatureC
    HistoryMetric.VOLTAGE -> voltageMv?.takeIf { it > 0 }?.let { it / 1000f }
}

private fun formatMetricValue(metric: HistoryMetric, value: Float, withUnit: Boolean = true): String {
    val text = when (metric) {
        HistoryMetric.LEVEL -> "${value.roundToInt()}"
        HistoryMetric.CURRENT -> if (value >= 1000f) {
            String.format(Locale.getDefault(), "%.2f", value / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f", value)
        }
        HistoryMetric.TEMPERATURE -> String.format(Locale.getDefault(), "%.1f", value)
        HistoryMetric.VOLTAGE -> String.format(Locale.getDefault(), "%.2f", value)
    }
    return if (withUnit) "$text ${if (metric == HistoryMetric.CURRENT && value >= 1000f) "A" else metric.unit}" else text
}

private fun downsample(points: List<ChartPoint>, maxPoints: Int): List<ChartPoint> {
    if (points.size <= maxPoints) return points
    val stride = ceil(points.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val sampled = points.filterIndexed { index, _ -> index % stride == 0 }
    return if (sampled.lastOrNull() != points.last()) sampled + points.last() else sampled
}
