package com.errado.tv

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
 * Permite que los testeos de señales caídas y transmisiones no disponibles detectadas
 * por una Smart TV se almacenen en la nube y se compartan instantáneamente con todas
 * las demás TVs que utilicen la aplicación.
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
     * Sincroniza desde Supabase todos los enlaces caídos reportados por cualquier Smart TV.
     * Se ejecuta en paralelo durante la carga inicial o actualización de la cartelera.
     */
    suspend fun syncFromCommunity(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<String>()
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/errado_tv_logs?event=eq.stream_unavailable&select=details,created_at&order=created_at.desc&limit=500")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .header("Accept", "application/json")
                .build()

            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val bodyStr = res.body?.string().orEmpty()
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
            res.close()
        } catch (_: Exception) {
            // Silencioso: si no hay conexión con Supabase, la TV continúa normalmente con su caché local
        }
        result
    }

    /**
     * Reporta un enlace individual caído hacia la base de datos comunitaria de Supabase.
     */
    fun reportUnavailable(url: String, reason: String, title: String? = null) {
        if (url.isEmpty() || !url.startsWith("http")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                    put("message", "Señal no disponible reportada por Smart TV: ${title ?: url.substringAfterLast('/')}")
                    put("device_model", deviceModel)
                    put("device_brand", deviceBrand)
                    put("android_version", androidVersion)
                    put("app_version", "1.3.2")
                    put("details", detailsJson)
                }

                val req = Request.Builder()
                    .url("$SUPABASE_URL/errado_tv_logs")
                    .header("apikey", SERVICE_KEY)
                    .header("Authorization", "Bearer $SERVICE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Reporta un lote masivo de enlaces caídos descubiertos durante el pretesteo de repeticiones.
     */
    fun reportBatchUnavailable(urls: List<String>, reason: String) {
        val validUrls = urls.filter { it.startsWith("http") }.distinct()
        if (validUrls.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonArray = JSONArray()
                for (url in validUrls.take(100)) {
                    val detailsJson = JSONObject().apply {
                        put("stream_url", url)
                        put("reason", reason)
                        put("device_model", deviceModel)
                        put("device_brand", deviceBrand)
                        put("android_version", androidVersion)
                    }

                    val obj = JSONObject().apply {
                        put("level", "warn")
                        put("event", "stream_unavailable")
                        put("message", "Señal caída detectada por pretesteo: ${url.substringAfterLast('/')}")
                        put("device_model", deviceModel)
                        put("device_brand", deviceBrand)
                        put("android_version", androidVersion)
                        put("app_version", "1.3.2")
                        put("details", detailsJson)
                    }
                    jsonArray.put(obj)
                }

                val req = Request.Builder()
                    .url("$SUPABASE_URL/errado_tv_logs")
                    .header("apikey", SERVICE_KEY)
                    .header("Authorization", "Bearer $SERVICE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .post(jsonArray.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }
}
