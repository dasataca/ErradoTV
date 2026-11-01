package com.gareca.tv.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import com.gareca.tv.model.EventItem
import com.gareca.tv.model.EventStatus
import com.gareca.tv.model.StreamOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * Motor SQLite FTS de alto rendimiento para GarecaTV.
 * Proporciona persistencia en disco de bajo consumo de RAM, búsqueda de texto completo (FTS)
 * con índices B-Tree y consultas O(1) / O(log N) para canales de TV y eventos deportivos.
 */
class AppDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext ?: context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "gareca_tv.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext ?: context).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
            db.execSQL("PRAGMA synchronous = NORMAL;")
            db.execSQL("PRAGMA temp_store = MEMORY;")
            db.execSQL("PRAGMA cache_size = -2000;") // ~2MB RAM de caché
        } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Tabla principal de eventos y canales
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS channels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                title TEXT NOT NULL,
                subtitle TEXT,
                category TEXT NOT NULL,
                country TEXT,
                image_url TEXT,
                status TEXT NOT NULL,
                original_status TEXT,
                time TEXT,
                local_time TEXT,
                start_epoch_millis INTEGER,
                countdown_text TEXT,
                raw_date TEXT,
                timestamp INTEGER,
                stream_type TEXT,
                streams_json TEXT
            );
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_source ON channels(source);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_category ON channels(category);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_country ON channels(country);")

        // 2. Tabla virtual FTS4 para búsqueda instantánea
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS channels_fts USING fts4(
                content='channels',
                title,
                subtitle,
                category,
                country
            );
        """.trimIndent())

        // 3. Triggers de sincronización automática entre channels y channels_fts
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS channels_bu BEFORE UPDATE ON channels BEGIN
                DELETE FROM channels_fts WHERE docid = old.id;
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS channels_bd BEFORE DELETE ON channels BEGIN
                DELETE FROM channels_fts WHERE docid = old.id;
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS channels_ai AFTER INSERT ON channels BEGIN
                INSERT INTO channels_fts(docid, title, subtitle, category, country)
                VALUES (new.id, new.title, new.subtitle, new.category, new.country);
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS channels_au AFTER UPDATE ON channels BEGIN
                INSERT INTO channels_fts(docid, title, subtitle, category, country)
                VALUES (new.id, new.title, new.subtitle, new.category, new.country);
            END;
        """.trimIndent())

        // 4. Tabla de metadatos de streaming y resoluciones (StreamCache SQLite)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS stream_metadata (
                url TEXT PRIMARY KEY,
                resolution TEXT,
                is_unavailable INTEGER DEFAULT 0,
                updated_at INTEGER
            );
        """.trimIndent())

        // 5. Tabla de Canales Favoritos
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorites (
                channel_key TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
        """.trimIndent())
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS favorites (
                    channel_key TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );
            """.trimIndent())
        } catch (_: Exception) {}
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS channels_fts;")
        db.execSQL("DROP TABLE IF EXISTS channels;")
        db.execSQL("DROP TABLE IF EXISTS stream_metadata;")
        db.execSQL("DROP TABLE IF EXISTS favorites;")
        onCreate(db)
    }

    // ==========================================
    // OPERACIONES EN LOTE (BATCH INSERTS)
    // ==========================================

    fun saveChannels(source: String, items: List<EventItem>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        var stmt: SQLiteStatement? = null
        try {
            // Limpiar datos previos de la misma fuente
            db.delete("channels", "source = ?", arrayOf(source))

            val sql = """
                INSERT INTO channels (
                    source, title, subtitle, category, country, image_url,
                    status, original_status, time, local_time, start_epoch_millis,
                    countdown_text, raw_date, timestamp, stream_type, streams_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()
            stmt = db.compileStatement(sql)

            for (item in items) {
                stmt.clearBindings()
                stmt.bindString(1, source)
                stmt.bindString(2, item.title)
                stmt.bindString(3, item.subtitle)
                stmt.bindString(4, item.category)
                if (item.country != null) stmt.bindString(5, item.country) else stmt.bindNull(5)
                if (item.imageUrl != null) stmt.bindString(6, item.imageUrl) else stmt.bindNull(6)
                stmt.bindString(7, item.status.name)
                stmt.bindString(8, item.originalStatus.name)
                if (item.time != null) stmt.bindString(9, item.time) else stmt.bindNull(9)
                if (item.localTime != null) stmt.bindString(10, item.localTime) else stmt.bindNull(10)
                stmt.bindLong(11, item.startEpochMillis)
                if (item.countdownText != null) stmt.bindString(12, item.countdownText) else stmt.bindNull(12)
                if (item.rawDate != null) stmt.bindString(13, item.rawDate) else stmt.bindNull(13)
                stmt.bindLong(14, item.timestamp)
                stmt.bindString(15, item.streamType)
                stmt.bindString(16, serializeStreams(item.streams))
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            stmt?.close()
            db.endTransaction()
        }
    }

    // ==========================================
    // BÚSQUEDA DE TEXTO COMPLETO (FTS4)
    // ==========================================

    fun searchChannelsFts(query: String, source: String? = null, limit: Int = 100): List<EventItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val tokens = cleanQuery.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        // Construir consulta FTS con prefijo para cada palabra (ej. "boca* rac*")
        val ftsExpression = tokens.joinToString(" ") { "$it*" }

        val db = readableDatabase
        val results = mutableListOf<EventItem>()

        val sql = if (source != null) {
            """
                SELECT c.* FROM channels c
                JOIN channels_fts f ON c.id = f.docid
                WHERE channels_fts MATCH ? AND c.source = ?
                ORDER BY CASE WHEN c.status = 'REPLAY_UNAVAILABLE' THEN 1 ELSE 0 END, c.id ASC
                LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT c.* FROM channels c
                JOIN channels_fts f ON c.id = f.docid
                WHERE channels_fts MATCH ?
                ORDER BY CASE WHEN c.status = 'REPLAY_UNAVAILABLE' THEN 1 ELSE 0 END, c.id ASC
                LIMIT ?
            """.trimIndent()
        }

        val args = if (source != null) arrayOf(ftsExpression, source, limit.toString()) else arrayOf(ftsExpression, limit.toString())

        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(sql, args)
            while (cursor.moveToNext()) {
                results.add(cursorToEventItem(cursor))
            }
        } catch (_: Exception) {
            // Fallback en caso de caracteres especiales que SQLite FTS no pueda parsear directamente
            return searchChannelsLike(tokens, source, limit)
        } finally {
            cursor?.close()
        }
        return results
    }

    private fun searchChannelsLike(tokens: List<String>, source: String?, limit: Int): List<EventItem> {
        val db = readableDatabase
        val results = mutableListOf<EventItem>()

        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (source != null) {
            whereClauses.add("source = ?")
            args.add(source)
        }

        for (token in tokens) {
            whereClauses.add("(title LIKE ? OR subtitle LIKE ? OR category LIKE ? OR country LIKE ?)")
            val pattern = "%$token%"
            args.add(pattern)
            args.add(pattern)
            args.add(pattern)
            args.add(pattern)
        }

        args.add(limit.toString())
        val where = if (whereClauses.isNotEmpty()) "WHERE " + whereClauses.joinToString(" AND ") else ""
        val sql = "SELECT * FROM channels $where ORDER BY CASE WHEN status = 'REPLAY_UNAVAILABLE' THEN 1 ELSE 0 END LIMIT ?"

        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(sql, args.toTypedArray())
            while (cursor.moveToNext()) {
                results.add(cursorToEventItem(cursor))
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return results
    }

    fun getSuggestions(query: String, limit: Int = 8): List<String> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()

        val ftsExpression = "$clean*"
        val db = readableDatabase
        val suggestions = mutableListOf<String>()
        var cursor: Cursor? = null
        try {
            val sql = """
                SELECT DISTINCT c.title FROM channels c
                JOIN channels_fts f ON c.id = f.docid
                WHERE channels_fts MATCH ?
                LIMIT ?
            """.trimIndent()
            cursor = db.rawQuery(sql, arrayOf(ftsExpression, limit.toString()))
            while (cursor.moveToNext()) {
                suggestions.add(cursor.getString(0))
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return suggestions
    }

    // ==========================================
    // CONSULTAS POR CATEGORÍA Y PAÍS
    // ==========================================

    fun getChannelsByCategory(category: String, source: String = "cxtv"): List<EventItem> {
        val db = readableDatabase
        val list = mutableListOf<EventItem>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT * FROM channels WHERE source = ? AND category = ? ORDER BY CASE WHEN status = 'REPLAY_UNAVAILABLE' THEN 1 ELSE 0 END, title ASC",
                arrayOf(source, category)
            )
            while (cursor.moveToNext()) {
                list.add(cursorToEventItem(cursor))
            }
        } finally {
            cursor?.close()
        }
        return list
    }

    fun getChannelsByCountry(country: String, source: String = "cxtv"): List<EventItem> {
        val db = readableDatabase
        val list = mutableListOf<EventItem>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT * FROM channels WHERE source = ? AND country = ? ORDER BY CASE WHEN status = 'REPLAY_UNAVAILABLE' THEN 1 ELSE 0 END, category ASC, title ASC",
                arrayOf(source, country)
            )
            while (cursor.moveToNext()) {
                list.add(cursorToEventItem(cursor))
            }
        } finally {
            cursor?.close()
        }
        return list
    }

    fun getAllChannels(source: String = "cxtv"): List<EventItem> {
        val db = readableDatabase
        val list = mutableListOf<EventItem>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT * FROM channels WHERE source = ? ORDER BY CASE WHEN status = 'REPLAY_UNAVAILABLE' THEN 1 ELSE 0 END, category ASC, title ASC",
                arrayOf(source)
            )
            while (cursor.moveToNext()) {
                list.add(cursorToEventItem(cursor))
            }
        } finally {
            cursor?.close()
        }
        return list
    }

    fun getSubcategoriesByCountry(country: String, source: String = "cxtv"): List<Pair<String, Int>> {
        val db = readableDatabase
        val list = mutableListOf<Pair<String, Int>>()
        var cursor: Cursor? = null
        try {
            val sql = "SELECT category, COUNT(*) as cnt FROM channels WHERE source = ? AND country = ? GROUP BY category ORDER BY category ASC"
            cursor = db.rawQuery(sql, arrayOf(source, country))
            while (cursor.moveToNext()) {
                val cat = cursor.getString(0)
                val cnt = cursor.getInt(1)
                if (!cat.isNullOrBlank()) {
                    list.add(Pair(cat, cnt))
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return list
    }

    fun getSubcategoriesByCategory(category: String, source: String = "cxtv"): List<Pair<String, Int>> {
        val db = readableDatabase
        val list = mutableListOf<Pair<String, Int>>()
        var cursor: Cursor? = null
        try {
            val sql = "SELECT country, COUNT(*) as cnt FROM channels WHERE source = ? AND category = ? AND country IS NOT NULL AND country != '' GROUP BY country ORDER BY country ASC"
            cursor = db.rawQuery(sql, arrayOf(source, category))
            while (cursor.moveToNext()) {
                val country = cursor.getString(0)
                val cnt = cursor.getInt(1)
                if (!country.isNullOrBlank()) {
                    list.add(Pair(country, cnt))
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return list
    }

    // ==========================================
    // STREAM CACHE (METADATOS Y RESOLUCIONES)
    // ==========================================

    fun saveStreamResolution(url: String, resolution: String) {
        if (url.isEmpty() || resolution.isEmpty()) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("resolution", resolution)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("stream_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getStreamResolution(url: String): String? {
        if (url.isEmpty()) return null
        val db = readableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT resolution FROM stream_metadata WHERE url = ? LIMIT 1", arrayOf(url))
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    fun getAllStreamResolutions(): Map<String, String> {
        val db = readableDatabase
        val map = mutableMapOf<String, String>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT url, resolution FROM stream_metadata WHERE resolution IS NOT NULL", null)
            while (cursor.moveToNext()) {
                val url = cursor.getString(0)
                val res = cursor.getString(1)
                if (!url.isNullOrEmpty() && !res.isNullOrEmpty()) {
                    map[url] = res
                }
            }
        } finally {
            cursor?.close()
        }
        return map
    }

    fun markStreamUnavailable(url: String) {
        if (url.isEmpty()) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("is_unavailable", 1)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("stream_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markStreamAvailable(url: String) {
        if (url.isEmpty()) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("is_unavailable", 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("stream_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUnavailableUrls(): Set<String> {
        val db = readableDatabase
        val set = mutableSetOf<String>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT url FROM stream_metadata WHERE is_unavailable = 1", null)
            while (cursor.moveToNext()) {
                set.add(cursor.getString(0))
            }
        } finally {
            cursor?.close()
        }
        return set
    }

    // ==========================================
    // SERIALIZADORES AUXILIARES
    // ==========================================

    private fun serializeStreams(streams: List<StreamOption>): String {
        val array = JSONArray()
        for (s in streams) {
            val obj = JSONObject().apply {
                put("title", s.title)
                put("url", s.url)
                put("type", s.type)
                if (s.drmLicenseUrl != null) put("drmLicenseUrl", s.drmLicenseUrl)
                if (s.userAgent != null) put("userAgent", s.userAgent)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeStreams(jsonStr: String?): List<StreamOption> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<StreamOption>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StreamOption(
                        title = obj.optString("title", "Opción"),
                        url = obj.optString("url", ""),
                        type = obj.optString("type", "exo"),
                        drmLicenseUrl = if (obj.has("drmLicenseUrl")) obj.getString("drmLicenseUrl") else null,
                        userAgent = if (obj.has("userAgent")) obj.getString("userAgent") else null
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun cursorToEventItem(cursor: Cursor): EventItem {
        val statusStr = cursor.getString(cursor.getColumnIndexOrThrow("status"))
        val origStatusStr = cursor.getString(cursor.getColumnIndexOrThrow("original_status"))
        val status = try { EventStatus.valueOf(statusStr) } catch (_: Exception) { EventStatus.CHANNEL_24_7 }
        val origStatus = try { EventStatus.valueOf(origStatusStr) } catch (_: Exception) { status }
        val streamsJson = cursor.getString(cursor.getColumnIndexOrThrow("streams_json"))
        val streams = deserializeStreams(streamsJson)

        return EventItem(
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            time = cursor.getString(cursor.getColumnIndexOrThrow("time")),
            subtitle = cursor.getString(cursor.getColumnIndexOrThrow("subtitle")),
            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url")),
            streams = streams,
            status = status,
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            rawDate = cursor.getString(cursor.getColumnIndexOrThrow("raw_date")),
            localTime = cursor.getString(cursor.getColumnIndexOrThrow("local_time")),
            startEpochMillis = cursor.getLong(cursor.getColumnIndexOrThrow("start_epoch_millis")),
            countdownText = cursor.getString(cursor.getColumnIndexOrThrow("countdown_text")),
            originalStatus = origStatus,
            country = cursor.getString(cursor.getColumnIndexOrThrow("country")),
            streamType = cursor.getString(cursor.getColumnIndexOrThrow("stream_type"))
        )
    }

    // ==========================================
    // CANALES FAVORITOS
    // ==========================================

    fun isFavorite(channelKey: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM favorites WHERE channel_key = ? LIMIT 1;", arrayOf(channelKey.trim().lowercase()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun addFavorite(channelKey: String, source: String = "cxtv") {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("channel_key", channelKey.trim().lowercase())
            put("source", source)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("favorites", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeFavorite(channelKey: String) {
        val db = writableDatabase
        db.delete("favorites", "channel_key = ?", arrayOf(channelKey.trim().lowercase()))
    }

    fun toggleFavorite(channelKey: String, source: String = "cxtv"): Boolean {
        val key = channelKey.trim().lowercase()
        return if (isFavorite(key)) {
            removeFavorite(key)
            false
        } else {
            addFavorite(key, source)
            true
        }
    }

    fun getAllFavorites(source: String = "cxtv"): Set<String> {
        val result = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT channel_key FROM favorites WHERE source = ?;", arrayOf(source))
        while (cursor.moveToNext()) {
            result.add(cursor.getString(0))
        }
        cursor.close()
        return result
    }
}
