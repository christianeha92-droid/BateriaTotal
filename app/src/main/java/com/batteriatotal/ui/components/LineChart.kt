package com.batteriatotal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ChartPoint(val timestamp: Long, val value: Float)

@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    valueFormatter: (Float) -> String,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Recopilando datos…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    val rawRange = (maxValue - minValue).takeIf { it > 0.0001f } ?: 1f
    val pad = rawRange * 0.12f
    val lower = minValue - pad
    val upper = maxValue + pad
    val average = points.map { it.value }.average().toFloat()
    val startTs = points.first().timestamp
    val endTs = points.last().timestamp
    val span = (endTs - startTs).coerceAtLeast(1L)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .height(178.dp)
                    .width(46.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = valueFormatter(maxValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = valueFormatter(minValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(178.dp)
            ) {
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val left = 2.dp.toPx()
                val right = size.width - 2.dp.toPx()
                val chartWidth = right - left
                val chartHeight = bottom - top

                repeat(4) { index ->
                    val y = top + chartHeight * index / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 7.dp.toPx()))
                    )
                }

                val avgY = bottom - ((average - lower) / (upper - lower)) * chartHeight
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = Offset(left, avgY),
                    end = Offset(right, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx()))
                )

                fun xFor(ts: Long): Float = left + ((ts - startTs).toFloat() / span.toFloat()) * chartWidth
                fun yFor(value: Float): Float = bottom - ((value - lower) / (upper - lower)) * chartHeight

                val linePath = Path()
                val fillPath = Path()
                points.forEachIndexed { index, point ->
                    val x = xFor(point.timestamp)
                    val y = yFor(point.value)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, bottom)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(xFor(points.last().timestamp), bottom)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.01f))
                    )
                )
                drawPath(
                    path = linePath,
                    color = color,
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                val last = points.last()
                val lastX = xFor(last.timestamp)
                val lastY = yFor(last.value)
                drawCircle(color = color.copy(alpha = 0.2f), radius = 9.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(
                    color = surfaceColor,
                    radius = 1.7.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 54.dp, top = 6.dp)
        ) {
            Text(
                text = startLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = endLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
