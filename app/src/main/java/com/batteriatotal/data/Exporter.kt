package com.batteriatotal.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    fun buildCsvIntent(context: Context, samples: List<Sample>): Intent? {
        if (samples.isEmpty()) return null
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "bateria-total-historial.csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        file.bufferedWriter().use { writer ->
            writer.appendLine("fecha,nivel_pct,corriente_uA,voltaje_mV,temperatura_C,estado,conectado,carga_uAh,energia_nWh,pantalla,wifi,datos_moviles,brillo")
            samples.forEach { sample ->
                writer.append(dateFormat.format(Date(sample.ts)))
                writer.append(',')
                writer.append(sample.level.toString())
                writer.append(',')
                writer.append(sample.currentUa?.toString() ?: "")
                writer.append(',')
                writer.append(sample.voltageMv?.toString() ?: "")
                writer.append(',')
                writer.append(sample.temperatureC?.toString() ?: "")
                writer.append(',')
                writer.append(sample.status.toString())
                writer.append(',')
                writer.append(sample.plugged.toString())
                writer.append(',')
                writer.append(sample.chargeCounterUah?.toString() ?: "")
                writer.append(',')
                writer.append(sample.energyCounterNwh?.toString() ?: "")
                writer.append(',')
                writer.append(if (sample.screenOn) "1" else "0")
                writer.append(',')
                writer.append(if (sample.wifiOn) "1" else "0")
                writer.append(',')
                writer.append(if (sample.mobileOn) "1" else "0")
                writer.append(',')
                writer.append(sample.brightness?.toString() ?: "")
                writer.appendLine()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Historial de Batería Total")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(intent, "Compartir historial CSV").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
