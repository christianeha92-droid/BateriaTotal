package com.batteriatotal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.data.ConsumptionPeriod
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.Permissions
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.components.AppCard
import com.batteriatotal.ui.components.AppConsumptionItem
import com.batteriatotal.ui.components.ChipSelector
import com.batteriatotal.ui.components.EmptyState
import com.batteriatotal.ui.components.HardwareNowCard
import com.batteriatotal.ui.components.InfoRow
import com.batteriatotal.ui.components.LinearBar
import com.batteriatotal.ui.components.MeasuredConsumptionCard
import com.batteriatotal.ui.components.NoteCard
import com.batteriatotal.ui.components.SectionHeader
import java.util.Locale

@Composable
fun ConsumptionScreen(viewModel: AppViewModel) {
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val period by viewModel.consumptionPeriod.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val capacity by viewModel.designCapacityMah.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSystem by rememberSaveable { mutableStateOf(false) }

    val analysis = consumption.analysis
    val standby = (analysis.dischargeUah - consumption.attributedUah).coerceAtLeast(0L)
    val attributedFraction = if (analysis.dischargeUah > 0) {
        consumption.attributedUah.toFloat() / analysis.dischargeUah.toFloat()
    } else {
        0f
    }
    val filteredApps = remember(consumption.apps, showSystem) {
        if (showSystem) consumption.apps else consumption.apps.filter { !it.isSystem }
    }
    val maxDrain = filteredApps.maxOfOrNull { it.drainUah } ?: 0L

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            ChipSelector(
                options = ConsumptionPeriod.entries,
                selected = period,
                label = { it.label },
                onSelect = { viewModel.setConsumptionPeriod(it) }
            )
        }

        item {
            AppCard {
                SectionHeader(
                    title = "Consumo del periodo",
                    subtitle = "${period.label} · medición real de la batería"
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (analysis.dischargeUah > 0) {
                            String.format(Locale.getDefault(), "%.0f", analysis.dischargeMah)
                        } else "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " mAh",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = Fmt.formatDuration(analysis.monitoredMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (analysis.dischargeUah > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(attributedFraction.coerceAtLeast(0.001f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((1f - attributedFraction).coerceAtLeast(0.001f))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f))
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMini(
                        label = "Apps",
                        value = Fmt.formatMahFromUah(consumption.attributedUah),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMini(
                        label = "Sistema y reposo",
                        value = Fmt.formatMahFromUah(standby),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMini(
                        label = "Atribuido",
                        value = String.format(Locale.getDefault(), "%.0f %%", attributedFraction * 100f),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (analysis.samples < 2) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Aún recopilando. El servicio guarda una muestra cada 30 s con la pantalla encendida y cada 5 min en reposo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("Aplicaciones") }
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("Hardware") }
                )
            }
        }

        if (tab == 0) {
            item {
                AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apps del sistema",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Incluye servicios y launcher en la lista",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showSystem,
                            onCheckedChange = { showSystem = it }
                        )
                    }
                }
            }

            if (!usageAccess) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Default.Apps,
                            title = "Sin acceso de uso",
                            message = "Android necesita que concedas el acceso de uso para saber qué aplicación está en primer plano y atribuirle consumo."
                        )
                        TextButton(
                            onClick = { Permissions.openUsageAccess(context) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Conceder acceso de uso") }
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Default.Apps,
                            title = "Todavía no hay datos",
                            message = "Deja el monitor activo y usa las aplicaciones con normalidad. Las caídas reales de batería se repartirán entre las apps que estén en pantalla."
                        )
                    }
                }
            } else {
                item {
                    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        SectionHeader(
                            title = "Aplicaciones",
                            subtitle = "${filteredApps.size} apps · consumo atribuido"
                        )
                        Spacer(Modifier.height(4.dp))
                        filteredApps.forEachIndexed { index, row ->
                            AppConsumptionItem(
                                row = row,
                                maxDrainUah = maxDrain,
                                totalDrainUah = analysis.dischargeUah
                            )
                            if (index != filteredApps.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }

                item {
                    AppCard {
                        InfoRow(
                            icon = Icons.Default.PhoneAndroid,
                            label = "Sistema y reposo",
                            value = Fmt.formatMahFromUah(standby),
                            supporting = "Consumo sin app en primer plano: pantalla apagada, radios, servicios del sistema y margen de medición"
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearBar(
                            fraction = if (analysis.dischargeUah > 0) {
                                standby.toFloat() / analysis.dischargeUah.toFloat()
                            } else 0f,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                item {
                    NoteCard(
                        text = "El reparto por app es una estimación: Android solo revela el nivel, la corriente y los eventos de uso. " +
                            "La energía se atribuye proporcionalmente al tiempo en primer plano de cada app cuando se detecta una caída real de batería."
                    )
                }
            }
        } else {
            item {
                MeasuredConsumptionCard(
                    analysis = analysis,
                    capacityMah = capacity
                )
            }
            item {
                HardwareNowCard(hardware = live.hardware)
            }
            item {
                AppCard {
                    SectionHeader(
                        title = "Resumen del periodo",
                        subtitle = "Lo que el sistema sí expone"
                    )
                    Spacer(Modifier.height(6.dp))
                    InfoRow(
                        icon = Icons.Default.PowerSettingsNew,
                        label = "Energía consumida",
                        value = Fmt.formatMahFromUah(analysis.dischargeUah),
                        supporting = "${analysis.samples} muestras · ${Fmt.formatDuration(analysis.dischargeMs)} en descarga"
                    )
                    InfoRow(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Energía cargada",
                        value = Fmt.formatMahFromUah(analysis.chargeGainUah),
                        supporting = "${Fmt.formatDuration(analysis.chargeMs)} conectado a corriente"
                    )
                    InfoRow(
                        icon = Icons.Default.PhoneAndroid,
                        label = "Pantalla",
                        value = "${Fmt.formatDuration(analysis.screenOnMs)} encendida",
                        supporting = "Apagada ${Fmt.formatDuration(analysis.screenOffMs)}"
                    )
                    InfoRow(
                        icon = Icons.Default.Thermostat,
                        label = "Temperatura",
                        value = Fmt.formatTemperature(analysis.avgTempC),
                        supporting = "Máxima ${Fmt.formatTemperature(analysis.maxTempC)}"
                    )
                }
            }
            item {
                NoteCard(
                    text = "El desglose en milivatios por CPU, pantalla, WiFi o radio es información privilegiada del sistema " +
                        "y no está disponible para apps normales. Aquí se muestra el consumo total medido y la corriente " +
                        "media en cada estado del hardware, que sí son datos reales."
                )
            }
        }
    }
}

@Composable
private fun SummaryMini(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}
