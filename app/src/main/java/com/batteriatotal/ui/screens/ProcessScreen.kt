package com.batteriatotal.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.data.AppProcessState
import com.batteriatotal.data.Fmt
import com.batteriatotal.data.Permissions
import com.batteriatotal.data.ProcessRow
import com.batteriatotal.data.ProcessStopMode
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.components.AppCard
import com.batteriatotal.ui.components.AppIcon
import com.batteriatotal.ui.components.EmptyState
import com.batteriatotal.ui.components.NoteCard
import com.batteriatotal.ui.components.SectionHeader
import com.batteriatotal.ui.components.StatusPill

private enum class ProcessFilter(val label: String) {
    ALL("Todas"),
    FOREGROUND("Primer plano"),
    BACKGROUND("Segundo plano"),
    CONSUMPTION("Con consumo")
}

@Composable
fun ProcessScreen(viewModel: AppViewModel) {
    val processes by viewModel.processes.collectAsStateWithLifecycle()
    val generatedAt by viewModel.processesGeneratedAt.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val notice by viewModel.processNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var filter by rememberSaveable { mutableStateOf(ProcessFilter.ALL) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var pendingManualStop by remember { mutableStateOf<ProcessRow?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshProcesses()
    }
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        viewModel.clearProcessNotice(current.id)
    }

    val visibleRows = remember(processes, filter, showSystem) {
        processes
            .filter { showSystem || !it.isSystem }
            .filter { row ->
                when (filter) {
                    ProcessFilter.ALL -> true
                    ProcessFilter.FOREGROUND -> row.isForeground
                    ProcessFilter.BACKGROUND -> !row.isForeground
                    ProcessFilter.CONSUMPTION -> row.drainUah > 0
                }
            }
    }
    val foregroundCount = visibleRows.count { it.isForeground }
    val backgroundCount = visibleRows.count { !it.isForeground }
    val totalDrainUah = visibleRows.sumOf { it.drainUah }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            if (!usageAccess) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Default.Memory,
                            title = "Sin acceso de uso",
                            message = "Para saber qué app está en primer plano y cuáles siguen activas, Android necesita que concedas el acceso de uso. El resto de la app sigue funcionando sin este permiso."
                        )
                        TextButton(
                            onClick = { Permissions.openUsageAccess(context) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Conceder acceso de uso") }
                    }
                }
                item {
                    NoteCard(
                        text = "El acceso de uso también es necesario para el reparto de consumo por app. " +
                            "Se concede en Ajustes → Privacidad → Permisos especiales → Acceso de uso."
                    )
                }
            } else {
                item {
                    AppCard {
                        SectionHeader(
                            title = "Procesos y apps",
                            subtitle = "Actividad de las últimas 24 h · estado estimado",
                            trailing = {
                                TextButton(onClick = { viewModel.refreshProcesses() }) {
                                    Text("Actualizar")
                                }
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProcessSummary(
                                label = "Primer plano",
                                value = foregroundCount.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            ProcessSummary(
                                label = "Segundo plano",
                                value = backgroundCount.toString(),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                            ProcessSummary(
                                label = "Estimado hoy",
                                value = if (totalDrainUah > 0) {
                                    Fmt.formatMahFromUah(totalDrainUah)
                                } else {
                                    "—"
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (generatedAt > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Actualizado ${Fmt.formatRelative(generatedAt)}. " +
                                    "Mientras ves esta pantalla, Batería Total está en primer plano.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProcessFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Apps del sistema",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Servicios internos, launcher y componentes de HyperOS/MIUI",
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
                }

                if (visibleRows.isEmpty()) {
                    item {
                        AppCard {
                            EmptyState(
                                icon = Icons.Default.Apps,
                                title = if (processes.isEmpty()) {
                                    "Sin actividad reciente"
                                } else {
                                    "Nada en este filtro"
                                },
                                message = if (processes.isEmpty()) {
                                    "Usa algunas aplicaciones y vuelve a actualizar. Android solo permite ver la actividad que ha registrado el sistema."
                                } else {
                                    "Prueba otro filtro o activa las apps del sistema."
                                }
                            )
                        }
                    }
                } else {
                    item {
                        AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                            SectionHeader(
                                title = "Actividad reciente",
                                subtitle = "${visibleRows.size} apps · toca Detener o abre la ficha"
                            )
                            Spacer(Modifier.height(4.dp))
                            visibleRows.forEachIndexed { index, row ->
                                ProcessItem(
                                    row = row,
                                    onStop = {
                                        if (row.canStopDirectly) {
                                            viewModel.stopProcess(row.packageName)
                                        } else {
                                            pendingManualStop = row
                                        }
                                    },
                                    onDetails = { viewModel.openProcessDetails(row.packageName) }
                                )
                                if (index != visibleRows.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    NoteCard(
                        text = "Android no permite a las apps normales ver la lista completa de procesos ni la energía " +
                            "exacta de cada app. Esta lista muestra apps con actividad registrada en las últimas 24 h; " +
                            "el estado se deduce de los eventos de uso y el consumo es una estimación basada en las " +
                            "caídas reales de batería."
                    )
                }
                item {
                    NoteCard(
                        text = "En Android 8 a 13, «Detener» usa la API pública killBackgroundProcesses sobre apps en segundo plano. " +
                            "Desde Android 14 esa API solo puede cerrar los procesos de la propia app, así que «Detener» " +
                            "abre la ficha de la app para que uses «Forzar detención». HyperOS/MIUI puede reiniciarla después."
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        pendingManualStop?.let { row ->
            AlertDialog(
                onDismissRequest = { pendingManualStop = null },
                title = { Text("Detener ${row.label}") },
                text = { Text(manualStopMessage(row)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingManualStop = null
                            viewModel.openProcessDetails(row.packageName)
                        }
                    ) { Text("Abrir ficha") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingManualStop = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

private fun manualStopMessage(row: ProcessRow): String {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        "Desde Android 14, una app normal solo puede detener sus propios procesos. " +
            "Se abrirá la ficha de ${row.label}: pulsa «Forzar detención» para cerrarla. " +
            "HyperOS/MIUI puede reiniciarla después."
    } else {
        "${row.label} está en primer plano. Android no permite cerrarla desde otra app: " +
            "se abrirá su ficha para que uses «Forzar detención»."
    }
}

@Composable
private fun ProcessItem(
    row: ProcessRow,
    onStop: () -> Unit,
    onDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = row.packageName, label = row.label)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (row.isSelf) "${row.label} (esta app)" else row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = row.state.label,
                        active = row.state == AppProcessState.FOREGROUND
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = supportingText(row),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (row.drainUah > 0) Fmt.formatMahFromUah(row.drainUah) else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.drainUah > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "estimado hoy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!row.isSelf) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.stopMode == ProcessStopMode.NONE) {
                    Text(
                        text = if (row.isForeground) "En uso · no se puede cerrar desde aquí" else "Protegida por el sistema",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
                TextButton(onClick = onDetails) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (row.isForeground) "Abrir ficha" else "Ficha")
                }
                if (row.canRequestStop) {
                    TextButton(
                        onClick = onStop,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Detener")
                    }
                }
            }
        }
    }
}

private fun supportingText(row: ProcessRow): String {
    val parts = ArrayList<String>()
    if (row.foregroundMs > 0) {
        parts.add("${Fmt.formatDurationCompact(row.foregroundMs)} en pantalla")
    }
    when (row.state) {
        AppProcessState.FOREGROUND -> parts.add("ahora")
        AppProcessState.VISIBLE -> parts.add("visible")
        AppProcessState.BACKGROUND -> {
            if (row.lastActivityAt > 0) {
                parts.add("último uso ${Fmt.formatRelative(row.lastActivityAt)}")
            }
        }
        AppProcessState.CACHED -> {
            if (row.lastActivityAt > 0) {
                parts.add("última actividad ${Fmt.formatRelative(row.lastActivityAt)}")
            }
        }
        AppProcessState.SERVICE -> parts.add("servicio")
        AppProcessState.UNKNOWN -> parts.add("sin eventos de uso")
    }
    if (row.isSystem) parts.add("sistema")
    return if (parts.isEmpty()) "Sin eventos de uso" else parts.joinToString(" · ")
}

@Composable
private fun ProcessSummary(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 2.dp)
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}
