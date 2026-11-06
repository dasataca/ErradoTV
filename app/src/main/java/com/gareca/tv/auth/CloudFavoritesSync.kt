package com.gareca.tv.auth

import android.content.Context
import com.gareca.tv.api.ApiClient
import com.gareca.tv.controller.CatalogStateManager
import com.gareca.tv.db.AppDatabase
import com.gareca.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class CloudFavoriteItem(
    val email: String,
    val channelKey: String,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
)

sealed class SyncResult {
    data class Success(val pulled: Int, val pushed: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object NotLoggedIn : SyncResult()
}

/**
 * Gestor de sincronización de canales favoritos en la nube (Supabase).
 * Implementa resolución de conflictos por Unión segura (Merge):
 * Nunca elimina favoritos locales en caso de desconexión o error de red.
 */
object CloudFavoritesSync {

    private const val SUPABASE_REST_URL = "https://pfhjrplnfuupftgzdnop.supabase.co/rest/v1"
    private const val SERVICE_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBmaGpycGxuZnV1cGZ0Z3pkbm9wIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODk1Nzg0MSwiZXhwIjoyMTA0NTMzODQxfQ.g79nmXleslc4M7W_hmJZs-CvwkEcFfTMUQHB79RK4ME"

    /**
     * Sincroniza bidireccionalmente los favoritos entre SQLite local y Supabase.
     */
    suspend fun syncFavorites(
        context: Context,
        catalogState: CatalogStateManager? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!AuthManager.isLoggedIn(context)) {
            return@withContext SyncResult.NotLoggedIn
        }

        val email = AuthManager.getUserEmail(context).trim().lowercase()
        if (email.isEmpty()) {
            return@withContext SyncResult.NotLoggedIn
        }

        try {
            val db = AppDatabase.getInstance(context)

            // 1. Obtener favoritos locales actuales
            val localSports = db.getAllFavorites("sports")
            val localCxtv = db.getAllFavorites("cxtv")
            val localFreeTv = db.getAllFavorites("freetv")

            val localMap = mutableMapOf<Pair<String, String>, Long>()
            localSports.forEach { localMap[Pair(it, "sports")] = System.currentTimeMillis() }
            localCxtv.forEach { localMap[Pair(it, "cxtv")] = System.currentTimeMillis() }
            localFreeTv.forEach { localMap[Pair(it, "freetv")] = System.currentTimeMillis() }

            // 2. Consultar favoritos remotos en Supabase
            val queryReq = Request.Builder()
                .url("$SUPABASE_REST_URL/errado_user_favorites?email=eq.$email&select=channel_key,source,updated_at")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .build()

            val resp = ApiClient.httpClient.newCall(queryReq).execute()
            val body = resp.body?.string().orEmpty()

            if (resp.code == 404) {
                // Tabla no creada aún en Supabase: no bloquea la app
                SupabaseLogger.info("cloud_favorites_pending_table", "Tabla errado_user_favorites pendiente en Supabase")
                return@withContext SyncResult.Success(pulled = 0, pushed = 0)
            }

            if (!resp.isSuccessful) {
                return@withContext SyncResult.Error("HTTP ${resp.code}: $body")
            }

            val remoteItems = mutableListOf<CloudFavoriteItem>()
            val remoteArray = JSONArray(body)
            for (i in 0 until remoteArray.length()) {
                val obj = remoteArray.getJSONObject(i)
                remoteItems.add(
                    CloudFavoriteItem(
                        email = email,
                        channelKey = obj.optString("channel_key", "").trim().lowercase(),
                        source = obj.optString("source", "cxtv").trim().lowercase()
                    )
                )
            }

            // 3. PULL: Insertar en local los favoritos remotos que falten
            var pulledCount = 0
            for (remote in remoteItems) {
                if (remote.channelKey.isNotEmpty()) {
                    val keyPair = Pair(remote.channelKey, remote.source)
                    if (!localMap.containsKey(keyPair)) {
                        db.addFavorite(remote.channelKey, remote.source)
                        when (remote.source) {
                            "sports" -> catalogState?.cachedSportsFavorites?.add(remote.channelKey)
                            "freetv" -> catalogState?.cachedFreeTvFavorites?.add(remote.channelKey)
                            else -> catalogState?.cachedCxtvFavorites?.add(remote.channelKey)
                        }
                        pulledCount++
                    }
                }
            }

            // 4. PUSH: Subir a Supabase los favoritos locales que falten en remoto
            val remoteKeysSet = remoteItems.map { Pair(it.channelKey, it.source) }.toSet()
            val toPush = localMap.keys.filter { !remoteKeysSet.contains(it) }
            var pushedCount = 0

            if (toPush.isNotEmpty()) {
                val pushArray = JSONArray()
                for (pair in toPush) {
                    pushArray.put(
                        JSONObject().apply {
                            put("email", email)
                            put("channel_key", pair.first)
                            put("source", pair.second)
                            put("updated_at", "now()")
                        }
                    )
                }

                val pushReq = Request.Builder()
                    .url("$SUPABASE_REST_URL/errado_user_favorites")
                    .header("apikey", SERVICE_KEY)
                    .header("Authorization", "Bearer $SERVICE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates")
                    .post(pushArray.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val pushResp = ApiClient.httpClient.newCall(pushReq).execute()
                if (pushResp.isSuccessful) {
                    pushedCount = toPush.size
                }
                pushResp.close()
            }

            SupabaseLogger.info(
                "cloud_favorites_synced",
                "Favoritos sincronizados para $email (Descargados: $pulledCount, Subidos: $pushedCount)"
            )
            SyncResult.Success(pulled = pulledCount, pushed = pushedCount)
        } catch (e: Exception) {
            SupabaseLogger.error("cloud_favorites_error", "Error sincronizando favoritos: ${e.message}")
            SyncResult.Error(e.message ?: "Error desconocido")
        }
    }

    /**
     * Registra un favorito inmediatamente en la nube tras marcarlo en la app.
     */
    suspend fun pushFavoriteAsync(context: Context, channelKey: String, source: String) = withContext(Dispatchers.IO) {
        if (!AuthManager.isLoggedIn(context)) return@withContext
        val email = AuthManager.getUserEmail(context).trim().lowercase()
        if (email.isEmpty()) return@withContext

        try {
            val itemJson = JSONObject().apply {
                put("email", email)
                put("channel_key", channelKey.trim().lowercase())
                put("source", source.trim().lowercase())
                put("updated_at", "now()")
            }

            val req = Request.Builder()
                .url("$SUPABASE_REST_URL/errado_user_favorites")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(itemJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            ApiClient.httpClient.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    /**
     * Remueve un favorito de la nube al desmarcarlo en la app.
     */
    suspend fun removeFavoriteAsync(context: Context, channelKey: String, source: String) = withContext(Dispatchers.IO) {
        if (!AuthManager.isLoggedIn(context)) return@withContext
        val email = AuthManager.getUserEmail(context).trim().lowercase()
        if (email.isEmpty()) return@withContext

        try {
            val req = Request.Builder()
                .url("$SUPABASE_REST_URL/errado_user_favorites?email=eq.$email&channel_key=eq.${channelKey.trim().lowercase()}&source=eq.${source.trim().lowercase()}")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .delete()
                .build()

            ApiClient.httpClient.newCall(req).execute().close()
        } catch (_: Exception) {}
    }
}
