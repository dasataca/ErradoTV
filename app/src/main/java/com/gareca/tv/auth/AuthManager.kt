package com.gareca.tv.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.gareca.tv.api.ApiClient
import com.gareca.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class AuthResult {
    data class Success(val email: String, val message: String = "Acceso concedido") : AuthResult()
    data class Error(val message: String) : AuthResult()
    object AccountDisabled : AuthResult()
    object DeviceLimitReached : AuthResult()
}

enum class SessionState {
    ACTIVE,
    DISABLED,
    DEVICE_CONFLICT,
    NOT_LOGGED_IN
}

object AuthManager {

    private const val PREFS_NAME = "gareca_auth_prefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_DEVICE_ID = "device_id"

    private const val SUPABASE_AUTH_URL = "https://pfhjrplnfuupftgzdnop.supabase.co/auth/v1"
    private const val SUPABASE_REST_URL = "https://pfhjrplnfuupftgzdnop.supabase.co/rest/v1"
    private const val SERVICE_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBmaGpycGxuZnV1cGZ0Z3pkbm9wIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODk1Nzg0MSwiZXhwIjoyMTA0NTMzODQxfQ.g79nmXleslc4M7W_hmJZs-CvwkEcFfTMUQHB79RK4ME"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrEmpty()) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            deviceId = if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                UUID.randomUUID().toString()
            }
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOGGED_IN, false)
    }

    fun getUserEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, "").orEmpty()
    }

    private fun saveSession(context: Context, email: String, token: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_AUTH_TOKEN, token.orEmpty())
            .apply()
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .putString(KEY_USER_EMAIL, "")
            .putString(KEY_AUTH_TOKEN, "")
            .apply()
        SupabaseLogger.info("auth_logout", "Sesión cerrada localmente")
    }

    suspend fun loginWithEmailPassword(
        context: Context,
        email: String,
        pass: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        if (cleanEmail.isEmpty() || cleanPass.isEmpty()) {
            return@withContext AuthResult.Error("Por favor completa todos los campos")
        }

        try {
            val bodyJson = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }

            val req = Request.Builder()
                .url("$SUPABASE_AUTH_URL/token?grant_type=password")
                .header("apikey", SERVICE_KEY)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = ApiClient.httpClient.newCall(req).execute()
            val respBody = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                val errorMsg = try {
                    JSONObject(respBody).optString("error_description", "Credenciales incorrectas")
                } catch (_: Exception) {
                    "Usuario o contraseña incorrectos"
                }
                return@withContext AuthResult.Error(errorMsg)
            }

            val token = try {
                JSONObject(respBody).optString("access_token", "")
            } catch (_: Exception) {
                ""
            }

            // Validar en la tabla gareca_users
            return@withContext processUserAccess(context, cleanEmail, token, "email_password")
        } catch (e: Exception) {
            SupabaseLogger.error("auth_login_error", "Error en login email: ${e.message}")
            return@withContext AuthResult.Error("Error de conexión: ${e.message}")
        }
    }

    suspend fun loginWithGoogle(context: Context, googleEmail: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = googleEmail.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return@withContext AuthResult.Error("Correo de Google no válido")
        }
        return@withContext processUserAccess(context, cleanEmail, null, "google_tv")
    }

    private suspend fun processUserAccess(
        context: Context,
        email: String,
        token: String?,
        provider: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val deviceBrand = Build.BRAND.orEmpty()
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        try {
            // 1. Consultar registro del usuario en gareca_users
            val queryReq = Request.Builder()
                .url("$SUPABASE_REST_URL/gareca_users?email=eq.$email&select=*")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .build()

            val queryResp = ApiClient.httpClient.newCall(queryReq).execute()
            val queryBody = queryResp.body?.string().orEmpty()

            if (queryResp.code == 404) {
                // La tabla gareca_users aún no fue creada en Supabase (o está pendiente).
                // Permitimos acceso en esta fase inicial y registramos telemetría
                saveSession(context, email, token)
                SupabaseLogger.info(
                    "auth_login_success",
                    "Ingreso exitoso por $provider: $email (Modo Resiliente - Tabla pendiente)",
                    mapOf("email" to email, "provider" to provider, "device_id" to deviceId)
                )
                return@withContext AuthResult.Success(email)
            }

            val usersArray = JSONArray(queryBody)

            if (usersArray.length() == 0) {
                // Usuario nuevo: auto-registro con status 'enabled' y max_devices = 1
                val insertJson = JSONObject().apply {
                    put("email", email)
                    put("status", "enabled")
                    put("max_devices", 1)
                    put("active_device_id", deviceId)
                    put("device_model", deviceModel)
                    put("device_brand", deviceBrand)
                    put("android_version", androidVer)
                    put("app_version", "1.5.0")
                }

                val insertReq = Request.Builder()
                    .url("$SUPABASE_REST_URL/gareca_users")
                    .header("apikey", SERVICE_KEY)
                    .header("Authorization", "Bearer $SERVICE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .post(insertJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                ApiClient.httpClient.newCall(insertReq).execute().close()

                saveSession(context, email, token)
                SupabaseLogger.info(
                    "user_registered",
                    "Nuevo usuario registrado en GarecaTV: $email ($provider)",
                    mapOf("email" to email, "device_id" to deviceId, "device_model" to deviceModel)
                )
                return@withContext AuthResult.Success(email)
            } else {
                // Usuario existente
                val userObj = usersArray.getJSONObject(0)
                val status = userObj.optString("status", "enabled")
                val maxDevices = userObj.optInt("max_devices", 1)
                val activeDeviceId = userObj.optString("active_device_id", "")

                // A. ¿Está deshabilitado por el administrador?
                if (status.equals("disabled", ignoreCase = true)) {
                    SupabaseLogger.warn(
                        "auth_blocked_disabled",
                        "Intento de acceso de cuenta deshabilitada: $email",
                        mapOf("email" to email, "device_model" to deviceModel)
                    )
                    return@withContext AuthResult.AccountDisabled
                }

                // B. Actualizar dispositivo activo y última conexión
                if (maxDevices == 1 && activeDeviceId.isNotEmpty() && activeDeviceId != deviceId) {
                    SupabaseLogger.info(
                        "auth_device_switch",
                        "Usuario $email cambió de dispositivo activo a $deviceModel (Anterior: $activeDeviceId)",
                        mapOf("email" to email, "old_device" to activeDeviceId, "new_device" to deviceId)
                    )
                }

                val updateJson = JSONObject().apply {
                    put("active_device_id", deviceId)
                    put("device_model", deviceModel)
                    put("device_brand", deviceBrand)
                    put("android_version", androidVer)
                    put("app_version", "1.5.0")
                    put("last_login_at", "now()")
                }

                val updateReq = Request.Builder()
                    .url("$SUPABASE_REST_URL/gareca_users?email=eq.$email")
                    .header("apikey", SERVICE_KEY)
                    .header("Authorization", "Bearer $SERVICE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(updateJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                ApiClient.httpClient.newCall(updateReq).execute().close()

                saveSession(context, email, token)
                SupabaseLogger.info(
                    "auth_login_success",
                    "Sesión iniciada: $email ($provider) en $deviceModel",
                    mapOf("email" to email, "device_id" to deviceId)
                )
                return@withContext AuthResult.Success(email)
            }
        } catch (e: Exception) {
            SupabaseLogger.error("auth_process_error", "Error validando usuario: ${e.message}")
            // Fallback: Si ocurre un error de red pero la contraseña o google fue validado, permitir entrar
            saveSession(context, email, token)
            return@withContext AuthResult.Success(email)
        }
    }

    suspend fun verifyActiveSession(context: Context): SessionState = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) return@withContext SessionState.NOT_LOGGED_IN

        val email = getUserEmail(context)
        if (email.isEmpty()) return@withContext SessionState.NOT_LOGGED_IN

        val currentDeviceId = getDeviceId(context)

        try {
            val req = Request.Builder()
                .url("$SUPABASE_REST_URL/gareca_users?email=eq.$email&select=status,active_device_id,max_devices")
                .header("apikey", SERVICE_KEY)
                .header("Authorization", "Bearer $SERVICE_KEY")
                .build()

            val resp = ApiClient.httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext SessionState.ACTIVE

            val body = resp.body?.string().orEmpty()
            val arr = JSONArray(body)
            if (arr.length() == 0) return@withContext SessionState.ACTIVE

            val obj = arr.getJSONObject(0)
            val status = obj.optString("status", "enabled")
            val activeDeviceId = obj.optString("active_device_id", "")
            val maxDevices = obj.optInt("max_devices", 1)

            if (status.equals("disabled", ignoreCase = true)) {
                return@withContext SessionState.DISABLED
            }

            if (maxDevices == 1 && activeDeviceId.isNotEmpty() && activeDeviceId != currentDeviceId) {
                return@withContext SessionState.DEVICE_CONFLICT
            }

            SessionState.ACTIVE
        } catch (_: Exception) {
            SessionState.ACTIVE
        }
    }
}
