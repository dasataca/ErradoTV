package com.errado.tv.telemetry

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseLogger {

    private const val SUPABASE_URL = "https://pfhjrplnfuupftgzdnop.supabase.co/rest/v1"
    private const val SERVICE_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBmaGpycGxuZnV1cGZ0Z3pkbm9wIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODk1Nzg0MSwiZXhwIjoyMTA0NTMzODQxfQ.g79nmXleslc4M7W_hmJZs-CvwkEcFfTMUQHB79RK4ME"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val deviceBrand = Build.BRAND.orEmpty()
    private val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    private val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun log(level: String, event: String, message: String, details: Map<String, Any?> = emptyMap()) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val detailsJson = JSONObject()
                detailsJson.put("device_model", deviceModel)
                detailsJson.put("device_brand", deviceBrand)
                detailsJson.put("android_version", androidVersion)
                details.forEach { (k, v) -> detailsJson.put(k, v) }

                // 1. Intentar enviar a errado_tv_logs
                val bodyJson = JSONObject().apply {
                    put("level", level)
                    put("event", event)
                    put("message", message)
                    put("device_model", deviceModel)
                    put("device_brand", deviceBrand)
                    put("android_version", androidVersion)
                    put("app_version", "1.0.0")
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

                val res = httpClient.newCall(req).execute()
                if (!res.isSuccessful) {
                    // Fallback a karaoke_system_logs si la tabla aún no fue creada con el script SQL
                    val fallbackJson = JSONObject().apply {
                        put("room_code", "GARECA_TV")
                        put("node_type", "tv")
                        put("level", level)
                        put("event", event)
                        put("message", message)
                        put("details", detailsJson)
                    }
                    val fallbackReq = Request.Builder()
                        .url("$SUPABASE_URL/karaoke_system_logs")
                        .header("apikey", SERVICE_KEY)
                        .header("Authorization", "Bearer $SERVICE_KEY")
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .post(fallbackJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(fallbackReq).execute().close()
                } else {
                    res.close()
                }
            } catch (_: Exception) {
                // Silencio: el registro nunca debe ralentizar ni bloquear la interfaz del televisor
            }
        }
    }

    fun info(event: String, message: String, details: Map<String, Any?> = emptyMap()) =
        log("info", event, message, details)

    fun warn(event: String, message: String, details: Map<String, Any?> = emptyMap()) =
        log("warn", event, message, details)

    fun error(event: String, message: String, details: Map<String, Any?> = emptyMap()) =
        log("error", event, message, details)
}
