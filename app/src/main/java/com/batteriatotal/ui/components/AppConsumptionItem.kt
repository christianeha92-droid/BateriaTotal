package com.batteriatotal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.batteriatotal.data.AppConsumptionRow
import com.batteriatotal.data.Fmt
import java.util.Locale

@Composable
fun AppConsumptionItem(
    row: AppConsumptionRow,
    maxDrainUah: Long,
    totalDrainUah: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = row.packageName, label = row.label)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (row.foregroundMs > 0) {
                            append("${Fmt.formatDuration(row.foregroundMs)} en primer plano")
                        }
                        if (row.launches > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${row.launches} aperturas")
                        }
                        if (row.lastUsed > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(Fmt.formatRelative(row.lastUsed))
                        }
                        if (isEmpty()) append("Consumo registrado por el monitor")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                if (row.drainUah > 0 && totalDrainUah > 0) {
                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "%.1f %% del total",
                            row.drainUah.toDouble() / totalDrainUah * 100.0
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (maxDrainUah > 0 && row.drainUah > 0) {
            LinearBar(
                fraction = row.drainUah.toFloat() / maxDrainUah.toFloat(),
                modifier = Modifier.padding(start = 53.dp, top = 8.dp, end = 2.dp),
                height = 4.dp
            )
        }
    }
}
