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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SetupMonitorCard(
    monitoringEnabled: Boolean,
    usageAccess: Boolean,
    notificationPermission: Boolean,
    ignoringOptimizations: Boolean,
    onEnableMonitoring: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutoStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Completa la monitorización",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Sin estos pasos el historial y el análisis por app pueden cortarse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SetupRow(
            title = "Servicio de monitorización",
            description = if (monitoringEnabled) {
                "Activo en segundo plano"
            } else {
                "En pausa: no se registrará el consumo"
            },
            good = monitoringEnabled,
            actionLabel = if (monitoringEnabled) null else "Activar",
            onAction = onEnableMonitoring
        )
        SetupRow(
            title = "Acceso de uso",
            description = if (usageAccess) {
                "Concedido: se puede atribuir consumo a cada app"
            } else {
                "Necesario para identificar qué app está en primer plano"
            },
            good = usageAccess,
            actionLabel = if (usageAccess) "Revisar" else "Conceder",
            onAction = onOpenUsageAccess
        )
        SetupRow(
            title = "Notificaciones",
            description = if (notificationPermission) {
                "La notificación del monitor está visible"
            } else {
                "Android puede ocultar la notificación del servicio"
            },
            good = notificationPermission,
            actionLabel = if (notificationPermission) null else "Permitir",
            onAction = onRequestNotifications
        )
        SetupRow(
            title = "Sin restricciones de batería",
            description = if (ignoringOptimizations) {
                "El sistema no debería pausar el registro"
            } else {
                "Desactiva la optimización para esta app"
            },
            good = ignoringOptimizations,
            actionLabel = if (ignoringOptimizations) null else "Ajustar",
            onAction = onRequestBatteryExemption
        )
        SetupRow(
            title = "Autoinicio del sistema",
            description = "Actívalo para reanudar el monitor tras reiniciar el móvil",
            good = null,
            actionLabel = "Abrir",
            onAction = onOpenAutoStart
        )
        Text(
            text = "En HyperOS/MIUI: Ajustes → Apps → Batería Total → Autoinicio, y Ahorro de batería → Sin restricciones.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SetupRow(
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
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (good) {
                    true -> Icons.Default.CheckCircle
                    false -> Icons.Default.Warning
                    null -> Icons.Default.Info
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
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
            TextButton(
                onClick = onAction,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
