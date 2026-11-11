package com.batteriatotal.monitor

import android.os.BatteryManager
import com.batteriatotal.data.BatteryRepository
import com.batteriatotal.data.GameDetector
import com.batteriatotal.data.GameSession
import com.batteriatotal.data.Sample
import kotlin.math.abs

/**
 * Mantiene la sesión de juego activa y la persiste en SQLite. No intenta
 * controlar la carga: solo observa la batería mientras un juego está delante.
 */
class GameSessionTracker(private val repository: BatteryRepository) {

    data class Result(
        val session: GameSession?,
        val started: Boolean = false,
        val warningTriggered: Boolean = false
    )

    private var active: GameSession? = null
    private var warningNotified = false

    fun update(
        sample: Sample,
        previous: Sample?,
        game: GameDetector.GameApp?,
        enabled: Boolean
    ): Result {
        if (!enabled || game == null || !sample.screenOn) {
            if (active != null) finish(sample.ts)
            return Result(null)
        }

        val current = active
        if (current == null || current.packageName != game.packageName) {
            if (current != null) finish(sample.ts)
            val started = start(sample, game)
            return Result(started, started = true)
        }
        return updateActive(current, sample, previous)
    }

    fun finish(endTs: Long) {
        val current = active ?: return
        repository.finishGameSession(current.id, endTs.coerceAtLeast(current.lastTs))
        active = null
        warningNotified = false
    }

    fun activeSession(): GameSession? = active

    private fun start(sample: Sample, game: GameDetector.GameApp): GameSession {
        val currentMa = sample.currentMa()
        val session = GameSession(
            startTs = sample.ts,
            lastTs = sample.ts,
            endTs = null,
            packageName = game.packageName,
            label = game.label,
            startLevel = sample.level,
            endLevel = sample.level,
            startChargeUah = sample.chargeCounterUah,
            endChargeUah = sample.chargeCounterUah,
            minTempC = sample.temperatureC,
            maxTempC = sample.temperatureC,
            avgCurrentMa = currentMa,
            maxCurrentMa = currentMa,
            pluggedMs = 0L,
            plugged = sample.plugged != 0,
            insufficientCharger = false,
            sampleCount = 1
        )
        val id = repository.insertGameSession(session)
        val stored = session.copy(id = id)
        active = stored
        warningNotified = false
        return stored
    }

    private fun updateActive(
        current: GameSession,
        sample: Sample,
        previous: Sample?
    ): Result {
        val gap = previous?.let {
            (sample.ts - it.ts).coerceIn(0L, MAX_GAP_MS)
        } ?: 0L
        val pluggedMs = current.pluggedMs + if (
            previous?.isChargingConnection == true && sample.isChargingConnection
        ) {
            gap
        } else {
            0L
        }

        val currentMa = sample.currentMa()
        val count = current.sampleCount + 1
        val avgCurrent = when {
            currentMa == null -> current.avgCurrentMa
            current.avgCurrentMa == null -> currentMa
            else -> (current.avgCurrentMa * (count - 1) + currentMa) / count
        }
        val maxCurrent = listOfNotNull(current.maxCurrentMa, currentMa).maxOrNull()
        val minTemp = listOfNotNull(current.minTempC, sample.temperatureC).minOrNull()
        val maxTemp = listOfNotNull(current.maxTempC, sample.temperatureC).maxOrNull()

        var insufficient = current.insufficientCharger
        if (sample.plugged != 0 && previous != null && previous.plugged != 0) {
            val prevCharge = previous.chargeCounterUah
            val curCharge = sample.chargeCounterUah
            val counterDrop = prevCharge != null && curCharge != null &&
                prevCharge - curCharge > INSUFFICIENT_COUNTER_DROP_UAH
            val statusDrop = sample.status == BatteryManager.BATTERY_STATUS_DISCHARGING &&
                sample.level < previous.level
            if (counterDrop || statusDrop) insufficient = true
        }

        val updated = current.copy(
            lastTs = sample.ts,
            endLevel = sample.level,
            endChargeUah = sample.chargeCounterUah ?: current.endChargeUah,
            minTempC = minTemp,
            maxTempC = maxTemp,
            avgCurrentMa = avgCurrent,
            maxCurrentMa = maxCurrent,
            pluggedMs = pluggedMs,
            plugged = sample.plugged != 0,
            insufficientCharger = insufficient,
            sampleCount = count
        )
        repository.updateGameSession(updated)
        active = updated

        val warning = updated.insufficientCharger &&
            !current.insufficientCharger &&
            !warningNotified
        if (warning) warningNotified = true
        return Result(updated, warningTriggered = warning)
    }

    private fun Sample.currentMa(): Float? =
        currentUa?.let { abs(it) / 1000f }?.takeIf { it > 0.1f }

    companion object {
        private const val MAX_GAP_MS = 10L * 60L * 1000L
        private const val INSUFFICIENT_COUNTER_DROP_UAH = 8_000L
    }
}
