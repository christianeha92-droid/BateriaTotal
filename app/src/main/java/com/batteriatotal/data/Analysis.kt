package com.batteriatotal.data

import android.os.BatteryManager
import kotlin.math.abs

object Analysis {

    private const val MAX_INTERVAL_MS = 6 * 60 * 60 * 1000L
    const val MAX_ATTRIBUTION_GAP_MS = 30 * 60 * 1000L

    /**
     * Devuelve la energía descargada entre dos muestras en µAh, o null si el
     * intervalo no es una descarga fiable.
     */
    fun drainUah(prev: Sample, cur: Sample, capacityMah: Int): Long? {
        if (cur.plugged != 0) return null
        if (cur.status == BatteryManager.BATTERY_STATUS_CHARGING ||
            cur.status == BatteryManager.BATTERY_STATUS_FULL
        ) return null
        if (prev.plugged != 0) return null
        if (cur.level > prev.level) return null

        val counterDrain = if (prev.chargeCounterUah != null && cur.chargeCounterUah != null) {
            val difference = prev.chargeCounterUah - cur.chargeCounterUah
            val safeLimit = capacityMah.toLong() * 1000L * 3L
            if (difference > 0L && difference <= safeLimit) difference else null
        } else {
            null
        }
        if (counterDrain != null) return counterDrain

        return if (cur.level < prev.level) {
            (prev.level - cur.level).toLong() * capacityMah.toLong() * 1000L
        } else {
            0L
        }
    }

    fun analyze(
        samples: List<Sample>,
        capacityMah: Int,
        periodStart: Long,
        periodEnd: Long
    ): HardwareAnalysis {
        if (samples.size < 2) {
            return HardwareAnalysis(
                periodStart = periodStart,
                periodEnd = periodEnd,
                samples = samples.size
            )
        }

        var monitoredMs = 0L
        var dischargeMs = 0L
        var dischargeUah = 0L
        var chargeMs = 0L
        var chargeGainUah = 0L
        var screenOnMs = 0L
        var screenOffMs = 0L
        var dischargeScreenOnMs = 0L
        var dischargeScreenOnUah = 0L
        var dischargeScreenOffMs = 0L
        var dischargeScreenOffUah = 0L

        var tempWeightedSum = 0.0
        var tempWeight = 0L
        var maxTemp: Float? = null

        var wifiMs = 0L
        var wifiUah = 0L
        var mobileMs = 0L
        var mobileUah = 0L
        var offlineMs = 0L
        var offlineUah = 0L

        for (index in 1 until samples.size) {
            val prev = samples[index - 1]
            val cur = samples[index]
            val duration = cur.ts - prev.ts
            if (duration <= 0L || duration > MAX_INTERVAL_MS) continue
            monitoredMs += duration

            if (cur.screenOn) screenOnMs += duration else screenOffMs += duration

            cur.temperatureC?.let { temp ->
                tempWeightedSum += temp.toDouble() * duration
                tempWeight += duration
                if (maxTemp == null || temp > maxTemp!!) maxTemp = temp
            }

            if (prev.isChargingConnection && cur.isChargingConnection) {
                chargeMs += duration
                val gained = energyGainedUah(prev, cur, capacityMah)
                if (gained != null && gained > 0) chargeGainUah += gained
                continue
            }

            val drain = drainUah(prev, cur, capacityMah) ?: continue
            dischargeMs += duration
            dischargeUah += drain

            if (cur.screenOn) {
                dischargeScreenOnMs += duration
                dischargeScreenOnUah += drain
            } else {
                dischargeScreenOffMs += duration
                dischargeScreenOffUah += drain
            }

            when {
                cur.wifiOn -> {
                    wifiMs += duration
                    wifiUah += drain
                }
                cur.mobileOn -> {
                    mobileMs += duration
                    mobileUah += drain
                }
                else -> {
                    offlineMs += duration
                    offlineUah += drain
                }
            }
        }

        val buckets = listOf(
            StateBucket("WiFi", avgMa(wifiUah, wifiMs), wifiMs, wifiUah),
            StateBucket("Datos móviles", avgMa(mobileUah, mobileMs), mobileMs, mobileUah),
            StateBucket("Sin red", avgMa(offlineUah, offlineMs), offlineMs, offlineUah)
        )

        return HardwareAnalysis(
            periodStart = periodStart,
            periodEnd = periodEnd,
            monitoredMs = monitoredMs,
            dischargeMs = dischargeMs,
            dischargeUah = dischargeUah,
            chargeMs = chargeMs,
            chargeGainUah = chargeGainUah,
            screenOnMs = screenOnMs,
            screenOffMs = screenOffMs,
            screenOnAvgMa = avgMa(dischargeScreenOnUah, dischargeScreenOnMs),
            screenOffAvgMa = avgMa(dischargeScreenOffUah, dischargeScreenOffMs),
            overallAvgMa = avgMa(dischargeUah, dischargeMs),
            wifiAvgMa = avgMa(wifiUah, wifiMs),
            mobileAvgMa = avgMa(mobileUah, mobileMs),
            offlineAvgMa = avgMa(offlineUah, offlineMs),
            avgTempC = if (tempWeight > 0) (tempWeightedSum / tempWeight).toFloat() else null,
            maxTempC = maxTemp,
            stateBuckets = buckets,
            samples = samples.size
        )
    }

    private fun energyGainedUah(prev: Sample, cur: Sample, capacityMah: Int): Long? {
        val counterGain = if (prev.chargeCounterUah != null && cur.chargeCounterUah != null) {
            val difference = cur.chargeCounterUah - prev.chargeCounterUah
            val safeLimit = capacityMah.toLong() * 1000L * 3L
            if (difference > 0 && difference <= safeLimit) difference else null
        } else {
            null
        }
        if (counterGain != null) return counterGain
        return if (cur.level > prev.level) {
            (cur.level - prev.level).toLong() * capacityMah.toLong() * 1000L
        } else {
            0L
        }
    }

    private fun avgMa(drainUah: Long, durationMs: Long): Float? {
        if (durationMs <= 0L || drainUah <= 0L) return null
        return (drainUah.toDouble() * 3_600_000.0 / durationMs / 1000.0).toFloat()
    }

    /**
     * Reparte el consumo de un intervalo entre las apps que estuvieron en
     * primer plano. La parte de tiempo sin app activa queda como sistema/reposo.
     */
    fun attributeDrain(
        drainUah: Long,
        activeMs: Map<String, Long>,
        intervalMs: Long
    ): Map<String, Long> {
        if (drainUah <= 0L || intervalMs <= 0L || activeMs.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Long>()
        activeMs.forEach { (pkg, duration) ->
            if (pkg.isBlank() || duration <= 0L) return@forEach
            val capped = duration.coerceAtMost(intervalMs)
            val share = (drainUah.toDouble() * capped / intervalMs).toLong()
            if (share > 0L) result[pkg] = share
        }
        return result
    }

    fun chargingSessions(
        samples: List<Sample>,
        capacityMah: Int
    ): List<ChargingSession> {
        if (samples.size < 2) return emptyList()
        val sessions = ArrayList<ChargingSession>()
        var activeStart: Sample? = null
        var lastCharging: Sample? = null
        var maxTemp: Float? = null
        val currents = ArrayList<Float>()

        fun closeSession(endSample: Sample) {
            val start = activeStart ?: return
            val duration = (endSample.ts - start.ts).coerceAtLeast(0L)
            if (duration >= 60_000L) {
                val added = when {
                    start.chargeCounterUah != null && endSample.chargeCounterUah != null -> {
                        (endSample.chargeCounterUah - start.chargeCounterUah).takeIf { it > 0 }
                    }
                    endSample.level > start.level ->
                        (endSample.level - start.level).toLong() * capacityMah.toLong() * 1000L
                    else -> null
                }
                sessions.add(
                    ChargingSession(
                        startTs = start.ts,
                        endTs = endSample.ts,
                        startLevel = start.level,
                        endLevel = endSample.level,
                        addedUah = added,
                        durationMs = duration,
                        avgCurrentMa = if (currents.isEmpty()) null else currents.average().toFloat(),
                        maxTempC = maxTemp,
                        plugged = endSample.plugged
                    )
                )
            }
            activeStart = null
            lastCharging = null
            maxTemp = null
            currents.clear()
        }

        samples.forEach { sample ->
            if (sample.isChargingConnection) {
                if (activeStart == null) activeStart = sample
                lastCharging = sample
                sample.temperatureC?.let { maxTemp = maxOf(maxTemp ?: it, it) }
                val currentMa = sample.currentUa?.let { abs(it) / 1000f }?.takeIf { it > 0f }
                if (currentMa != null) currents.add(currentMa)
            } else if (activeStart != null) {
                closeSession(sample)
            }
        }

        lastCharging?.let { end ->
            if (activeStart != null) closeSession(end)
        }

        return sessions.asReversed()
    }
}
