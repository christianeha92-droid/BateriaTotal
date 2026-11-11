package com.batteriatotal.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BatteryRepository private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                level INTEGER NOT NULL,
                current_ua INTEGER,
                voltage_mv INTEGER,
                temp_c REAL,
                status INTEGER NOT NULL,
                plugged INTEGER NOT NULL,
                charge_uah INTEGER,
                energy_nwh INTEGER,
                screen_on INTEGER NOT NULL,
                wifi_on INTEGER NOT NULL,
                mobile_on INTEGER NOT NULL,
                brightness INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_samples_ts ON samples(ts)")
        db.execSQL(
            """
            CREATE TABLE app_drains (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                pkg TEXT NOT NULL,
                drain_uah INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_app_drains_ts ON app_drains(ts)")
        db.execSQL("CREATE INDEX idx_app_drains_pkg ON app_drains(pkg)")
        createGameSessions(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createGameSessions(db)
        }
    }

    private fun createGameSessions(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_ts INTEGER NOT NULL,
                last_ts INTEGER NOT NULL,
                end_ts INTEGER,
                pkg TEXT NOT NULL,
                label TEXT NOT NULL,
                start_level INTEGER NOT NULL,
                end_level INTEGER NOT NULL,
                start_charge_uah INTEGER,
                end_charge_uah INTEGER,
                min_temp_c REAL,
                max_temp_c REAL,
                avg_current_ma REAL,
                max_current_ma REAL,
                plugged_ms INTEGER NOT NULL,
                plugged INTEGER NOT NULL,
                insufficient_charger INTEGER NOT NULL,
                sample_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_game_sessions_start ON game_sessions(start_ts)")
    }

    fun record(sample: Sample, appDrains: Map<String, Long>) {
        synchronized(this) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.insert("samples", null, sampleValues(sample))
                appDrains.forEach { (pkg, drain) ->
                    if (drain > 0 && pkg.isNotBlank()) {
                        val values = ContentValues().apply {
                            put("ts", sample.ts)
                            put("pkg", pkg)
                            put("drain_uah", drain)
                        }
                        db.insert("app_drains", null, values)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        bump()
    }

    fun samplesBetween(startTs: Long, endTs: Long): List<Sample> {
        val result = ArrayList<Sample>()
        val db = readableDatabase
        db.query(
            "samples",
            SAMPLE_COLUMNS,
            "ts >= ? AND ts <= ?",
            arrayOf(startTs.toString(), endTs.toString()),
            null,
            null,
            "ts ASC",
            "50000"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.toSample())
            }
        }
        return result
    }

    fun appDrainsBetween(startTs: Long, endTs: Long): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT pkg, SUM(drain_uah) FROM app_drains WHERE ts >= ? AND ts <= ? GROUP BY pkg",
            arrayOf(startTs.toString(), endTs.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(0)] = cursor.getLong(1)
            }
        }
        return result
    }

    fun lastSample(): Sample? {
        val db = readableDatabase
        db.query(
            "samples",
            SAMPLE_COLUMNS,
            null,
            null,
            null,
            null,
            "ts DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToNext()) cursor.toSample() else null
        }
    }

    fun insertGameSession(session: GameSession): Long = synchronized(this) {
        writableDatabase.insertOrThrow("game_sessions", null, gameValues(session))
    }

    fun updateGameSession(session: GameSession) {
        if (session.id <= 0L) return
        synchronized(this) {
            writableDatabase.update(
                "game_sessions",
                gameValues(session),
                "id = ?",
                arrayOf(session.id.toString())
            )
        }
    }

    fun finishGameSession(id: Long, endTs: Long) {
        if (id <= 0L) return
        synchronized(this) {
            val values = ContentValues().apply {
                put("end_ts", endTs)
                put("last_ts", endTs)
            }
            writableDatabase.update(
                "game_sessions",
                values,
                "id = ?",
                arrayOf(id.toString())
            )
        }
    }

    /** Cierra sesiones que quedaron abiertas porque el servicio murió. */
    fun closeStaleGameSessions(cutoffTs: Long) {
        synchronized(this) {
            writableDatabase.execSQL(
                "UPDATE game_sessions SET end_ts = last_ts " +
                    "WHERE end_ts IS NULL AND last_ts < ?",
                arrayOf<Any>(cutoffTs)
            )
        }
    }

    fun activeGameSession(now: Long): GameSession? {
        val db = readableDatabase
        db.query(
            "game_sessions",
            GAME_COLUMNS,
            "end_ts IS NULL AND last_ts >= ?",
            arrayOf((now - ACTIVE_GAME_WINDOW_MS).toString()),
            null,
            null,
            "start_ts DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToNext()) cursor.toGameSession() else null
        }
    }

    fun gameSessions(limit: Int = 50): List<GameSession> {
        val result = ArrayList<GameSession>()
        val db = readableDatabase
        db.query(
            "game_sessions",
            GAME_COLUMNS,
            "end_ts IS NOT NULL",
            null,
            null,
            null,
            "start_ts DESC",
            limit.coerceIn(1, 200).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.toGameSession())
            }
        }
        return result
    }

    fun clearAll() {
        synchronized(this) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("samples", null, null)
                db.delete("app_drains", null, null)
                db.delete("game_sessions", null, null)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        bump()
    }

    fun prune(cutoffTs: Long) {
        synchronized(this) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("samples", "ts < ?", arrayOf(cutoffTs.toString()))
                db.delete("app_drains", "ts < ?", arrayOf(cutoffTs.toString()))
                db.delete("game_sessions", "start_ts < ?", arrayOf(cutoffTs.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        bump()
    }

    fun bump() {
        _version.value = _version.value + 1
    }

    private fun sampleValues(sample: Sample): ContentValues = ContentValues().apply {
        put("ts", sample.ts)
        put("level", sample.level)
        put("current_ua", sample.currentUa)
        put("voltage_mv", sample.voltageMv)
        put("temp_c", sample.temperatureC)
        put("status", sample.status)
        put("plugged", sample.plugged)
        put("charge_uah", sample.chargeCounterUah)
        put("energy_nwh", sample.energyCounterNwh)
        put("screen_on", if (sample.screenOn) 1 else 0)
        put("wifi_on", if (sample.wifiOn) 1 else 0)
        put("mobile_on", if (sample.mobileOn) 1 else 0)
        put("brightness", sample.brightness)
    }

    private fun gameValues(session: GameSession): ContentValues = ContentValues().apply {
        put("start_ts", session.startTs)
        put("last_ts", session.lastTs)
        put("end_ts", session.endTs)
        put("pkg", session.packageName)
        put("label", session.label)
        put("start_level", session.startLevel)
        put("end_level", session.endLevel)
        put("start_charge_uah", session.startChargeUah)
        put("end_charge_uah", session.endChargeUah)
        put("min_temp_c", session.minTempC)
        put("max_temp_c", session.maxTempC)
        put("avg_current_ma", session.avgCurrentMa)
        put("max_current_ma", session.maxCurrentMa)
        put("plugged_ms", session.pluggedMs)
        put("plugged", if (session.plugged) 1 else 0)
        put("insufficient_charger", if (session.insufficientCharger) 1 else 0)
        put("sample_count", session.sampleCount)
    }

    private fun android.database.Cursor.toGameSession(): GameSession = GameSession(
        id = getLong(getColumnIndexOrThrow("id")),
        startTs = getLong(getColumnIndexOrThrow("start_ts")),
        lastTs = getLong(getColumnIndexOrThrow("last_ts")),
        endTs = getNullableLong("end_ts"),
        packageName = getString(getColumnIndexOrThrow("pkg")),
        label = getString(getColumnIndexOrThrow("label")),
        startLevel = getInt(getColumnIndexOrThrow("start_level")),
        endLevel = getInt(getColumnIndexOrThrow("end_level")),
        startChargeUah = getNullableLong("start_charge_uah"),
        endChargeUah = getNullableLong("end_charge_uah"),
        minTempC = getNullableFloat("min_temp_c"),
        maxTempC = getNullableFloat("max_temp_c"),
        avgCurrentMa = getNullableFloat("avg_current_ma"),
        maxCurrentMa = getNullableFloat("max_current_ma"),
        pluggedMs = getLong(getColumnIndexOrThrow("plugged_ms")),
        plugged = getInt(getColumnIndexOrThrow("plugged")) == 1,
        insufficientCharger = getInt(getColumnIndexOrThrow("insufficient_charger")) == 1,
        sampleCount = getInt(getColumnIndexOrThrow("sample_count"))
    )

    private fun android.database.Cursor.toSample(): Sample = Sample(
        ts = getLong(getColumnIndexOrThrow("ts")),
        level = getInt(getColumnIndexOrThrow("level")),
        currentUa = getNullableLong("current_ua"),
        voltageMv = getNullableInt("voltage_mv"),
        temperatureC = getNullableFloat("temp_c"),
        status = getInt(getColumnIndexOrThrow("status")),
        plugged = getInt(getColumnIndexOrThrow("plugged")),
        chargeCounterUah = getNullableLong("charge_uah"),
        energyCounterNwh = getNullableLong("energy_nwh"),
        screenOn = getInt(getColumnIndexOrThrow("screen_on")) == 1,
        wifiOn = getInt(getColumnIndexOrThrow("wifi_on")) == 1,
        mobileOn = getInt(getColumnIndexOrThrow("mobile_on")) == 1,
        brightness = getNullableInt("brightness")
    )

    private fun android.database.Cursor.getNullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun android.database.Cursor.getNullableInt(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    private fun android.database.Cursor.getNullableFloat(column: String): Float? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getFloat(index)
    }

    companion object {
        private const val DB_NAME = "battery_history.db"
        private const val DB_VERSION = 2
        private const val ACTIVE_GAME_WINDOW_MS = 10L * 60L * 1000L

        private val SAMPLE_COLUMNS = arrayOf(
            "ts", "level", "current_ua", "voltage_mv", "temp_c", "status", "plugged",
            "charge_uah", "energy_nwh", "screen_on", "wifi_on", "mobile_on", "brightness"
        )

        private val GAME_COLUMNS = arrayOf(
            "id", "start_ts", "last_ts", "end_ts", "pkg", "label", "start_level",
            "end_level", "start_charge_uah", "end_charge_uah", "min_temp_c", "max_temp_c",
            "avg_current_ma", "max_current_ma", "plugged_ms", "plugged",
            "insufficient_charger", "sample_count"
        )

        @Volatile
        private var instance: BatteryRepository? = null

        fun get(context: Context): BatteryRepository =
            instance ?: synchronized(this) {
                instance ?: BatteryRepository(context).also { instance = it }
            }
    }
}
