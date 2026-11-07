package com.gareca.tv.network

import com.gareca.tv.api.CryptoUtils
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SecureDns : Dns {

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Servidores DNS globales seguros de respaldo directo (Cloudflare + Google)
    private val DEFAULT_BOOTSTRAP_HOSTS by lazy {
        listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4")
        )
    }

    private data class DnsCacheEntry(
        val addresses: List<InetAddress>,
        val timestamp: Long
    )

    // Caché en RAM volátil (0 ms Lookup) con TTL de 30 minutos para evitar consultas de red repetitivas
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private val dnsCache = ConcurrentHashMap<String, DnsCacheEntry>()

    @Volatile
    private var dohInstance: Dns = buildDoh(CryptoUtils.activeDohUrl)

    @Volatile
    private var currentDohUrl: String = CryptoUtils.activeDohUrl

    private fun buildDoh(url: String): Dns {
        return try {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(DEFAULT_BOOTSTRAP_HOSTS)
                .includeIPv6(false) // Previene problemas en Android TVs sin enrutamiento IPv6
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
            Dns.SYSTEM
        }
    }

    fun updateDohServer(newUrl: String) {
        if (newUrl.isNotBlank() && newUrl != currentDohUrl) {
            currentDohUrl = newUrl
            dohInstance = buildDoh(newUrl)
            dnsCache.clear()
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = dnsCache[hostname]
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            return cached.addresses
        }

        val resolved = try {
            val addresses = dohInstance.lookup(hostname)
            android.util.Log.i("SecureDns", "🔒 [Cloudflare DoH] '$hostname' resuelto con éxito -> ${addresses.map { it.hostAddress }}")
            addresses
        } catch (e: Exception) {
            android.util.Log.w("SecureDns", "⚠️ [DoH Fallback a Sistema] Falló DoH para '$hostname' (${e.message}), usando DNS del sistema...")
            // Resiliencia total: si el servidor DoH no responde, recurre de inmediato al DNS del sistema
            try {
                Dns.SYSTEM.lookup(hostname)
            } catch (_: Exception) {
                throw e
            }
        }

        if (resolved.isNotEmpty()) {
            dnsCache[hostname] = DnsCacheEntry(resolved, now)
        }
        return resolved
    }
}
