package com.errado.tv

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import com.errado.tv.model.EventStatus
import com.errado.tv.model.StreamOption
import com.errado.tv.telemetry.SupabaseLogger
import com.errado.tv.updater.AppUpdater
import com.errado.tv.updater.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvEvents: RecyclerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvDate: TextView
    private lateinit var btnRefresh: View
    private lateinit var btnUpdate: View
    private lateinit var adapter: EventAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var autoRefreshJob: Job? = null

    private val categoryList = listOf(
        "⚽ Partidos del Día",
        "📺 Regionales",
        "🏆 Deportes",
        "🇦🇷 Argentina",
        "🇪🇸 España",
        "🇲🇽 México",
        "🇨🇴 Colombia",
        "🇵🇪 Perú",
        "🇨🇱 Chile",
        "🇪🇨 Ecuador",
        "🇵🇾 Paraguay",
        "🇺🇾 Uruguay",
        "🇧🇷 Brasil",
        "🇺🇸 USA / MLS",
        "🇬🇧 Premier League",
        "🇮🇹 Serie A",
        "🇩🇪 Bundesliga",
        "🇫🇷 Ligue 1",
        "🇵🇹 Portugal",
        "🌎 Latinoamérica",
        "🏎️ F1 / Motor",
        "🏀 NBA",
        "🥊 Boxeo / UFC",
        "⚾ Béisbol MLB",
        "🌍 Mundo",
        "⚽ Otras Copas",
        "🌐 Todos los Canales"
    )
    private var currentCategory = "⚽ Partidos del Día"
    private var cachedDailyEvents = listOf<EventItem>()
    private var cachedLiveChannels = listOf<EventItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvEvents = findViewById(R.id.rvEvents)
        rvCategories = findViewById(R.id.rvCategories)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvDate = findViewById(R.id.tvDate)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnUpdate = findViewById(R.id.btnUpdate)

        val tvUpdateLabel = findViewById<TextView>(R.id.tvUpdateLabel)
        val ivUpdateIcon = findViewById<ImageView>(R.id.ivUpdateIcon)
        val tvRefresh = findViewById<TextView>(R.id.btnRefresh)
        val currentVersion = AppUpdater.getCurrentVersionName(this)
        tvUpdateLabel.text = "Actualizar (v$currentVersion)"

        btnUpdate.setOnFocusChangeListener { _, hasFocus ->
            tvUpdateLabel.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            ivUpdateIcon.setColorFilter(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }

        tvRefresh.setOnFocusChangeListener { _, hasFocus ->
            tvRefresh.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }

        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryAdapter = CategoryAdapter(categoryList, currentCategory) { selectedCat ->
            currentCategory = selectedCat
            filterAndDisplayEvents(silent = false)
        }
        rvCategories.adapter = categoryAdapter

        adapter = EventAdapter { item ->
            handleEventClick(item)
        }

        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = adapter

        CryptoUtils.init(this)
        StreamCache.init(this)

        SupabaseLogger.info("app_start", "GarecaTV iniciado en Smart TV")

        btnRefresh.setOnClickListener {
            loadData(silent = false, force = true)
        }

        btnUpdate.setOnClickListener {
            checkAndPromptUpdate(userInitiated = true)
        }

        // Carga al abrir la app con punto de control de 15 minutos
        loadData(silent = false, force = false)
    }

    private val SYNC_INTERVAL_MS = 15 * 60 * 1000L // Intervalo de 15 minutos

    override fun onResume() {
        super.onResume()
        // Si algún stream falló o fue marcado como no disponible mientras se reproducía, reflejarlo inmediatamente
        var updated = false
        for (item in cachedDailyEvents) {
            val streamUrl = item.streams.firstOrNull()?.url ?: ""
            if (StreamCache.isUnavailable(streamUrl) && item.status != EventStatus.REPLAY_UNAVAILABLE) {
                item.status = EventStatus.REPLAY_UNAVAILABLE
                updated = true
            }
        }
        for (item in cachedLiveChannels) {
            val streamUrl = item.streams.firstOrNull()?.url ?: ""
            if (StreamCache.isUnavailable(streamUrl) && item.status != EventStatus.REPLAY_UNAVAILABLE) {
                item.status = EventStatus.REPLAY_UNAVAILABLE
                updated = true
            }
        }
        if (updated) {
            cachedDailyEvents = cachedDailyEvents.sortedBy { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
            filterAndDisplayEvents(silent = true)
        }

        val prefs = getSharedPreferences("errado_tv_prefs", Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_time", 0L)
        val now = System.currentTimeMillis()
        // Si regresamos de ver un partido y ya pasaron más de 15 minutos, actualizar en segundo plano
        if (cachedDailyEvents.isNotEmpty() && (now - lastSync > SYNC_INTERVAL_MS)) {
            loadData(silent = true, force = true)
        }
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS) // Refresco automático en segundo plano cada 15 minutos
                loadData(silent = true, force = true)
            }
        }
    }

    private fun loadData(silent: Boolean = false, force: Boolean = false) {
        val prefs = getSharedPreferences("errado_tv_prefs", Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_time", 0L)
        val now = System.currentTimeMillis()

        // Punto de control: si ya cargó hace menos de 15 minutos y no es manual, usar memoria directa (0 esperas)
        if (!force && cachedDailyEvents.isNotEmpty() && (now - lastSync < SYNC_INTERVAL_MS)) {
            filterAndDisplayEvents(silent = true)
            return
        }

        if (!silent) {
            progressBar.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                // Sincroniza la clave remota si está disponible
                CryptoUtils.syncRemoteKey(this@MainActivity, ApiClient.httpClient)

                // Sincronización comunitaria desde Supabase en paralelo con los catálogos de canales
                val communityJob = async(Dispatchers.IO) {
                    CommunityStreamManager.syncFromCommunity(this@MainActivity)
                }
                val dailyJob = async { ApiClient.fetchDailyEvents() }
                val featuredJob = async { ApiClient.fetchFeaturedEvents() }
                val liveChannelsJob = async { ApiClient.fetchTeveplayChannels() }

                communityJob.await()
                val dailyResponse = dailyJob.await()
                val featuredList = featuredJob.await()
                val liveChannels = liveChannelsJob.await()

                tvDate.text = dailyResponse.date

                val allEvents = mutableListOf<EventItem>()
                allEvents.addAll(dailyResponse.events)
                allEvents.addAll(featuredList)

                // Aprendizaje comunitario y local: sólo las señales reportadas por fallo real se muestran como no disponibles
                for (item in allEvents) {
                    val streamUrl = item.streams.firstOrNull()?.url ?: ""
                    if (StreamCache.isUnavailable(streamUrl)) {
                        item.status = EventStatus.REPLAY_UNAVAILABLE
                    }
                }

                // Colocar los partidos 'NO DISPONIBLE' al final de la lista de partidos del día
                val sortedEvents = allEvents.sortedBy { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }

                cachedDailyEvents = sortedEvents
                cachedLiveChannels = liveChannels
                prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()

                if (!silent) {
                    progressBar.visibility = View.GONE
                }

                filterAndDisplayEvents(silent)

                // Verificación en segundo plano sin molestar si no hay actualización
                checkAndPromptUpdate(userInitiated = false)
            } catch (e: Exception) {
                if (!silent) {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Error al cargar eventos: ${e.message}"
                }
            }
        }
    }

    private fun filterAndDisplayEvents(silent: Boolean = false) {
        val displayList = when (currentCategory) {
            "⚽ Partidos del Día" -> cachedDailyEvents
            "🌐 Todos los Canales" -> cachedLiveChannels
            else -> cachedLiveChannels.filter { it.category == currentCategory }
        }

        if (displayList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (currentCategory == "⚽ Partidos del Día") {
                getString(R.string.no_events)
            } else {
                "No hay señales disponibles actualmente para esta categoría."
            }
            adapter.submitList(emptyList())
        } else {
            tvEmpty.visibility = View.GONE
            adapter.submitList(displayList)
            if (!silent && !rvCategories.hasFocus() && !btnRefresh.hasFocus() && !btnUpdate.hasFocus()) {
                rvEvents.requestFocus()
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
                    "GarecaTV ya está actualizado (v${AppUpdater.getCurrentVersionName(this@MainActivity)})",
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

        val currentVer = AppUpdater.getCurrentVersionName(this)
        tvTitle.text = "GarecaTV v${update.versionName} (Instalada: v$currentVer)"
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

        val sortedStreams = item.streams.sortedBy { if (it.type.equals("config", ignoreCase = true)) 0 else 1 }
        val buttonMap = mutableMapOf<StreamOption, Button>()

        for ((index, stream) in sortedStreams.withIndex()) {
            val isConfig = stream.type.equals("config", ignoreCase = true)
            val isDead = StreamCache.isUnavailable(stream.url)
            val cachedRes = StreamCache.getResolution(stream.url)
            val initialText = if (isDead) {
                "⚪ ${stream.title}  [SIN SEÑAL / CAÍDO]"
            } else if (cachedRes != null) {
                "🟢 ${stream.title}  [$cachedRes - VERIFICADO]"
            } else if (isConfig) {
                "🟢 ${stream.title}  [HD DIRECTO - RECOMENDADO]"
            } else {
                "🌐 ${stream.title}  [WEB EXTERNO]"
            }

            val btn = Button(this).apply {
                text = initialText
                textSize = 15f
                isFocusable = true
                isClickable = true
                if (isDead) alpha = 0.5f
                setBackgroundResource(R.drawable.button_focus_selector)

                // Máximo contraste en Smart TV:
                // Al enfocar: Fondo cyan -> Letras NEGRAS en negrita (100% legibles)
                // Sin foco: Fondo oscuro -> Letras BLANCAS
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        setTextColor(android.graphics.Color.BLACK)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        setTextColor(android.graphics.Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                }

                // Estado inicial
                if (index == 0) {
                    setTextColor(android.graphics.Color.BLACK)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(android.graphics.Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.NORMAL)
                }

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
            buttonMap[stream] = btn
            if (index == 0) {
                btn.requestFocus()
            }
        }

        dialog.show()

        // Pretesteo en segundo plano de las opciones (1 a 2 segundos)
        lifecycleScope.launch {
            sortedStreams.map { stream ->
                async {
                    val health = ApiClient.checkStreamHealth(stream)
                    val btn = buttonMap[stream]
                    if (btn != null && dialog.isShowing) {
                        val resLabel = health.resolution?.let { " [$it]" } ?: ""
                        if (health.isOnline) {
                            btn.text = "🟢 ${stream.title}$resLabel  [EN LÍNEA]"
                            btn.alpha = 1.0f
                        } else {
                            btn.text = "⚪ ${stream.title}  [SIN SEÑAL / CAÍDO]"
                            btn.alpha = 0.5f
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun playStream(stream: StreamOption) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resolved = ApiClient.resolveStreamUrl(stream)
                progressBar.visibility = View.GONE

                SupabaseLogger.info(
                    "play_stream",
                    "Abriendo reproducción: ${stream.title}",
                    mapOf("resolved_url" to resolved.url, "type" to resolved.type, "original_type" to stream.type)
                )

                if (resolved.type.equals("exo", ignoreCase = true) || resolved.url.contains(".m3u8") || resolved.url.contains(".mpd")) {
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra("EXTRA_URL", resolved.url)
                        putExtra("EXTRA_TITLE", stream.title)
                        putExtra("EXTRA_DRM_LICENSE", resolved.drmLicenseUrl)
                        putExtra("EXTRA_USER_AGENT", resolved.userAgent)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this@MainActivity, WebStreamActivity::class.java).apply {
                        putExtra("EXTRA_URL", resolved.url)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "No se pudo conectar a esta opción. Pruebe otra alternativa.", Toast.LENGTH_SHORT).show()
                SupabaseLogger.warn("play_stream_error", "Error al abrir stream: ${e.message}", mapOf("title" to stream.title, "url" to stream.url))
            }
        }
    }
}
