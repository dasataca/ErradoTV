package com.errado.tv

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import com.errado.tv.api.ApiClient
import com.errado.tv.api.CryptoUtils
import com.errado.tv.model.EventItem
import com.errado.tv.model.StreamOption
import com.errado.tv.telemetry.SupabaseLogger
import com.errado.tv.updater.AppUpdater
import com.errado.tv.updater.UpdateInfo
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvEvents: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvDate: TextView
    private lateinit var btnRefresh: View
    private lateinit var btnUpdate: View
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvEvents = findViewById(R.id.rvEvents)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvDate = findViewById(R.id.tvDate)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnUpdate = findViewById(R.id.btnUpdate)

        adapter = EventAdapter { item ->
            handleEventClick(item)
        }

        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = adapter

        CryptoUtils.init(this)

        SupabaseLogger.info("app_start", "ErradoTV iniciado en Smart TV")

        btnRefresh.setOnClickListener {
            loadData()
        }

        btnUpdate.setOnClickListener {
            checkAndPromptUpdate(userInitiated = true)
        }

        // Carga al abrir la aplicación (comportamiento idéntico a la APK original)
        loadData()
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Sincroniza la clave remota si está disponible
                CryptoUtils.syncRemoteKey(this@MainActivity, ApiClient.httpClient)

                val dailyResponse = ApiClient.fetchDailyEvents()
                val featuredList = ApiClient.fetchFeaturedEvents()

                tvDate.text = dailyResponse.date

                val allEvents = mutableListOf<EventItem>()
                allEvents.addAll(dailyResponse.events)
                allEvents.addAll(featuredList)

                progressBar.visibility = View.GONE

                if (allEvents.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = getString(R.string.no_events)
                } else {
                    adapter.submitList(allEvents)
                    rvEvents.requestFocus()
                }

                // Verificación en segundo plano sin molestar si no hay actualización
                checkAndPromptUpdate(userInitiated = false)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Error al cargar eventos: ${e.message}"
            }
        }
    }

    private fun checkAndPromptUpdate(userInitiated: Boolean) {
        lifecycleScope.launch {
            if (userInitiated) {
                Toast.makeText(this@MainActivity, "Buscando actualizaciones...", Toast.LENGTH_SHORT).show()
            }
            val update = AppUpdater.checkForUpdates(this@MainActivity)
            if (update != null) {
                showUpdateAvailableDialog(update)
            } else if (userInitiated) {
                Toast.makeText(
                    this@MainActivity,
                    "ErradoTV ya está actualizado (v${AppUpdater.getCurrentVersionName(this@MainActivity)})",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showUpdateAvailableDialog(update: UpdateInfo) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_update_confirm)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvVersionTitle)
        val tvChangelog = dialog.findViewById<TextView>(R.id.tvChangelog)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelUpdate)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmUpdate)

        tvTitle.text = "ErradoTV v${update.versionName}"
        tvChangelog.text = update.changelog

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            showDownloadProgressDialog(update)
        }

        dialog.show()
        btnConfirm.requestFocus()
    }

    private fun showDownloadProgressDialog(update: UpdateInfo) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_update_progress)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val pbDownload = dialog.findViewById<ProgressBar>(R.id.pbUpdateDownload)
        val tvPercent = dialog.findViewById<TextView>(R.id.tvUpdatePercent)

        dialog.show()

        lifecycleScope.launch {
            AppUpdater.downloadAndInstall(
                activity = this@MainActivity,
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
                    Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun handleEventClick(item: EventItem) {
        if (item.streams.isEmpty()) return

        SupabaseLogger.info(
            "event_click",
            "Usuario seleccionó: ${item.title}",
            mapOf("title" to item.title, "time" to item.time, "status" to item.status.name, "options" to item.streams.size)
        )

        if (item.streams.size == 1) {
            playStream(item.streams.first())
        } else {
            showStreamPickerDialog(item)
        }
    }

    private fun showStreamPickerDialog(item: EventItem) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_stream_picker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = dialog.findViewById<TextView>(R.id.dialogTitle)
        val container = dialog.findViewById<LinearLayout>(R.id.optionsContainer)

        title.text = item.title

        for ((index, stream) in item.streams.withIndex()) {
            val btn = Button(this).apply {
                text = "${stream.title} (${stream.type.uppercase()})"
                textSize = 16f
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.button_focus_selector)
                setTextColor(resources.getColor(R.color.text_primary, null))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 8, 0, 8)
                layoutParams = params

                setOnClickListener {
                    dialog.dismiss()
                    playStream(stream)
                }
            }
            container.addView(btn)
            if (index == 0) {
                btn.requestFocus()
            }
        }

        dialog.show()
    }

    private fun playStream(stream: StreamOption) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resolved = ApiClient.resolveStreamUrl(stream)
            progressBar.visibility = View.GONE

            SupabaseLogger.info(
                "play_stream",
                "Abriendo reproducción: ${stream.title}",
                mapOf("resolved_url" to resolved.url, "type" to resolved.type, "original_type" to stream.type)
            )

            if (resolved.type.equals("exo", ignoreCase = true) || resolved.url.contains(".m3u8")) {
                val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                    putExtra("EXTRA_URL", resolved.url)
                }
                startActivity(intent)
            } else {
                val intent = Intent(this@MainActivity, WebStreamActivity::class.java).apply {
                    putExtra("EXTRA_URL", resolved.url)
                }
                startActivity(intent)
            }
        }
    }
}
