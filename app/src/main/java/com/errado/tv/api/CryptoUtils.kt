package com.errado.tv.api

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    // Clave local por defecto integrada en la app
    const val DEFAULT_KEY_B64 = "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="

    // URL remota donde se puede alojar un JSON con la clave actualizada (GitHub Gist)
    var remoteConfigUrl: String = "https://gist.githubusercontent.com/dasataca/b5f27059b9db7e878e6a0fab05a7a951/raw/config.json"

    @Volatile
    var activeKeyB64: String = DEFAULT_KEY_B64

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("errado_tv_prefs", Context.MODE_PRIVATE)
        activeKeyB64 = prefs.getString("cached_key", DEFAULT_KEY_B64) ?: DEFAULT_KEY_B64
    }

    suspend fun syncRemoteKey(context: Context, httpClient: OkHttpClient) = withContext(Dispatchers.IO) {
        if (remoteConfigUrl.isEmpty()) return@withContext
        try {
            val req = Request.Builder().url(remoteConfigUrl).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val newKey = json.optString("active_key", "").trim()
                if (newKey.isNotEmpty() && newKey != activeKeyB64) {
                    activeKeyB64 = newKey
                    val prefs = context.getSharedPreferences("errado_tv_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("cached_key", newKey).apply()
                }
            }
        } catch (_: Exception) {
            // Si la URL remota no existe aún o no hay internet, continúa usando la clave local
        }
    }

    fun decrypt(encryptedPayload: String): String {
        val trimmed = encryptedPayload.trim()
        if (trimmed.startsWith("{")) {
            return trimmed
        }

        // 1. Intenta descifrar con la clave activa en caché o remota
        val result = tryDecryptWithKey(trimmed, activeKeyB64)
        if (result.isNotEmpty()) return result

        // 2. Si falló y la clave activa era distinta de la predeterminada, intenta con la de fábrica
        if (activeKeyB64 != DEFAULT_KEY_B64) {
            val fallbackResult = tryDecryptWithKey(trimmed, DEFAULT_KEY_B64)
            if (fallbackResult.isNotEmpty()) {
                activeKeyB64 = DEFAULT_KEY_B64
                return fallbackResult
            }
        }

        return ""
    }

    private fun tryDecryptWithKey(payload: String, b64Key: String): String {
        return try {
            val rawBytes = Base64.decode(payload, Base64.NO_WRAP)
            if (rawBytes.size < 16) return ""

            val iv = Arrays.copyOfRange(rawBytes, 0, 16)
            val cipherText = Arrays.copyOfRange(rawBytes, 16, rawBytes.size)

            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(b64Key.toByteArray(Charsets.UTF_8))

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
