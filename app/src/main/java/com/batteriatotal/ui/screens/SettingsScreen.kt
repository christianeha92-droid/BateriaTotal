package com.batteriatotal.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteriatotal.BuildConfig
import com.batteriatotal.data.DeviceProfiles
import com.batteriatotal.data.Permissions
import com.batteriatotal.data.ThemeMode
import com.batteriatotal.ui.AppViewModel
import com.batteriatotal.ui.components.AppCard
import com.batteriatotal.ui.components.ChipSelector
import com.batteriatotal.ui.components.InfoRow
import com.batteriatotal.ui.components.NoteCard
import com.batteriatotal.ui.components.SectionHeader

private val samplingOptions = listOf(15, 30, 60, 120, 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val gameModeEnabled by viewModel.gameModeEnabled.collectAsStateWithLifecycle()
    val samplingSeconds by viewModel.samplingSeconds.collectAsStateWithLifecycle()
    val capacity by viewModel.designCapacityMah.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val notificationPermission by viewModel.notificationPermission.collectAsStateWithLifecycle()
    val ignoringOptimizations by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var capacityDialog by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var detectedCapacity by remember { mutableStateOf<Int?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                AppCard {
                    SectionHeader(
                        title = "Monitorización",
                        subtitle = "Registro en segundo plano del estado de la batería",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.MonitorHeart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Servicio activo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (monitoringEnabled) {
                                    "Muestreando y guardando en el dispositivo"
                                } else {
                                    "En pausa: no se registra ningún dato"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = monitoringEnabled,
                            onCheckedChange = { viewModel.setMonitoringEnabled(it) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Frecuencia con la pantalla encendida",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipSelector(
                        options = samplingOptions,
                        selected = samplingSeconds,
                        label = { seconds ->
                            when {
                                seconds < 60 -> "$seconds s"
                                else -> "${seconds / 60} min"
                            }
                        },
                        onSelect = { viewModel.setSamplingSeconds(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Con la pantalla apagada se toma una muestra cada 5 min para reducir el consumo " +
                            "de la propia app. Doze puede espaciar aún más las muestras.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AppCard {
                    SectionHeader(
                        title = "Modo juego",
                        subtitle = "Avisos y registro mientras juegas",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detectar juegos",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (gameModeEnabled) {
                                    "Sesiones, temperatura y avisos activos"
                                } else {
                                    "Sin detección ni registro de partidas"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = gameModeEnabled,
                            onCheckedChange = { viewModel.setGameModeEnabled(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Cuando un juego está en primer plano se registran batería, corriente y " +
                            "temperatura. Si conectas el cargador, la notificación muestra si la batería " +
                            "carga o baja, y avisa si el cargador no cubre el consumo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { Permissions.openChargeSettings(context) }) {
                            Text("Opciones de carga")
                        }
                        TextButton(onClick = { Permissions.openGameTurbo(context) }) {
                            Text("Game Turbo")
                        }
                    }
                    if (!usageAccess) {
                        Text(
                            text = "El modo juego necesita el acceso de uso para saber qué app está delante.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (!monitoringEnabled) {
                        Text(
                            text = "El monitor está en pausa; activa “Servicio activo” para registrar partidas.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NoteCard(
                        text = "HyperOS/MIUI decide la fuente de energía. Esta app no puede forzar que el " +
                            "teléfono use el cargador en lugar de la batería: solo mide, avisa y registra. " +
                            "El bypass o “carga inteligente” depende del modelo y del firmware; en muchos " +
                            "Xiaomi/Redmi no está disponible. Usa el cargador original y, si tu modelo lo " +
                            "permite, el límite de carga del 80 % para cuidar la batería."
                    )
                }
            }

            item {
                AppCard {
                    SectionHeader(
                        title = "Batería",
                        subtitle = "Capacidad usada para convertir % en mAh",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoRow(
                        label = "Capacidad de diseño",
                        value = "$capacity mAh",
                        supporting = "Ajusta la capacidad nominal de tu modelo. Solo afecta a las estimaciones en mAh.",
                        trailing = {
                            TextButton(onClick = { capacityDialog = true }) { Text("Cambiar") }
                        }
                    )
                    InfoRow(
                        label = "Capacidad detectada en el sistema",
                        value = detectedCapacity?.let { "$it mAh" } ?: "—",
                        supporting = "Lectura best-effort de /sys; HyperOS/MIUI suele bloquearla.",
                        trailing = {
                            if (detectedCapacity != null) {
                                TextButton(onClick = {
                                    detectedCapacity?.let { viewModel.setDesignCapacity(it) }
                                }) { Text("Usar") }
                            } else {
                                TextButton(onClick = {
                                    detectedCapacity = viewModel.detectedCapacityMah()
                                }) { Text("Detectar") }
                            }
                        }
                    )
                }
            }

            item {
                AppCard {
                    SectionHeader(
                        title = "Apariencia",
                        subtitle = "Tema de la aplicación",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipSelector(
                        options = ThemeMode.entries,
                        selected = themeMode,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> "Sistema"
                                ThemeMode.LIGHT -> "Claro"
                                ThemeMode.DARK -> "Oscuro"
                            }
                        },
                        onSelect = { viewModel.setThemeMode(it) }
                    )
                }
            }

            item {
                AppCard {
                    SectionHeader(
                        title = "Permisos del sistema",
                        subtitle = "Necesarios para un registro fiable",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    PermissionActionRow(
                        icon = Icons.Default.Storage,
                        title = "Acceso de uso",
                        description = if (usageAccess) {
                            "Concedido: se identifica la app en primer plano"
                        } else {
                            "Sin él no se puede repartir el consumo entre apps"
                        },
                        good = usageAccess,
                        actionLabel = if (usageAccess) "Revisar" else "Conceder",
                        onAction = { Permissions.openUsageAccess(context) }
                    )
                    PermissionActionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notificaciones",
                        description = if (notificationPermission) {
                            "El monitor puede mostrar su notificación"
                        } else {
                            "Android puede ocultar la notificación del servicio"
                        },
                        good = notificationPermission,
                        actionLabel = if (notificationPermission) null else "Permitir",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Permissions.openNotificationSettings(context)
                            }
                        }
                    )
                    PermissionActionRow(
                        icon = Icons.Default.Autorenew,
                        title = "Sin restricciones de batería",
                        description = if (ignoringOptimizations) {
                            "El sistema no debería pausar el monitor"
                        } else {
                            "Excluye la app de la optimización de batería"
                        },
                        good = ignoringOptimizations,
                        actionLabel = if (ignoringOptimizations) "Revisar" else "Ajustar",
                        onAction = { Permissions.requestIgnoreBatteryOptimizations(context) }
                    )
                    PermissionActionRow(
                        icon = Icons.Default.Autorenew,
                        title = "Autoinicio del sistema",
                        description = "Ajustes → Apps → Batería Total → Autoinicio",
                        good = null,
                        actionLabel = "Abrir",
                        onAction = { Permissions.openAutoStart(context) }
                    )
                    PermissionActionRow(
                        icon = Icons.Default.Info,
                        title = "Ficha de la app",
                        description = "Permisos, notificaciones y uso de batería",
                        good = null,
                        actionLabel = "Abrir",
                        onAction = { Permissions.openAppDetails(context) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "En HyperOS/MIUI también conviene fijar la app en recientes y desactivar " +
                            "“Ahorro de batería” en la ficha de la aplicación.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AppCard {
                    SectionHeader(
                        title = "Datos e historial",
                        subtitle = "Todo se guarda solo en este dispositivo"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Borrar historial",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Elimina las muestras y el consumo por app (máx. 30 días).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { clearDialog = true }) {
                            Text("Borrar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                AppCard {
                    SectionHeader(
                        title = "Acerca de",
                        subtitle = "Batería Total ${BuildConfig.VERSION_NAME}"
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoRow(
                        icon = Icons.Default.Info,
                        label = "Sin permisos privilegiados",
                        value = "APIs públicas",
                        supporting = "No usa root ni Shizuku. Tampoco tiene permiso de Internet."
                    )
                    InfoRow(
                        icon = Icons.Default.MonitorHeart,
                        label = "Privacidad",
                        value = "100 % local",
                        supporting = "Los datos nunca salen del teléfono salvo que tú los compartas."
                    )
                    Spacer(Modifier.height(8.dp))
                    NoteCard(
                        text = "El consumo por app es una estimación basada en caídas reales de batería y " +
                            "tiempo en primer plano. Android no permite a las apps normales leer la energía " +
                            "exacta consumida por cada proceso ni por cada componente de hardware."
                    )
                }
            }

            item {
                Text(
                    text = "Compatible con Xiaomi, Redmi y POCO con HyperOS/MIUI · " +
                        "${DeviceProfiles.displayName()} · " +
                        "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (capacityDialog) {
        CapacityDialog(
            current = capacity,
            onDismiss = { capacityDialog = false },
            onConfirm = { value ->
                viewModel.setDesignCapacity(value)
                capacityDialog = false
            }
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("¿Borrar todo el historial?") },
            text = {
                Text(
                    "Se eliminarán las muestras y el consumo atribuido a apps. " +
                        "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    clearDialog = false
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CapacityDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in 1000..20000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Capacidad de diseño") },
        text = {
            Column {
                Text(
                    text = "Indica la capacidad nominal de la batería en mAh. Consulta la ficha de tu " +
                        "modelo: por ejemplo, Xiaomi 14 son 4610 mAh y muchos Redmi usan 5000 mAh. " +
                        "Solo se usa para estimar mAh y porcentajes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter(Char::isDigit).take(5)
                    },
                    label = { Text("mAh") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = valid
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    good: Boolean?,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val tint = when (good) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.tertiary
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
