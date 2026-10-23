package com.errado.tv.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.errado.tv.api.ApiClient
import com.errado.tv.api.CryptoUtils
import com.errado.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

object AppUpdater {

    @Suppress("DEPRECATION")
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val gistUrl = CryptoUtils.remoteConfigUrl
        if (gistUrl.isEmpty()) return@withContext null

        try {
            val req = Request.Builder().url(gistUrl).build()
            val resp = ApiClient.httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val body = resp.body?.string().orEmpty()
            if (body.isEmpty()) return@withContext null

            val json = JSONObject(body)
            val latestCode = json.optInt("latest_version_code", 0)
            val latestName = json.optString("latest_version_name", "")
            val apkUrl = json.optString("apk_url", "")
            val changelog = json.optString("changelog", "Mejoras y correcciones generales.")

            val currentCode = getCurrentVersionCode(context)

            SupabaseLogger.info(
                "update_check",
                "Comprobación de actualización: actual=$currentCode, remota=$latestCode",
                mapOf("current_code" to currentCode, "latest_code" to latestCode, "latest_name" to latestName)
            )

            if (latestCode > currentCode && apkUrl.isNotEmpty()) {
                return@withContext UpdateInfo(latestCode, latestName, apkUrl, changelog)
            }
        } catch (e: Exception) {
            SupabaseLogger.warn("update_check_error", "Error verificando actualización: ${e.message}")
        }

        null
    }

    private val downloadClient = ApiClient.httpClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndInstall(
        activity: Activity,
        updateInfo: UpdateInfo,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            SupabaseLogger.info(
                "update_download_start",
                "Descargando versión ${updateInfo.versionName} desde ${updateInfo.apkUrl}"
            )

            val req = Request.Builder()
                .url(updateInfo.apkUrl)
                .header("User-Agent", "Mozilla/5.0 (Android TV; ErradoTV-Updater)")
                .build()
            val resp = downloadClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                withContext(Dispatchers.Main) { onError("Error del servidor: código HTTP ${resp.code}") }
                return@withContext
            }

            val body = resp.body ?: run {
                withContext(Dispatchers.Main) { onError("Respuesta vacía del servidor") }
                return@withContext
            }

            val totalBytes = body.contentLength()
            val apkFile = File(activity.cacheDir, "ErradoTV_update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloaded: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((downloaded * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
                installApk(activity, apkFile)
            }
        } catch (e: Exception) {
            SupabaseLogger.error("update_download_error", "Fallo al descargar actualización: ${e.message}")
            withContext(Dispatchers.Main) { onError(e.message ?: "Error desconocido al descargar") }
        }
    }

    private fun installApk(activity: Activity, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            SupabaseLogger.info("update_install_intent", "Lanzando instalador del sistema Android TV")
            activity.startActivity(intent)
        } catch (e: Exception) {
            SupabaseLogger.error("update_install_error", "Error iniciando instalador: ${e.message}")
        }
    }
}
