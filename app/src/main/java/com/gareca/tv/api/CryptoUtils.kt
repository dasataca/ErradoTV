package com.gareca.tv.api

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

    // Máscara y bytes protegidos para evitar extracción estática con Jadx / APKTool
    private const val SHIELD_MASK: Byte = 0x5A
    private val SHIELDED_KEY_BYTES = byteArrayOf(
        (0x08).toByte(), (0x68).toByte(), (0x63).toByte(), (0x29).toByte(), (0x03).toByte(), (0x02).toByte(), (0x2A).toByte(), (0x2C).toByte(),
        (0x38).toByte(), (0x6A).toByte(), (0x1C).toByte(), (0x2D).toByte(), (0x39).toByte(), (0x1C).toByte(), (0x14).toByte(), (0x36).toByte(),
        (0x03).toByte(), (0x69).toByte(), (0x10).toByte(), (0x36).toByte(), (0x3E).toByte(), (0x1F).toByte(), (0x2E).toByte(), (0x36).toByte(),
        (0x3F).toByte(), (0x0E).toByte(), (0x13).toByte(), (0x2D).toByte(), (0x17).toByte(), (0x30).toByte(), (0x08).toByte(), (0x0E).toByte(),
        (0x00).toByte(), (0x0D).toByte(), (0x14).toByte(), (0x6B).toByte(), (0x39).toByte(), (0x37).toByte(), (0x0F).toByte(), (0x32).toByte(),
        (0x13).toByte(), (0x0B).toByte(), (0x67).toByte(), (0x67).toByte()
    )

    fun getFactoryKey(): String {
        val raw = ByteArray(SHIELDED_KEY_BYTES.size) { i ->
            (SHIELDED_KEY_BYTES[i].toInt() xor SHIELD_MASK.toInt()).toByte()
        }
        return String(raw, Charsets.US_ASCII)
    }

    // Servidor DNS sobre HTTPS (DoH) seguro por defecto
    const val DEFAULT_DOH_URL = "https://cloudflare-dns.com/dns-query"

    // URLs remotas (Repositiorio GitHub como fuente principal automatizada y Gist como respaldo)
    val remoteConfigUrls = listOf(
        "https://raw.githubusercontent.com/dasataca/ErradoTV/main/version.json",
        "https://gist.githubusercontent.com/dasataca/b5f27059b9db7e878e6a0fab05a7a951/raw/config.json"
    )

    var remoteConfigUrl: String = remoteConfigUrls.first()

    @Volatile
    var activeKeyB64: String = getFactoryKey()

    @Volatile
    var activeDohUrl: String = DEFAULT_DOH_URL

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("gareca_tv_prefs", Context.MODE_PRIVATE)
        val oldPrefs = context.getSharedPreferences("errado_tv_prefs", Context.MODE_PRIVATE)
        val oldCachedKey = oldPrefs.getString("cached_key", null)
        if (oldCachedKey != null && !prefs.contains("cached_key")) {
            prefs.edit().putString("cached_key", oldCachedKey).apply()
        }
        activeKeyB64 = prefs.getString("cached_key", getFactoryKey()) ?: getFactoryKey()
        activeDohUrl = prefs.getString("cached_doh_url", DEFAULT_DOH_URL) ?: DEFAULT_DOH_URL
        com.gareca.tv.network.SecureDns.updateDohServer(activeDohUrl)
    }

    suspend fun syncRemoteKey(context: Context, httpClient: OkHttpClient) = withContext(Dispatchers.IO) {
        for (url in remoteConfigUrls) {
            try {
                val req = Request.Builder().url(url).build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    // Sincronización remota de clave: Soporta Sobre Cifrado ("active_key_enc") y fallback a texto plano ("active_key")
                    val encKey = json.optString("active_key_enc", "").trim()
                    val plainKey = json.optString("active_key", "").trim()
                    val resolvedKey = if (encKey.isNotEmpty()) {
                        decryptEnvelope(encKey)
                    } else {
                        plainKey
                    }

                    val prefs = context.getSharedPreferences("gareca_tv_prefs", Context.MODE_PRIVATE)
                    if (resolvedKey.isNotEmpty() && resolvedKey != activeKeyB64) {
                        activeKeyB64 = resolvedKey
                        prefs.edit().putString("cached_key", resolvedKey).apply()
                    }

                    // Sincronización remota del servidor DNS sobre HTTPS (DoH)
                    val newDoh = json.optString("doh_url", "").trim()
                    if (newDoh.isNotEmpty() && newDoh != activeDohUrl) {
                        activeDohUrl = newDoh
                        prefs.edit().putString("cached_doh_url", newDoh).apply()
                        com.gareca.tv.network.SecureDns.updateDohServer(newDoh)
                    }

                    remoteConfigUrl = url
                    break
                }
            } catch (_: Exception) {
                // Si la URL falla, continúa a la siguiente
            }
        }
    }

    // Semilla de derivación interna para descifrar el sobre remoto (Anti-Sniffer)
    private const val ENVELOPE_SEED_MASK: Byte = 0x3C
    private val ENVELOPE_SEED_BYTES = byteArrayOf(
        (0x7B).toByte(), (0x5D).toByte(), (0x4E).toByte(), (0x59).toByte(), (0x5F).toByte(), (0x5D).toByte(), (0x68).toByte(), (0x6A).toByte(),
        (0x63).toByte(), (0x79).toByte(), (0x52).toByte(), (0x4A).toByte(), (0x59).toByte(), (0x50).toByte(), (0x53).toByte(), (0x4C).toByte(),
        (0x59).toByte(), (0x63).toByte(), (0x6F).toByte(), (0x59).toByte(), (0x5F).toByte(), (0x4E).toByte(), (0x59).toByte(), (0x48).toByte(),
        (0x63).toByte(), (0x0E).toByte(), (0x0C).toByte(), (0x0E).toByte(), (0x0A).toByte(), (0x63).toByte(), (0x6C).toByte(), (0x4E).toByte(),
        (0x53).toByte(), (0x48).toByte(), (0x59).toByte(), (0x5F).toByte(), (0x48).toByte(), (0x55).toByte(), (0x53).toByte(), (0x52).toByte()
    )

    fun decryptEnvelope(envelopeHex: String): String {
        return try {
            val trimmed = envelopeHex.trim()
            if (trimmed.length < 64) return ""

            val rawBytes = ByteArray(trimmed.length / 2) { i ->
                trimmed.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            if (rawBytes.size < 32) return ""

            val iv = Arrays.copyOfRange(rawBytes, 0, 16)
            val cipherText = Arrays.copyOfRange(rawBytes, 16, rawBytes.size)

            val seedBytes = ByteArray(ENVELOPE_SEED_BYTES.size) { i ->
                (ENVELOPE_SEED_BYTES[i].toInt() xor ENVELOPE_SEED_MASK.toInt()).toByte()
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(seedBytes)

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
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
        val factoryKey = getFactoryKey()
        if (activeKeyB64 != factoryKey) {
            val fallbackResult = tryDecryptWithKey(trimmed, factoryKey)
            if (fallbackResult.isNotEmpty()) {
                activeKeyB64 = factoryKey
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
