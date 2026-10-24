package com.gareca.tv

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gestor de Base de Datos Comunitaria para ErradoTV (Supabase).
 * Gestiona la tabla exclusiva public.errado_community_streams para compartir
 * enlaces caídos entre todos los Smart TVs en tiempo real.
 */
object CommunityStreamManager {

    private const val SUPABASE_URL = "https://pfhjrplnfuupftgzdnop.supabase.co/rest/v1"
    private const val SERVICE_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBmaGpycGxuZnV1cGZ0Z3pkbm9wIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODk1Nzg0MSwiZXhwIjoyMTA0NTMzODQxfQ.g79nmXleslc4M7W_hmJZs-CvwkEcFfTMUQHB79RK4ME"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val deviceBrand = Build.BRAND.orEmpty()
    private val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    private val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /**
     * Sincroniza desde la tabla exclusiva public.errado_community_streams los enlaces caídos.
     * Si la tabla aún no se ha creado en la base de datos, utiliza errado_tv_logs como fallback.
     */
    suspend fun syncFromCommunity(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<String>()
        try {
            // 1. Intentar leer desde la tabla exclusiva errado_community_streams
            val dedicatedReq = Request.Builder()
                .url("$SUPABASE_URL/errado_community_streams?status=eq.unavailable&select=stream_url")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .header("Accept", "application/json")
                .build()

            val dedicatedRes = httpClient.newCall(dedicatedReq).execute()
            if (dedicatedRes.isSuccessful) {
                val bodyStr = dedicatedRes.body?.string().orEmpty()
                if (bodyStr.isNotEmpty()) {
                    val array = JSONArray(bodyStr)
                    for (i in 0 until array.length()) {
                        val row = array.optJSONObject(i) ?: continue
                        val deadUrl = row.optString("stream_url", "").trim()
                        if (deadUrl.isNotEmpty() && deadUrl.startsWith("http")) {
                            result.add(deadUrl)
                            StreamCache.markUnavailable(context, deadUrl, broadcast = false)
                        }
                    }
                }
                dedicatedRes.close()
                return@withContext result
            }
            dedicatedRes.close()

            // 2. Fallback a errado_tv_logs si la tabla dedicada no existe todavía
            val fallbackReq = Request.Builder()
                .url("$SUPABASE_URL/errado_tv_logs?event=eq.stream_unavailable&select=details,created_at&order=created_at.desc&limit=500")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .header("Accept", "application/json")
                .build()

            val fallbackRes = httpClient.newCall(fallbackReq).execute()
            if (fallbackRes.isSuccessful) {
                val bodyStr = fallbackRes.body?.string().orEmpty()
                if (bodyStr.isNotEmpty()) {
                    val array = JSONArray(bodyStr)
                    for (i in 0 until array.length()) {
                        val row = array.optJSONObject(i) ?: continue
                        val details = row.optJSONObject("details") ?: continue
                        val deadUrl = details.optString("stream_url", "").trim()
                        if (deadUrl.isNotEmpty() && deadUrl.startsWith("http")) {
                            result.add(deadUrl)
                            StreamCache.markUnavailable(context, deadUrl, broadcast = false)
                        }
                    }
                }
            }
            fallbackRes.close()
        } catch (_: Exception) {
            // Silencioso: la TV continúa de inmediato con su caché local
        }
        result
    }

    /**
     * Reporta un enlace caído hacia la tabla exclusiva public.errado_community_streams.
     */
    fun reportUnavailable(url: String, reason: String, title: String? = null) {
        if (url.isEmpty() || !url.startsWith("http")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Intentar insertar / actualizar en la tabla exclusiva errado_community_streams
                val obj = JSONObject().apply {
                    put("stream_url", url)
                    put("status", "unavailable")
                    if (!title.isNullOrEmpty()) put("event_title", title)
                    put("failure_reason", reason)
                    put("device_model", deviceModel)
                    put("device_brand", deviceBrand)
                }

                val dedicatedReq = Request.Builder()
                    .url("$SUPABASE_URL/errado_community_streams")
                    .header("apikey", SERVICE_KEY)
                    .header("Authorization", "Bearer $SERVICE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(obj.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val res = httpClient.newCall(dedicatedReq).execute()
                val isSuccess = res.isSuccessful
                res.close()

                // 2. Si la tabla aún no existe (HTTP 404), respaldar en errado_tv_logs
                if (!isSuccess) {
                    val detailsJson = JSONObject().apply {
                        put("stream_url", url)
                        put("reason", reason)
                        if (!title.isNullOrEmpty()) put("title", title)
                        put("device_model", deviceModel)
                        put("device_brand", deviceBrand)
                        put("android_version", androidVersion)
                    }

                    val bodyJson = JSONObject().apply {
                        put("level", "warn")
                        put("event", "stream_unavailable")
                        put("message", "Señal caída reportada por Smart TV: ${title ?: url.substringAfterLast('/')}")
                        put("device_model", deviceModel)
                        put("device_brand", deviceBrand)
                        put("android_version", androidVersion)
                        put("app_version", "1.3.2")
                        put("details", detailsJson)
                    }

                    val fallbackReq = Request.Builder()
                        .url("$SUPABASE_URL/errado_tv_logs")
                        .header("apikey", SERVICE_KEY)
                        .header("Authorization", "Bearer $SERVICE_KEY")
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(fallbackReq).execute().close()
                }
            } catch (_: Exception) {}
        }
    }
}
