package com.errado.tv

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var tvTimeZone: TextView
    private lateinit var btnRefresh: View
    private lateinit var btnUpdate: View
    private lateinit var adapter: EventAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var autoRefreshJob: Job? = null

    // Controles de búsqueda y ordenación para TV (Repeticiones)
    private lateinit var searchContainer: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var btnVoiceSearchReplay: View
    private lateinit var btnSortDate: Button
    private lateinit var btnSortName: Button

    // Controles de Búsqueda Global (Todo el catálogo)
    private lateinit var globalSearchContainer: LinearLayout
    private lateinit var etGlobalSearch: EditText
    private lateinit var btnVoiceGlobalSearch: View
    private lateinit var btnClearGlobalSearch: Button
    private var globalSearchQuery = ""
    private var isVoiceForGlobal = false

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText: String? = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                if (isVoiceForGlobal) {
                    etGlobalSearch.setText(spokenText)
                    globalSearchQuery = spokenText.trim()
                    filterAndDisplayEvents(silent = false)
                    focusFirstEventItem()
                } else {
                    etSearch.setText(spokenText)
                    searchQuery = spokenText.trim()
                    filterAndDisplayEvents(silent = false)
                    focusFirstEventItem()
                }
            }
        }
    }

    private enum class ReplaySortMode {
        DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC
    }
    private var currentSortMode = ReplaySortMode.DATE_DESC
    private var searchQuery = ""

    private val categoryList = listOf(
        "🔍 Buscar Todo",
        "⚽ Partidos del Día",
        "🔁 Repeticiones",
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
    private var cachedReplayEvents = listOf<EventItem>()
    private var cachedLiveChannels = listOf<EventItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvEvents = findViewById(R.id.rvEvents)
        rvCategories = findViewById(R.id.rvCategories)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvDate = findViewById(R.id.tvDate)
        tvTimeZone = findViewById(R.id.tvTimeZone)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnUpdate = findViewById(R.id.btnUpdate)

        searchContainer = findViewById(R.id.searchContainer)
        etSearch = findViewById(R.id.etSearch)
        btnVoiceSearchReplay = findViewById(R.id.btnVoiceSearchReplay)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnSortName = findViewById(R.id.btnSortName)

        globalSearchContainer = findViewById(R.id.globalSearchContainer)
        etGlobalSearch = findViewById(R.id.etGlobalSearch)
        btnVoiceGlobalSearch = findViewById(R.id.btnVoiceGlobalSearch)
        btnClearGlobalSearch = findViewById(R.id.btnClearGlobalSearch)

        updateTimeZoneHeader()
        setupSearchAndSortControls()
        setupGlobalSearchControls()

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
        categoryAdapter = CategoryAdapter(
            categories = categoryList,
            selectedCategory = currentCategory,
            onCategoryClick = { selectedCat ->
                currentCategory = selectedCat
                filterAndDisplayEvents(silent = false)
            },
            onDownPressed = { category ->
                if (category == "🔍 Buscar Todo" && globalSearchContainer.visibility == View.VISIBLE) {
                    etGlobalSearch.requestFocus()
                    true
                } else if (category == "🔁 Repeticiones" && searchContainer.visibility == View.VISIBLE) {
                    etSearch.requestFocus()
                    true
                } else {
                    false
                }
            },
            onCategoryFocus = { focusedCat ->
                if (currentCategory != focusedCat) {
                    currentCategory = focusedCat
                    filterAndDisplayEvents(silent = true)
                }
            }
        )
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

    private fun setupSearchAndSortControls() {
        // En Leanback TV, la caja de búsqueda se enfoca al bajar con la cruceta (D-Pad DOWN)
        // pero NO debe desplegar inmediatamente el teclado ni mostrar el cursor.
        // El usuario debe pulsar una vez más (OK / Center / Enter) para activar la edición.
        etSearch.isCursorVisible = false

        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                etSearch.isCursorVisible = false
            }
        }

        // Al pulsar OK / Center / Click sobre la caja de búsqueda, se activa el cursor y teclado
        etSearch.setOnClickListener {
            activateSearchInput()
        }

        etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Si el cursor no está activo, el foco pasa inmediatamente al primer elemento de la lista
                        if (!etSearch.isCursorVisible) {
                            focusFirstEventItem()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (!etSearch.isCursorVisible) {
                            activateSearchInput()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!etSearch.isCursorVisible) {
                            rvCategories.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!etSearch.isCursorVisible) {
                            btnVoiceSearchReplay.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        btnVoiceSearchReplay.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
        btnVoiceSearchReplay.setOnClickListener {
            startVoiceSearch(isGlobal = false)
        }
        btnVoiceSearchReplay.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        etSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnSortDate.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                if (currentCategory == "🔁 Repeticiones") {
                    filterAndDisplayEvents(silent = true)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                deactivateSearchInput()
                focusFirstEventItem()
                true
            } else {
                false
            }
        }

        btnSortDate.setOnFocusChangeListener { _, hasFocus ->
            btnSortDate.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        btnSortName.setOnFocusChangeListener { _, hasFocus ->
            btnSortName.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }

        btnSortDate.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        btnVoiceSearchReplay.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnSortName.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnSortName.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        btnSortDate.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnSortDate.setOnClickListener {
            currentSortMode = if (currentSortMode == ReplaySortMode.DATE_DESC) {
                ReplaySortMode.DATE_ASC
            } else {
                ReplaySortMode.DATE_DESC
            }
            updateSortButtonsUi()
            filterAndDisplayEvents(silent = true)
            rvEvents.scrollToPosition(0)
        }

        btnSortName.setOnClickListener {
            currentSortMode = if (currentSortMode == ReplaySortMode.NAME_ASC) {
                ReplaySortMode.NAME_DESC
            } else {
                ReplaySortMode.NAME_ASC
            }
            updateSortButtonsUi()
            filterAndDisplayEvents(silent = true)
            rvEvents.scrollToPosition(0)
        }

        updateSortButtonsUi()
    }

    private fun setupGlobalSearchControls() {
        etGlobalSearch.isCursorVisible = false

        etGlobalSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                etGlobalSearch.isCursorVisible = false
            }
        }

        etGlobalSearch.setOnClickListener {
            activateGlobalSearchInput()
        }

        etGlobalSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!etGlobalSearch.isCursorVisible) {
                            focusFirstEventItem()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (!etGlobalSearch.isCursorVisible) {
                            activateGlobalSearchInput()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!etGlobalSearch.isCursorVisible) {
                            rvCategories.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!etGlobalSearch.isCursorVisible) {
                            btnVoiceGlobalSearch.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        etGlobalSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                globalSearchQuery = s?.toString()?.trim().orEmpty()
                if (currentCategory == "🔍 Buscar Todo") {
                    filterAndDisplayEvents(silent = true)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etGlobalSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                deactivateGlobalSearchInput()
                focusFirstEventItem()
                true
            } else {
                false
            }
        }

        btnVoiceGlobalSearch.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
        btnVoiceGlobalSearch.setOnClickListener {
            startVoiceSearch(isGlobal = true)
        }
        btnVoiceGlobalSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        etGlobalSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnClearGlobalSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnClearGlobalSearch.setOnFocusChangeListener { _, hasFocus ->
            btnClearGlobalSearch.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        btnClearGlobalSearch.setOnClickListener {
            etGlobalSearch.setText("")
            globalSearchQuery = ""
            filterAndDisplayEvents(silent = true)
            etGlobalSearch.requestFocus()
        }
        btnClearGlobalSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        btnVoiceGlobalSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    private fun activateGlobalSearchInput() {
        etGlobalSearch.isCursorVisible = true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etGlobalSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun deactivateGlobalSearchInput() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etGlobalSearch.windowToken, 0)
        etGlobalSearch.isCursorVisible = false
    }

    private fun updateTimeZoneHeader() {
        try {
            val tz = java.util.TimeZone.getDefault()
            val offsetMillis = tz.getOffset(System.currentTimeMillis())
            val offsetHours = offsetMillis / (1000 * 60 * 60)
            val offsetMins = Math.abs((offsetMillis / (1000 * 60)) % 60)
            val sign = if (offsetHours >= 0) "+" else ""
            val tzText = if (offsetMins == 0) "GMT$sign$offsetHours" else "GMT$sign$offsetHours:$offsetMins"
            tvTimeZone.text = "($tzText)"
        } catch (_: Exception) {
            tvTimeZone.visibility = View.GONE
        }
    }

    private fun startVoiceSearch(isGlobal: Boolean) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (isGlobal) "Di el nombre del equipo o canal a buscar..." else "Di el partido a buscar en repeticiones..."
            )
        }
        try {
            isVoiceForGlobal = isGlobal
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Reconocimiento de voz no disponible en este televisor", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            startVoiceSearch(isGlobal = (currentCategory != "🔁 Repeticiones"))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun focusFirstEventItem() {
        val firstHolder = rvEvents.findViewHolderForAdapterPosition(0)
        if (firstHolder != null) {
            firstHolder.itemView.requestFocus()
        } else {
            val firstView = rvEvents.layoutManager?.findViewByPosition(0)
            firstView?.requestFocus() ?: rvEvents.requestFocus()
        }
    }

    private fun activateSearchInput() {
        etSearch.isCursorVisible = true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun deactivateSearchInput() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.isCursorVisible = false
    }

    private fun updateSortButtonsUi() {
        when (currentSortMode) {
            ReplaySortMode.DATE_DESC -> {
                btnSortDate.text = "📅 Fecha (Recientes ↓)"
                btnSortName.text = "🔤 Nombre"
            }
            ReplaySortMode.DATE_ASC -> {
                btnSortDate.text = "📅 Fecha (Antiguos ↑)"
                btnSortName.text = "🔤 Nombre"
            }
            ReplaySortMode.NAME_ASC -> {
                btnSortDate.text = "📅 Fecha"
                btnSortName.text = "🔤 Nombre (A-Z ↓)"
            }
            ReplaySortMode.NAME_DESC -> {
                btnSortDate.text = "📅 Fecha"
                btnSortName.text = "🔤 Nombre (Z-A ↑)"
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (::etGlobalSearch.isInitialized && (etGlobalSearch.isCursorVisible || etGlobalSearch.hasFocus())) {
                deactivateGlobalSearchInput()
                etGlobalSearch.clearFocus()
                rvEvents.requestFocus()
                return true
            }
            if (::etSearch.isInitialized && (etSearch.isCursorVisible || etSearch.hasFocus())) {
                deactivateSearchInput()
                etSearch.clearFocus()
                rvEvents.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private val SYNC_INTERVAL_MS = 15 * 60 * 1000L // Intervalo de 15 minutos

    override fun onResume() {
        super.onResume()
        // Sincronizar disponibilidad: si falló se marca NO DISPONIBLE, pero si volvió a estar activa
        // se restaura automáticamente la etiqueta que tenía previamente (originalStatus).
        var updated = false
        fun syncItemAvailability(item: EventItem) {
            val allUnavailable = item.streams.isNotEmpty() && item.streams.all { StreamCache.isUnavailable(it.url) }
            if (allUnavailable && item.status != EventStatus.REPLAY_UNAVAILABLE) {
                item.originalStatus = item.status
                item.status = EventStatus.REPLAY_UNAVAILABLE
                updated = true
            } else if (!allUnavailable && item.status == EventStatus.REPLAY_UNAVAILABLE) {
                item.status = item.originalStatus
                updated = true
            }
        }

        for (item in cachedDailyEvents) syncItemAvailability(item)
        for (item in cachedReplayEvents) syncItemAvailability(item)
        for (item in cachedLiveChannels) syncItemAvailability(item)

        if (updated) {
            cachedDailyEvents = cachedDailyEvents.sortedBy { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
            filterAndDisplayEvents(silent = true)
        }

        val prefs = getSharedPreferences("gareca_tv_prefs", Context.MODE_PRIVATE)
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
        val prefs = getSharedPreferences("gareca_tv_prefs", Context.MODE_PRIVATE)
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

                // Enriquecer canales de TevePlay que sean partidos cruzándolos con la agenda deportiva diaria
                enrichTeveplayMatches(liveChannels, dailyResponse.events)

                tvDate.text = dailyResponse.date
                updateTimeZoneHeader()

                // Separación estricta: en Partidos del Día SOLO van partidos que NO sean repetición
                val todayMatches = mutableListOf<EventItem>()
                val replayMatches = mutableListOf<EventItem>()

                for (item in dailyResponse.events) {
                    if (item.status == EventStatus.REPLAY) {
                        replayMatches.add(item)
                    } else {
                        todayMatches.add(item)
                    }
                }

                for (item in featuredList) {
                    if (item.status == EventStatus.REPLAY) {
                        replayMatches.add(item)
                    } else {
                        todayMatches.add(item)
                    }
                }

                // Aprendizaje comunitario y local: sólo las señales reportadas por fallo real se muestran como no disponibles.
                // Si la señal vuelve a estar activa, restaura su etiqueta previa (originalStatus).
                fun applyAvailability(item: EventItem) {
                    val allUnavailable = item.streams.isNotEmpty() && item.streams.all { StreamCache.isUnavailable(it.url) }
                    if (allUnavailable) {
                        if (item.status != EventStatus.REPLAY_UNAVAILABLE) {
                            item.originalStatus = item.status
                            item.status = EventStatus.REPLAY_UNAVAILABLE
                        }
                    } else if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                        item.status = item.originalStatus
                    }
                }
                for (item in todayMatches) applyAvailability(item)
                for (item in replayMatches) applyAvailability(item)
                for (item in liveChannels) applyAvailability(item)

                // Colocar los partidos 'NO DISPONIBLE' al final de la lista de partidos del día
                val sortedTodayMatches = todayMatches.sortedBy { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }

                cachedDailyEvents = sortedTodayMatches
                cachedReplayEvents = replayMatches
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

    private fun enrichTeveplayMatches(liveChannels: List<EventItem>, dailyEvents: List<EventItem>) {
        fun cleanWords(s: String): List<String> {
            return s.lowercase()
                .replace(Regex("""\b(liga|copa|colombia|chile|brasil|sub-20|sub20|campeonato|de|fc|cf|club|conmebol|concacaf|league|cup)\b"""), " ")
                .replace(Regex("""[^a-z0-9\s]"""), " ")
                .split(Regex("""\s+"""))
                .filter { it.length >= 4 }
        }

        for (channel in liveChannels) {
            if ("vs" in channel.title.lowercase() || " v " in channel.title.lowercase()) {
                val chWords = cleanWords(channel.title)
                val matched = dailyEvents.firstOrNull { daily ->
                    val dWords = cleanWords(daily.title)
                    dWords.isNotEmpty() && dWords.any { chWords.contains(it) }
                }

                if (matched != null) {
                    channel.time = matched.time
                    channel.localTime = matched.localTime
                    channel.countdownText = matched.countdownText
                    channel.originalStatus = matched.status
                    if (channel.status != EventStatus.REPLAY_UNAVAILABLE) {
                        channel.status = matched.status
                    }
                    val displayTime = channel.localTime ?: channel.time
                    channel.subtitle = "🟢 TevePlay • ${if (displayTime != null) "Hora local: $displayTime • " else ""}${matched.countdownText ?: "En agenda"}"
                }
            }
        }
    }

    private fun getFilteredGlobalEvents(): List<EventItem> {
        val q = globalSearchQuery.trim().lowercase()
        if (q.isEmpty()) {
            return cachedDailyEvents
        }
        val results = mutableListOf<EventItem>()

        for (item in cachedDailyEvents) {
            if (item.title.lowercase().contains(q) || item.subtitle.lowercase().contains(q)) {
                results.add(item)
            }
        }
        for (item in cachedLiveChannels) {
            if (item.title.lowercase().contains(q) || item.category.lowercase().contains(q) || item.subtitle.lowercase().contains(q)) {
                results.add(item)
            }
        }
        for (item in cachedReplayEvents) {
            if (item.title.lowercase().contains(q) || item.subtitle.lowercase().contains(q)) {
                results.add(item)
            }
        }
        return results
    }

    private fun filterAndDisplayEvents(silent: Boolean = false) {
        when (currentCategory) {
            "🔍 Buscar Todo" -> {
                globalSearchContainer.visibility = View.VISIBLE
                searchContainer.visibility = View.GONE
                if (::etSearch.isInitialized) deactivateSearchInput()
            }
            "🔁 Repeticiones" -> {
                globalSearchContainer.visibility = View.GONE
                searchContainer.visibility = View.VISIBLE
                if (::etGlobalSearch.isInitialized) deactivateGlobalSearchInput()
            }
            else -> {
                globalSearchContainer.visibility = View.GONE
                searchContainer.visibility = View.GONE
                if (::etSearch.isInitialized) deactivateSearchInput()
                if (::etGlobalSearch.isInitialized) deactivateGlobalSearchInput()
            }
        }

        val displayList = when (currentCategory) {
            "🔍 Buscar Todo" -> getFilteredGlobalEvents()
            "⚽ Partidos del Día" -> cachedDailyEvents
            "🔁 Repeticiones" -> getFilteredAndSortedReplays()
            "🌐 Todos los Canales" -> cachedLiveChannels
            else -> cachedLiveChannels.filter { it.category == currentCategory }
        }

        if (displayList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = when (currentCategory) {
                "🔍 Buscar Todo" -> if (globalSearchQuery.isNotEmpty()) {
                    "No se encontraron partidos ni canales para \"$globalSearchQuery\"."
                } else {
                    "Escribe un equipo o pulsa 🎙️ para buscar en partidos de hoy, TevePlay y repeticiones."
                }
                "⚽ Partidos del Día" -> getString(R.string.no_events)
                "🔁 Repeticiones" -> if (searchQuery.isNotEmpty()) {
                    "No se encontraron repeticiones para \"$searchQuery\"."
                } else {
                    "No hay repeticiones disponibles en este momento."
                }
                else -> "No hay señales disponibles actualmente para esta categoría."
            }
            adapter.submitList(emptyList())
        } else {
            tvEmpty.visibility = View.GONE
            adapter.submitList(displayList)
            if (!silent && !rvCategories.hasFocus() && !btnRefresh.hasFocus() && !btnUpdate.hasFocus()
                && (!::etSearch.isInitialized || (!etSearch.hasFocus() && !btnSortDate.hasFocus() && !btnSortName.hasFocus() && !btnVoiceSearchReplay.hasFocus()))
                && (!::etGlobalSearch.isInitialized || (!etGlobalSearch.hasFocus() && !btnVoiceGlobalSearch.hasFocus() && !btnClearGlobalSearch.hasFocus()))
            ) {
                rvEvents.requestFocus()
            }
        }
    }

    private fun getFilteredAndSortedReplays(): List<EventItem> {
        var list = cachedReplayEvents
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase().trim()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                (it.time?.contains(q) == true) ||
                (it.rawDate != null && it.rawDate.contains(q))
            }
        }

        return when (currentSortMode) {
            ReplaySortMode.DATE_DESC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenByDescending { it.timestamp }
                    .thenBy { it.title.lowercase() }
            )
            ReplaySortMode.DATE_ASC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenBy { it.timestamp }
                    .thenBy { it.title.lowercase() }
            )
            ReplaySortMode.NAME_ASC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenBy { it.title.lowercase() }
            )
            ReplaySortMode.NAME_DESC -> list.sortedWith(
                compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                    .thenByDescending { it.title.lowercase() }
            )
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
                            StreamCache.markAvailable(this@MainActivity, stream.url)
                            if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                                item.status = item.originalStatus
                                adapter.notifyDataSetChanged()
                            }
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
