package com.gareca.tv.updater

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gareca.tv.R
import com.gareca.tv.api.ApiClient
import com.gareca.tv.api.CryptoUtils
import com.gareca.tv.telemetry.SupabaseLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val releaseTimestamp: Long = 0L,
    val releaseDate: String? = null,
    val daysOutdated: Int = 0,
    val isMandatory: Boolean = false
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
        val currentCode = getCurrentVersionCode(context)

        for (url in CryptoUtils.remoteConfigUrls) {
            try {
                val nocacheUrl = if (url.contains("?")) "$url&_t=${System.currentTimeMillis()}" else "$url?_t=${System.currentTimeMillis()}"
                val req = Request.Builder()
                    .url(nocacheUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android TV; GarecaTV-OTA)")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()
                val resp = ApiClient.httpClient.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val body = resp.body?.string().orEmpty()
                if (body.isEmpty()) continue

                val json = JSONObject(body)
                val latestCode = if (json.has("latest_version_code")) {
                    json.optInt("latest_version_code", 0)
                } else {
                    json.optInt("versionCode", 0)
                }

                val latestName = if (json.has("latest_version_name")) {
                    json.optString("latest_version_name", "")
                } else {
                    json.optString("versionName", "")
                }

                val apkUrl = if (json.has("apk_url")) {
                    json.optString("apk_url", "")
                } else {
                    json.optString("apkUrl", "")
                }

                val changelog = json.optString("changelog", "Mejoras y correcciones generales.")
                val releaseTimestamp = json.optLong("release_timestamp", 0L)
                val releaseDateStr = json.optString("release_date", "")

                SupabaseLogger.info(
                    "update_check",
                    "Comprobación de actualización desde $url: actual=$currentCode, remota=$latestCode",
                    mapOf("current_code" to currentCode, "latest_code" to latestCode, "latest_name" to latestName, "url" to url)
                )

                if (latestCode > currentCode && apkUrl.isNotEmpty()) {
                    val prefs = context.getSharedPreferences("gareca_updater_prefs", Context.MODE_PRIVATE)
                    val keyFirstDetected = "first_detected_code_$latestCode"
                    var firstDetected = prefs.getLong(keyFirstDetected, 0L)
                    val now = System.currentTimeMillis()

                    if (firstDetected == 0L) {
                        firstDetected = now
                        prefs.edit().putLong(keyFirstDetected, now).apply()
                    }

                    // Calcular días de desactualización
                    val baseTime = if (releaseTimestamp in 1..now) releaseTimestamp else firstDetected
                    val elapsedMillis = now - baseTime
                    val daysFromMillis = (elapsedMillis / (24 * 60 * 60 * 1000L)).toInt()

                    var calendarDaysDiff = 0
                    if (releaseDateStr.isNotEmpty()) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getDefault()
                            }
                            val relDate = sdf.parse(releaseDateStr)
                            if (relDate != null) {
                                val calNow = Calendar.getInstance()
                                val calRel = Calendar.getInstance().apply { time = relDate }
                                val diff = ((calNow.timeInMillis - calRel.timeInMillis) / (24 * 3600 * 1000L)).toInt()
                                calendarDaysDiff = Math.max(0, diff)
                            }
                        } catch (_: Exception) {}
                    }

                    val daysOutdated = Math.max(daysFromMillis, calendarDaysDiff)

                    // Regla de negocio:
                    // 2 días de desactualización permitidos (días 0 y 1 transcurridos).
                    // Al tercer día (daysOutdated >= 2, es decir 48+ horas transcurridas),
                    // se exige actualización obligatoria para iniciar la app.
                    val isMandatory = daysOutdated >= 2

                    return@withContext UpdateInfo(
                        versionCode = latestCode,
                        versionName = latestName,
                        apkUrl = apkUrl,
                        changelog = changelog,
                        releaseTimestamp = releaseTimestamp,
                        releaseDate = releaseDateStr,
                        daysOutdated = daysOutdated,
                        isMandatory = isMandatory
                    )
                }
                // Si la URL principal respondió válidamente y no hay actualización, terminamos
                if (latestCode > 0) {
                    return@withContext null
                }
            } catch (e: Exception) {
                SupabaseLogger.warn("update_check_error", "Error verificando actualización en $url: ${e.message}")
            }
        }

        null
    }

    fun showUpdateDialog(
        activity: Activity,
        update: UpdateInfo,
        onDismissOptional: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_update_confirm)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvHeader = dialog.findViewById<TextView>(R.id.tvUpdateHeader)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvVersionTitle)
        val tvGrace = dialog.findViewById<TextView>(R.id.tvGracePeriodWarning)
        val tvChangelog = dialog.findViewById<TextView>(R.id.tvChangelog)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelUpdate)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmUpdate)

        val currentVer = getCurrentVersionName(activity)
        tvTitle.text = "GarecaTV v${update.versionName} (Instalada: v$currentVer)"
        tvChangelog.text = update.changelog

        if (update.isMandatory) {
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    activity.finishAffinity()
                    true
                } else false
            }

            tvHeader.text = "⛔ ¡Actualización Obligatoria!"
            tvHeader.setTextColor(Color.parseColor("#EF4444"))

            tvGrace.visibility = View.VISIBLE
            tvGrace.text = "Esta versión ha caducado (más de 2 días desactualizada).\nDebes actualizar GarecaTV para poder iniciar la aplicación."
            tvGrace.setTextColor(Color.parseColor("#EF4444"))

            btnCancel.text = "Salir"
            btnCancel.setOnClickListener {
                dialog.dismiss()
                activity.finishAffinity()
            }
        } else {
            dialog.setCancelable(true)
            val daysRemaining = Math.max(1, 2 - update.daysOutdated)
            tvGrace.visibility = View.VISIBLE
            tvGrace.text = "⏳ Tienes $daysRemaining día(s) de gracia antes de que sea obligatoria."
            tvGrace.setTextColor(Color.parseColor("#F59E0B"))

            btnCancel.text = "Más tarde"
            btnCancel.setOnClickListener {
                dialog.dismiss()
                onDismissOptional?.invoke()
            }
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            showDownloadProgressDialog(activity, update)
        }

        dialog.show()
        btnConfirm.requestFocus()
    }

    fun showDownloadProgressDialog(activity: Activity, update: UpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_update_progress)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val pbDownload = dialog.findViewById<ProgressBar>(R.id.pbUpdateDownload)
        val tvPercent = dialog.findViewById<TextView>(R.id.tvUpdatePercent)

        dialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            downloadAndInstall(
                activity = activity,
                updateInfo = update,
                onProgress = { percent ->
                    pbDownload.progress = percent
                    tvPercent.text = "$percent%"
                    if (percent >= 100) {
                        dialog.dismiss()
                    }
                },
                onError = { error ->
                    dialog.dismiss()
                    Toast.makeText(activity, "Error al descargar: $error", Toast.LENGTH_LONG).show()
                    if (update.isMandatory) {
                        showUpdateDialog(activity, update)
                    }
                }
            )
        }
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

            val downloadUrl = if (updateInfo.apkUrl.contains("?")) {
                "${updateInfo.apkUrl}&_t=${System.currentTimeMillis()}"
            } else {
                "${updateInfo.apkUrl}?_t=${System.currentTimeMillis()}"
            }

            val req = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Android TV; GarecaTV-Updater)")
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
            val apkFile = File(activity.cacheDir, "GarecaTV_update.apk")
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
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, activity.packageName)
                putExtra(Intent.EXTRA_TITLE, "GarecaTV")
            }

            SupabaseLogger.info("update_install_intent", "Lanzando instalador del sistema Android TV")
            activity.startActivity(intent)
        } catch (e: Exception) {
            SupabaseLogger.error("update_install_error", "Error iniciando instalador: ${e.message}")
        }
    }
}
