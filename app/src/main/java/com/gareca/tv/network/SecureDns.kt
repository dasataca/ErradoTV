package com.gareca.tv.network

import com.gareca.tv.api.CryptoUtils
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
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
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            dohInstance.lookup(hostname)
        } catch (e: Exception) {
            // Resiliencia total: si el servidor DoH no responde, recurre de inmediato al DNS del sistema
            try {
                Dns.SYSTEM.lookup(hostname)
            } catch (_: Exception) {
                throw e
            }
        }
    }
}
