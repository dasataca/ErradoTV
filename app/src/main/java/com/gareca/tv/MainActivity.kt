package com.gareca.tv

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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import com.gareca.tv.api.ApiClient
import com.gareca.tv.api.CryptoUtils
import com.gareca.tv.model.EventItem
import com.gareca.tv.model.EventStatus
import com.gareca.tv.model.StreamOption
import com.gareca.tv.telemetry.SupabaseLogger
import com.gareca.tv.updater.AppUpdater
import com.gareca.tv.updater.UpdateInfo
import com.gareca.tv.auth.AuthManager
import com.gareca.tv.auth.SessionState
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var rvEvents: RecyclerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTimeZone: TextView
    private lateinit var tvAppVersion: TextView
    private lateinit var tvUpdateLabel: TextView
    private lateinit var tvHeaderButtonHint: TextView
    private lateinit var btnRefresh: View
    private lateinit var btnUpdate: View
    private var pendingUpdate: com.gareca.tv.updater.UpdateInfo? = null
    private lateinit var adapter: EventAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var autoRefreshJob: Job? = null

    // Controles de búsqueda y ordenación para TV (Repeticiones)
    private lateinit var searchContainer: LinearLayout
    private lateinit var etSearch: SearchEditText
    private lateinit var btnVoiceSearchReplay: View
    private lateinit var btnSearchReplay: View
    private lateinit var btnClearSearchText: ImageView
    private lateinit var btnSortDate: Button
    private lateinit var btnSortName: Button

    // Controles de Búsqueda Global (Todo el catálogo)
    private lateinit var globalSearchContainer: LinearLayout
    private lateinit var etGlobalSearch: SearchEditText
    private lateinit var btnVoiceGlobalSearch: View
    private lateinit var btnSearchGlobal: View
    private lateinit var btnClearGlobalText: ImageView
    private lateinit var btnSortNameGlobal: Button
    private var globalSearchQuery = ""
    private var isVoiceForGlobal = false

    private enum class GlobalSortMode {
        DEFAULT,
        NAME_ASC,
        NAME_DESC
    }
    private var currentGlobalSortMode = GlobalSortMode.DEFAULT

    // Sugerencias en pantalla para Leanback TV
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var suggestionAdapter: SuggestionAdapter
    private val allKeywords = linkedSetOf<String>()
    private var isSelectingSuggestion = false

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
                    executeSearch(isGlobal = true)
                } else {
                    etSearch.setText(spokenText)
                    executeSearch(isGlobal = false)
                }
            }
        }
    }

    private enum class ReplaySortMode {
        DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC
    }
    private var currentSortMode = ReplaySortMode.DATE_DESC
    private var searchQuery = ""

    enum class AppMode { SPORTS, WORLD_TV }
    private var currentAppMode = AppMode.SPORTS

    private lateinit var btnModeSports: View
    private lateinit var btnModeWorldTv: View
    private lateinit var tvModeSportsLabel: TextView
    private lateinit var tvModeWorldTvLabel: TextView

    private lateinit var rvSidebarCategories: RecyclerView
    private lateinit var sidebarCategoryAdapter: SidebarCategoryAdapter
    private lateinit var rvSidebarCountries: RecyclerView
    private lateinit var sidebarCountryAdapter: SidebarCategoryAdapter
    private lateinit var tvChannelPositionIndicator: TextView
    private var selectedCountry = ""

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
        "🇮🇹 Italia - Serie A",
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

    // Catálogo Dinámico de Televisión Mundial (CXTv)
    private var cachedCxtvChannels = listOf<EventItem>()
    private var dynamicCxtvCategories = listOf<String>()
    private var dynamicCxtvCountries = listOf<String>()

    // Índices de Acceso Inmediato O(1) estilo Streamer TV Pro / TiviMate
    private val cxtvCategoryMap = mutableMapOf<String, List<EventItem>>()
    private val cxtvCountryMap = mutableMapOf<String, List<EventItem>>()
    private var preSortedAllChannels = listOf<EventItem>()
    private var cachedCategoryNavItems = listOf<NavItem>()
    private var cachedCountryNavItems = listOf<NavItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar si el usuario ha iniciado sesión
        if (!AuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.ivLogo)?.setOnLongClickListener {
            val user = AuthManager.getUserEmail(this)
            AlertDialog.Builder(this)
                .setTitle("Cuenta GarecaTV")
                .setMessage("Usuario conectado:\n$user\n\n¿Deseas cerrar sesión?")
                .setPositiveButton("Cerrar Sesión") { _, _ ->
                    AuthManager.logout(this)
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }

        rvEvents = findViewById(R.id.rvEvents)
        rvCategories = findViewById(R.id.rvCategories)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvDate = findViewById(R.id.tvDate)
        tvTimeZone = findViewById(R.id.tvTimeZone)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnUpdate = findViewById(R.id.btnUpdate)

        searchContainer = findViewById(R.id.searchContainer)
        etSearch = findViewById(R.id.etSearch)
        btnVoiceSearchReplay = findViewById(R.id.btnVoiceSearchReplay)
        btnSearchReplay = findViewById(R.id.btnSearchReplay)
        btnSortDate = findViewById(R.id.btnSortDate)
        btnSortName = findViewById(R.id.btnSortName)

        globalSearchContainer = findViewById(R.id.globalSearchContainer)
        etGlobalSearch = findViewById(R.id.etGlobalSearch)
        btnVoiceGlobalSearch = findViewById(R.id.btnVoiceGlobalSearch)
        btnSearchGlobal = findViewById(R.id.btnSearchGlobal)
        btnSortNameGlobal = findViewById(R.id.btnSortNameGlobal)
        btnClearSearchText = findViewById(R.id.btnClearSearchText)
        btnClearGlobalText = findViewById(R.id.btnClearGlobalText)

        rvSuggestions = findViewById(R.id.rvSuggestions)
        rvSuggestions.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter(
            onItemClick = { selectedText ->
                onSuggestionSelected(selectedText)
            },
            onUpFromFirst = {
                if (currentCategory == "🔍 Buscar Todo" || currentCategory == "🔍 Buscar TV") {
                    etGlobalSearch.requestFocus()
                } else {
                    etSearch.requestFocus()
                }
            }
        )
        rvSuggestions.adapter = suggestionAdapter

        btnModeSports = findViewById(R.id.btnModeSports)
        btnModeWorldTv = findViewById(R.id.btnModeWorldTv)
        tvModeSportsLabel = findViewById(R.id.tvModeSportsLabel)
        tvModeWorldTvLabel = findViewById(R.id.tvModeWorldTvLabel)

        btnModeSports.setOnClickListener {
            if (currentAppMode != AppMode.SPORTS) {
                switchAppMode(AppMode.SPORTS)
            }
        }

        btnModeWorldTv.setOnClickListener {
            if (currentAppMode != AppMode.WORLD_TV) {
                switchAppMode(AppMode.WORLD_TV)
            }
        }
        updateModeButtonStyles()

        updateTimeZoneHeader()
        setupSearchAndSortControls()
        setupGlobalSearchControls()

        tvUpdateLabel = findViewById(R.id.tvUpdateLabel)
        tvHeaderButtonHint = findViewById(R.id.tvHeaderButtonHint)
        val ivUpdateIcon = findViewById<ImageView>(R.id.ivUpdateIcon)
        val tvRefresh = findViewById<TextView>(R.id.btnRefresh)
        val currentVersion = AppUpdater.getCurrentVersionName(this)
        tvAppVersion.text = "v$currentVersion"
        tvUpdateLabel.text = "Actualizado"

        btnUpdate.setOnFocusChangeListener { _, hasFocus ->
            tvUpdateLabel.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            ivUpdateIcon.setColorFilter(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            if (hasFocus) {
                val update = pendingUpdate
                if (update != null) {
                    tvHeaderButtonHint.text = "Nueva versión v${update.versionName} lista para instalar"
                } else {
                    tvHeaderButtonHint.text = "Comprueba o descarga la última versión de la app"
                }
                tvHeaderButtonHint.visibility = View.VISIBLE
            } else if (!tvRefresh.hasFocus()) {
                tvHeaderButtonHint.visibility = View.GONE
            }
        }

        tvRefresh.setOnFocusChangeListener { _, hasFocus ->
            tvRefresh.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            if (hasFocus) {
                tvHeaderButtonHint.text = "Recarga lista de eventos/canales"
                tvHeaderButtonHint.visibility = View.VISIBLE
            } else if (!btnUpdate.hasFocus()) {
                tvHeaderButtonHint.visibility = View.GONE
            }
        }

        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryAdapter = CategoryAdapter(
            initialCategories = categoryList,
            selectedCategory = currentCategory,
            onCategoryClick = { selectedCat ->
                currentCategory = selectedCat
                if (::rvSuggestions.isInitialized) {
                    rvSuggestions.visibility = View.GONE
                }
                filterAndDisplayEvents(silent = false)
            },
            onDownPressed = { category ->
                if ((category == "🔍 Buscar Todo" || category == "🔍 Buscar TV") && globalSearchContainer.visibility == View.VISIBLE) {
                    etGlobalSearch.requestFocus()
                    true
                } else if (category == "🔁 Repeticiones" && searchContainer.visibility == View.VISIBLE) {
                    etSearch.requestFocus()
                    true
                } else {
                    false
                }
            }
        )
        rvCategories.adapter = categoryAdapter

        rvSidebarCategories = findViewById(R.id.rvSidebarCategories)
        rvSidebarCategories.layoutManager = LinearLayoutManager(this)
        sidebarCategoryAdapter = SidebarCategoryAdapter(
            initialItems = emptyList(),
            selectedCategory = currentCategory,
            onCategorySelected = { selectedCat ->
                currentCategory = selectedCat
                if (::rvSuggestions.isInitialized) {
                    rvSuggestions.visibility = View.GONE
                }
                if (selectedCat == "🌍 Países") {
                    rvSidebarCountries.visibility = View.VISIBLE
                    val countryItems = buildCountryNavItems()
                    if (selectedCountry.isEmpty() && countryItems.isNotEmpty()) {
                        selectedCountry = countryItems[0].title
                    }
                    sidebarCountryAdapter.setItems(countryItems, selectedCountry)
                } else {
                    rvSidebarCountries.visibility = View.GONE
                }
                filterAndDisplayEvents(silent = false)
            },
            onRightPressed = {
                if (currentCategory == "🌍 Países" && rvSidebarCountries.visibility == View.VISIBLE) {
                    focusSelectedCountry()
                    true
                } else {
                    focusFirstEventItem()
                    true
                }
            },
            onLeftPressed = null,
            onUpFromFirst = {
                if (globalSearchContainer.visibility == View.VISIBLE) {
                    etGlobalSearch.requestFocus()
                    true
                } else {
                    btnModeWorldTv.requestFocus()
                    true
                }
            }
        )
        rvSidebarCategories.adapter = sidebarCategoryAdapter

        rvSidebarCountries = findViewById(R.id.rvSidebarCountries)
        rvSidebarCountries.layoutManager = LinearLayoutManager(this)
        sidebarCountryAdapter = SidebarCategoryAdapter(
            initialItems = emptyList(),
            selectedCategory = selectedCountry,
            onCategorySelected = { country ->
                selectedCountry = country
                filterAndDisplayEvents(silent = false)
            },
            onRightPressed = {
                focusFirstEventItem()
                true
            },
            onLeftPressed = {
                focusSelectedSidebarCategory()
                true
            },
            onUpFromFirst = {
                if (globalSearchContainer.visibility == View.VISIBLE) {
                    etGlobalSearch.requestFocus()
                    true
                } else {
                    btnModeWorldTv.requestFocus()
                    true
                }
            }
        )
        rvSidebarCountries.adapter = sidebarCountryAdapter

        tvChannelPositionIndicator = findViewById(R.id.tvChannelPositionIndicator)

        adapter = EventAdapter(
            onItemClick = { item ->
                handleEventClick(item)
            },
            onLeftPressed = {
                if (currentAppMode == AppMode.WORLD_TV) {
                    if (currentCategory == "🌍 Países" && rvSidebarCountries.visibility == View.VISIBLE) {
                        focusSelectedCountry()
                        true
                    } else if (rvSidebarCategories.visibility == View.VISIBLE) {
                        focusSelectedSidebarCategory()
                        true
                    } else {
                        false
                    }
                } else if (currentAppMode == AppMode.SPORTS && rvCategories.visibility == View.VISIBLE) {
                    focusSelectedSportsCategory()
                    true
                } else {
                    false
                }
            },
            onItemFocus = { pos, total ->
                updateChannelPositionIndicator(pos, total)
            }
        )

        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.setHasFixedSize(true)
        rvEvents.setItemViewCacheSize(25)
        rvEvents.adapter = adapter

        rvSidebarCategories.setHasFixedSize(true)
        rvSidebarCategories.setItemViewCacheSize(25)
        rvSidebarCountries.setHasFixedSize(true)
        rvSidebarCountries.setItemViewCacheSize(25)

        CryptoUtils.init(this)
        StreamCache.init(this)

        SupabaseLogger.info("app_start", "GarecaTV iniciado en Smart TV")

        btnRefresh.setOnClickListener {
            if (::tvHeaderButtonHint.isInitialized) {
                tvHeaderButtonHint.text = "Recargando lista de eventos/canales..."
                tvHeaderButtonHint.visibility = View.VISIBLE
            }
            loadData(silent = false, force = true)
        }

        btnUpdate.setOnClickListener {
            val update = pendingUpdate
            if (update != null) {
                AppUpdater.showUpdateDialog(this, update)
            } else {
                checkAndPromptUpdate(userInitiated = true)
            }
        }

        // Comprobación de versión en la web al iniciar
        checkAndPromptUpdate(userInitiated = false)

        // Precargar catálogo CXTv de activos locales para disponibilidad inmediata al presionar Modo TV Mundial
        lifecycleScope.launch(Dispatchers.IO) {
            val localCatalog = ApiClient.fetchCxtvChannels(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (cachedCxtvChannels.isEmpty()) {
                    cachedCxtvChannels = localCatalog.channels
                    dynamicCxtvCategories = localCatalog.categories
                    dynamicCxtvCountries = localCatalog.countries
                    indexCxtvCatalog()
                }
            }
        }

        // Carga al abrir la app con punto de control de 15 minutos
        loadData(silent = false, force = false)
    }

    private fun executeSearch(isGlobal: Boolean) {
        if (::rvSuggestions.isInitialized) {
            rvSuggestions.visibility = View.GONE
            suggestionAdapter.submitList(emptyList())
        }
        if (isGlobal) {
            deactivateGlobalSearchInput()
            globalSearchQuery = etGlobalSearch.text?.toString()?.trim().orEmpty()
            filterAndDisplayEvents(silent = false)
            rvEvents.scrollToPosition(0)
            rvEvents.post { focusFirstEventItem() }
        } else {
            deactivateSearchInput()
            searchQuery = etSearch.text?.toString()?.trim().orEmpty()
            filterAndDisplayEvents(silent = false)
            rvEvents.scrollToPosition(0)
            rvEvents.post { focusFirstEventItem() }
        }
    }

    private fun filterSuggestions(query: String) {
        val q = query.trim().lowercase()
        if (q.length < 2) {
            if (::rvSuggestions.isInitialized) {
                rvSuggestions.visibility = View.GONE
                suggestionAdapter.submitList(emptyList())
            }
            return
        }

        val tokens = q.split(Regex("""\s+""")).filter { it.isNotBlank() }

        val matches = allKeywords.filter { keyword ->
            val lower = keyword.lowercase()
            tokens.all { lower.contains(it) }
        }.sortedBy { item ->
            val lower = item.lowercase()
            if (lower.startsWith(q)) 0 else 1
        }.take(6)

        if (matches.isNotEmpty() && ::rvSuggestions.isInitialized) {
            suggestionAdapter.submitList(matches)
            rvSuggestions.visibility = View.VISIBLE
        } else if (::rvSuggestions.isInitialized) {
            rvSuggestions.visibility = View.GONE
            suggestionAdapter.submitList(emptyList())
        }
    }

    private fun onSuggestionSelected(selectedText: String) {
        isSelectingSuggestion = true
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (currentCategory == "🔍 Buscar Todo") {
                imm.hideSoftInputFromWindow(etGlobalSearch.windowToken, 0)
                etGlobalSearch.isCursorVisible = false
                etGlobalSearch.clearFocus()
                etGlobalSearch.setText(selectedText)
                etGlobalSearch.setSelection(selectedText.length)
                globalSearchQuery = selectedText.trim()
                if (::btnClearGlobalText.isInitialized) {
                    btnClearGlobalText.visibility = View.VISIBLE
                }
            } else {
                imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
                etSearch.isCursorVisible = false
                etSearch.clearFocus()
                etSearch.setText(selectedText)
                etSearch.setSelection(selectedText.length)
                searchQuery = selectedText.trim()
                if (::btnClearSearchText.isInitialized) {
                    btnClearSearchText.visibility = View.VISIBLE
                }
            }
        } finally {
            isSelectingSuggestion = false
        }

        if (::rvSuggestions.isInitialized) {
            rvSuggestions.visibility = View.GONE
            suggestionAdapter.submitList(emptyList())
        }

        filterAndDisplayEvents(silent = false)
        rvEvents.scrollToPosition(0)
        rvEvents.post {
            focusFirstEventItem()
        }
    }

    private fun updateSearchSuggestions() {
        allKeywords.clear()

        fun extractWords(title: String) {
            if (title.isBlank()) return
            allKeywords.add(title.trim())
            val parts = title.split(Regex("""(?i)\s+(?:vs\.?|-|–|y|contra)\s+"""))
            for (p in parts) {
                val clean = p.trim()
                if (clean.length >= 3) {
                    allKeywords.add(clean)
                }
            }
        }

        if (currentAppMode == AppMode.WORLD_TV) {
            for (item in cachedCxtvChannels) {
                extractWords(item.title)
            }
        } else {
            for (item in cachedReplayEvents) {
                extractWords(item.title)
            }
            for (item in cachedDailyEvents) {
                extractWords(item.title)
            }
            for (item in cachedLiveChannels) {
                extractWords(item.title)
            }
        }
    }

    private fun updateGlobalSortButtonsUi() {
        if (!::btnSortNameGlobal.isInitialized) return
        btnSortNameGlobal.text = when (currentGlobalSortMode) {
            GlobalSortMode.DEFAULT -> "🔤 Nombre"
            GlobalSortMode.NAME_ASC -> "🔤 Nombre (A-Z ↓)"
            GlobalSortMode.NAME_DESC -> "🔤 Nombre (Z-A ↑)"
        }
    }

    private fun focusSuggestionsList(): Boolean {
        if (!::rvSuggestions.isInitialized || rvSuggestions.visibility != View.VISIBLE || suggestionAdapter.itemCount == 0) {
            return false
        }
        rvSuggestions.isFocusable = true
        rvSuggestions.isFocusableInTouchMode = true

        fun tryFocus(): Boolean {
            val child = rvSuggestions.findViewHolderForAdapterPosition(0)?.itemView
                ?: rvSuggestions.layoutManager?.findViewByPosition(0)
                ?: rvSuggestions.getChildAt(0)
            if (child != null) {
                child.isFocusable = true
                child.isFocusableInTouchMode = true
                return child.requestFocus()
            }
            return false
        }

        if (!tryFocus()) {
            rvSuggestions.post {
                if (!tryFocus()) {
                    rvSuggestions.requestFocus()
                }
            }
        }
        return true
    }

    private fun handleSearchBack(isGlobal: Boolean): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentEt = if (isGlobal) etGlobalSearch else etSearch
        imm.hideSoftInputFromWindow(currentEt.windowToken, 0)
        currentEt.isCursorVisible = false

        if (::rvSuggestions.isInitialized && rvSuggestions.visibility == View.VISIBLE && suggestionAdapter.itemCount > 0) {
            currentEt.clearFocus()
            focusSuggestionsList()
            return true
        } else {
            currentEt.clearFocus()
            rvEvents.requestFocus()
            return true
        }
    }

    private fun setupSearchAndSortControls() {
        etSearch.isCursorVisible = false

        etSearch.onBackListener = {
            handleSearchBack(isGlobal = false)
        }

        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                etSearch.isCursorVisible = false
            }
        }

        etSearch.setOnClickListener {
            activateSearchInput()
        }

        etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Solo intervenir si el cursor no está activo (navegación por control remoto)
                if (!etSearch.isCursorVisible) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (::rvSuggestions.isInitialized && rvSuggestions.visibility == View.VISIBLE && suggestionAdapter.itemCount > 0) {
                                focusSuggestionsList()
                            } else {
                                focusFirstEventItem()
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            activateSearchInput()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            rvCategories.requestFocus()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            btnVoiceSearchReplay.requestFocus()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (btnClearSearchText.visibility == View.VISIBLE) {
                                btnClearSearchText.requestFocus()
                            } else {
                                btnSearchReplay.requestFocus()
                            }
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isSelectingSuggestion) return
                searchQuery = s?.toString()?.trim().orEmpty()
                btnClearSearchText.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterSuggestions(searchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Botón "X" interno para limpiar texto e invocar teclado (Repeticiones)
        btnClearSearchText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                btnClearSearchText.setColorFilter(android.graphics.Color.BLACK)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                btnClearSearchText.setColorFilter(android.graphics.Color.WHITE)
            }
        }
        btnClearSearchText.setOnClickListener {
            etSearch.setText("")
            searchQuery = ""
            btnClearSearchText.visibility = View.GONE
            if (::rvSuggestions.isInitialized) {
                rvSuggestions.visibility = View.GONE
            }
            filterAndDisplayEvents(silent = true)
            activateSearchInput()
        }
        btnClearSearchText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (::rvSuggestions.isInitialized && rvSuggestions.visibility == View.VISIBLE && suggestionAdapter.itemCount > 0) {
                            focusSuggestionsList()
                        } else {
                            focusFirstEventItem()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        etSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnSearchReplay.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        rvCategories.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Al pulsar la lupa del teclado virtual (IME_ACTION_SEARCH)
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch(isGlobal = false)
                true
            } else {
                false
            }
        }

        // Botón Micrófono Replay (a la izquierda)
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
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        etSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Botón Lupa Replay (a la derecha de la caja de texto, sin texto)
        val ivSearchIconReplay = findViewById<ImageView>(R.id.ivSearchIconReplay)
        btnSearchReplay.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                ivSearchIconReplay?.setColorFilter(android.graphics.Color.BLACK)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                ivSearchIconReplay?.setColorFilter(android.graphics.Color.WHITE)
            }
        }
        btnSearchReplay.setOnClickListener {
            executeSearch(isGlobal = false)
        }
        btnSearchReplay.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (btnClearSearchText.visibility == View.VISIBLE) {
                            btnClearSearchText.requestFocus()
                        } else {
                            etSearch.requestFocus()
                        }
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
                        btnSearchReplay.requestFocus()
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

        etGlobalSearch.onBackListener = {
            handleSearchBack(isGlobal = true)
        }

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
                if (!etGlobalSearch.isCursorVisible) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (::rvSuggestions.isInitialized && rvSuggestions.visibility == View.VISIBLE && suggestionAdapter.itemCount > 0) {
                                focusSuggestionsList()
                            } else if (currentAppMode == AppMode.WORLD_TV && ::rvSidebarCategories.isInitialized && rvSidebarCategories.visibility == View.VISIBLE) {
                                rvSidebarCategories.requestFocus()
                            } else {
                                focusFirstEventItem()
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            activateGlobalSearchInput()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (currentAppMode == AppMode.WORLD_TV) {
                                btnModeWorldTv.requestFocus()
                            } else {
                                rvCategories.requestFocus()
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            btnVoiceGlobalSearch.requestFocus()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (btnClearGlobalText.visibility == View.VISIBLE) {
                                btnClearGlobalText.requestFocus()
                            } else {
                                btnSearchGlobal.requestFocus()
                            }
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
                if (isSelectingSuggestion) return
                globalSearchQuery = s?.toString()?.trim().orEmpty()
                btnClearGlobalText.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterSuggestions(globalSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Botón "X" interno para limpiar texto e invocar teclado (Buscar Todo)
        btnClearGlobalText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                btnClearGlobalText.setColorFilter(android.graphics.Color.BLACK)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                btnClearGlobalText.setColorFilter(android.graphics.Color.WHITE)
            }
        }
        btnClearGlobalText.setOnClickListener {
            etGlobalSearch.setText("")
            globalSearchQuery = ""
            currentGlobalSortMode = GlobalSortMode.DEFAULT
            updateGlobalSortButtonsUi()
            btnClearGlobalText.visibility = View.GONE
            if (::rvSuggestions.isInitialized) {
                rvSuggestions.visibility = View.GONE
            }
            filterAndDisplayEvents(silent = true)
            activateGlobalSearchInput()
        }
        btnClearGlobalText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (::rvSuggestions.isInitialized && rvSuggestions.visibility == View.VISIBLE && suggestionAdapter.itemCount > 0) {
                            focusSuggestionsList()
                        } else {
                            focusFirstEventItem()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        etGlobalSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnSearchGlobal.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        rvCategories.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Al pulsar la lupa del teclado virtual (IME_ACTION_SEARCH)
        etGlobalSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch(isGlobal = true)
                true
            } else {
                false
            }
        }

        // Botón Micrófono Global (a la izquierda)
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
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        etGlobalSearch.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Botón Lupa Global (a la derecha de la caja de texto, sin texto)
        val ivSearchIconGlobal = findViewById<ImageView>(R.id.ivSearchIconGlobal)
        btnSearchGlobal.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                ivSearchIconGlobal?.setColorFilter(android.graphics.Color.BLACK)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                ivSearchIconGlobal?.setColorFilter(android.graphics.Color.WHITE)
            }
        }
        btnSearchGlobal.setOnClickListener {
            executeSearch(isGlobal = true)
        }
        btnSearchGlobal.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (btnClearGlobalText.visibility == View.VISIBLE) {
                            btnClearGlobalText.requestFocus()
                        } else {
                            etGlobalSearch.requestFocus()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        btnSortNameGlobal.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Botón Ordenamiento por Nombre en Búsqueda Global
        btnSortNameGlobal.setOnFocusChangeListener { _, hasFocus ->
            btnSortNameGlobal.setTextColor(if (hasFocus) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        btnSortNameGlobal.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusFirstEventItem()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        btnSearchGlobal.requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
        btnSortNameGlobal.setOnClickListener {
            currentGlobalSortMode = when (currentGlobalSortMode) {
                GlobalSortMode.DEFAULT -> GlobalSortMode.NAME_ASC
                GlobalSortMode.NAME_ASC -> GlobalSortMode.NAME_DESC
                GlobalSortMode.NAME_DESC -> GlobalSortMode.DEFAULT
            }
            updateGlobalSortButtonsUi()
            filterAndDisplayEvents(silent = true)
            rvEvents.scrollToPosition(0)
        }
        updateGlobalSortButtonsUi()
    }

    private fun activateGlobalSearchInput() {
        etGlobalSearch.isCursorVisible = true
        etGlobalSearch.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etGlobalSearch, InputMethodManager.SHOW_IMPLICIT)
        val text = etGlobalSearch.text?.toString().orEmpty()
        if (text.length >= 2) {
            filterSuggestions(text)
        }
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

    private fun switchAppMode(mode: AppMode) {
        if (currentAppMode == mode) return
        currentAppMode = mode
        updateModeButtonStyles()

        if (mode == AppMode.SPORTS) {
            rvCategories.visibility = View.VISIBLE
            rvSidebarCategories.visibility = View.GONE
            rvSidebarCountries.visibility = View.GONE
            tvChannelPositionIndicator.visibility = View.GONE
            currentCategory = "⚽ Partidos del Día"
            categoryAdapter.setCategories(categoryList, currentCategory)
            rvCategories.scrollToPosition(1)
        } else {
            rvCategories.visibility = View.GONE
            rvSidebarCategories.visibility = View.VISIBLE
            val navItems = buildCategoryNavItems()
            currentCategory = if (navItems.isNotEmpty()) navItems[0].title else "⭐ Todos los Canales"
            sidebarCategoryAdapter.setItems(navItems, currentCategory)
            if (currentCategory == "🌍 Países") {
                rvSidebarCountries.visibility = View.VISIBLE
                val countryItems = buildCountryNavItems()
                if (selectedCountry.isEmpty() && countryItems.isNotEmpty()) {
                    selectedCountry = countryItems[0].title
                }
                sidebarCountryAdapter.setItems(countryItems, selectedCountry)
            } else {
                rvSidebarCountries.visibility = View.GONE
            }
            rvSidebarCategories.scrollToPosition(0)
        }

        updateSearchSuggestions()
        filterAndDisplayEvents(silent = false)
    }

    private fun updateModeButtonStyles() {
        val isSports = currentAppMode == AppMode.SPORTS
        btnModeSports.isSelected = isSports
        btnModeWorldTv.isSelected = !isSports
    }

    private fun indexCxtvCatalog() {
        if (cachedCxtvChannels.isEmpty()) return
        val sortedAll = sortCxtvChannelList(cachedCxtvChannels)
        preSortedAllChannels = sortedAll

        val catMap = mutableMapOf<String, MutableList<EventItem>>()
        val countMap = mutableMapOf<String, MutableList<EventItem>>()

        for (item in sortedAll) {
            val catLabel = getCategoryTabLabel(item.category)
            catMap.getOrPut(catLabel) { mutableListOf() }.add(item)
            catMap.getOrPut(item.category) { mutableListOf() }.add(item)

            val country = item.country
            if (!country.isNullOrBlank()) {
                val countryLabel = getCountryTabLabel(country)
                countMap.getOrPut(countryLabel) { mutableListOf() }.add(item)
                countMap.getOrPut(country) { mutableListOf() }.add(item)
            }
        }

        cxtvCategoryMap.clear()
        cxtvCategoryMap.putAll(catMap)

        cxtvCountryMap.clear()
        cxtvCountryMap.putAll(countMap)

        val catNavs = mutableListOf<NavItem>()
        catNavs.add(NavItem("⭐ Todos los Canales", sortedAll.size))
        for (cat in dynamicCxtvCategories.sortedWith(String.CASE_INSENSITIVE_ORDER)) {
            val label = getCategoryTabLabel(cat)
            val count = catMap[label]?.size ?: 0
            catNavs.add(NavItem(label, count))
        }
        val totalCountryChannels = sortedAll.count { !it.country.isNullOrBlank() }
        catNavs.add(NavItem("🌍 Países", totalCountryChannels))
        cachedCategoryNavItems = catNavs

        val countryNavs = mutableListOf<NavItem>()
        for (country in dynamicCxtvCountries.sortedWith(String.CASE_INSENSITIVE_ORDER)) {
            val label = getCountryTabLabel(country)
            val count = countMap[label]?.size ?: 0
            countryNavs.add(NavItem(label, count))
        }
        cachedCountryNavItems = countryNavs
    }

    private fun buildCategoryNavItems(): List<NavItem> {
        if (cachedCategoryNavItems.isEmpty()) indexCxtvCatalog()
        return cachedCategoryNavItems
    }

    private fun buildCountryNavItems(): List<NavItem> {
        if (cachedCountryNavItems.isEmpty()) indexCxtvCatalog()
        return cachedCountryNavItems
    }

    private fun getCategoryTabLabel(category: String): String {
        val icon = when (category.lowercase()) {
            "noticias" -> "📰"
            "música", "musica" -> "🎵"
            "cine" -> "🎬"
            "deportes" -> "🏆"
            "niños", "infantil" -> "👶"
            "serie", "series" -> "🎞️"
            "cultura" -> "🎨"
            "educación", "educacion" -> "📚"
            "documentales" -> "📽️"
            "evangélico", "evangelico", "católico", "catolico" -> "🕊️"
            "culinario" -> "🍳"
            "coches" -> "🏎️"
            "clima" -> "⛅"
            else -> "📺"
        }
        return "$icon $category"
    }

    private fun getCountryTabLabel(country: String): String {
        val flag = when (country.lowercase()) {
            "argentina" -> "🇦🇷"
            "brasil", "brazil" -> "🇧🇷"
            "estados unidos", "usa" -> "🇺🇸"
            "españa", "spain" -> "🇪🇸"
            "méjico", "mexico" -> "🇲🇽"
            "colombia" -> "🇨🇴"
            "chile" -> "🇨🇱"
            "perú", "peru" -> "🇵🇪"
            "ecuador" -> "🇪🇨"
            "uruguay" -> "🇺🇾"
            "paraguay" -> "🇵🇾"
            "venezuela" -> "🇻🇪"
            "bolivia" -> "🇧🇴"
            "italia", "italy" -> "🇮🇹"
            "francia", "france" -> "🇫🇷"
            "alemania", "germany" -> "🇩🇪"
            "reino unido", "uk" -> "🇬🇧"
            "portugal" -> "🇵🇹"
            else -> "🌍"
        }
        return "$flag $country"
    }

    private fun sortCxtvChannelList(list: List<EventItem>): List<EventItem> {
        return list.sortedWith(
            compareBy<EventItem> { item ->
                when {
                    // Canales no disponibles SIEMPRE al final de la lista
                    item.status == EventStatus.REPLAY_UNAVAILABLE || item.status == EventStatus.FINISHED -> 100
                    else -> 0
                }
            }
            .thenBy { it.category.lowercase().trim() }
            .thenBy { it.country.orEmpty().lowercase().trim() }
            .thenBy { it.title.lowercase().trim() }
        )
    }

    private fun startVoiceSearch(isGlobal: Boolean) {
        val promptText = if (isGlobal) {
            if (currentAppMode == AppMode.WORLD_TV) "Di el canal o país a buscar..." else "Di el nombre del equipo o canal a buscar..."
        } else {
            "Di el partido a buscar en repeticiones..."
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (rvEvents.hasFocus()) {
                if (currentAppMode == AppMode.WORLD_TV) {
                    if (currentCategory == "🌍 Países" && rvSidebarCountries.visibility == View.VISIBLE) {
                        focusSelectedCountry()
                        return true
                    } else if (rvSidebarCategories.visibility == View.VISIBLE) {
                        focusSelectedSidebarCategory()
                        return true
                    }
                } else if (currentAppMode == AppMode.SPORTS && rvCategories.visibility == View.VISIBLE) {
                    focusSelectedSportsCategory()
                    return true
                }
            } else if (::rvSidebarCountries.isInitialized && rvSidebarCountries.hasFocus()) {
                focusSelectedSidebarCategory()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun focusSelectedCountry() {
        val pos = sidebarCountryAdapter.getSelectedPosition()
        val targetPos = if (pos >= 0) pos else 0
        rvSidebarCountries.scrollToPosition(targetPos)
        rvSidebarCountries.post {
            val vh = rvSidebarCountries.findViewHolderForAdapterPosition(targetPos)
            val view = vh?.itemView ?: rvSidebarCountries.layoutManager?.findViewByPosition(targetPos)
            if (view != null) {
                view.requestFocus()
            } else {
                rvSidebarCountries.postDelayed({
                    val retryView = rvSidebarCountries.findViewHolderForAdapterPosition(targetPos)?.itemView
                        ?: rvSidebarCountries.layoutManager?.findViewByPosition(targetPos)
                    retryView?.requestFocus() ?: rvSidebarCountries.requestFocus()
                }, 50)
            }
        }
    }

    private fun updateChannelPositionIndicator(pos: Int, total: Int) {
        if (currentAppMode != AppMode.WORLD_TV || total <= 0) {
            tvChannelPositionIndicator.visibility = View.GONE
            return
        }
        val groupLabel = if (currentCategory == "🌍 Países") {
            selectedCountry.ifEmpty { "Países" }
        } else {
            currentCategory
        }
        tvChannelPositionIndicator.text = "Canal ${pos + 1} de $total  •  $groupLabel"
        tvChannelPositionIndicator.visibility = View.VISIBLE
    }

    private fun focusFirstEventItem() {
        rvEvents.post {
            if (adapter.itemCount == 0) return@post
            rvEvents.scrollToPosition(0)
            fun tryFocus(): Boolean {
                val child = rvEvents.findViewHolderForAdapterPosition(0)?.itemView
                    ?: rvEvents.layoutManager?.findViewByPosition(0)
                    ?: rvEvents.getChildAt(0)
                return child?.requestFocus() ?: false
            }

            if (!tryFocus()) {
                rvEvents.postDelayed({
                    val focused = tryFocus()
                    if (!focused) {
                        rvEvents.postDelayed({
                            tryFocus()
                        }, 50)
                    }
                }, 50)
            }
        }
    }

    private fun focusSelectedSidebarCategory() {
        val pos = sidebarCategoryAdapter.getSelectedPosition()
        if (pos >= 0) {
            rvSidebarCategories.scrollToPosition(pos)
            rvSidebarCategories.post {
                val vh = rvSidebarCategories.findViewHolderForAdapterPosition(pos)
                val view = vh?.itemView ?: rvSidebarCategories.layoutManager?.findViewByPosition(pos)
                if (view != null) {
                    view.requestFocus()
                } else {
                    rvSidebarCategories.postDelayed({
                        val retryView = rvSidebarCategories.findViewHolderForAdapterPosition(pos)?.itemView
                            ?: rvSidebarCategories.layoutManager?.findViewByPosition(pos)
                        retryView?.requestFocus() ?: rvSidebarCategories.requestFocus()
                    }, 50)
                }
            }
        } else {
            rvSidebarCategories.requestFocus()
        }
    }

    private fun focusSelectedSportsCategory() {
        val pos = categoryAdapter.getSelectedPosition()
        if (pos >= 0) {
            rvCategories.scrollToPosition(pos)
            rvCategories.post {
                val vh = rvCategories.findViewHolderForAdapterPosition(pos)
                val view = vh?.itemView ?: rvCategories.layoutManager?.findViewByPosition(pos)
                if (view != null) {
                    view.requestFocus()
                } else {
                    rvCategories.postDelayed({
                        val retryView = rvCategories.findViewHolderForAdapterPosition(pos)?.itemView
                            ?: rvCategories.layoutManager?.findViewByPosition(pos)
                        retryView?.requestFocus() ?: rvCategories.requestFocus()
                    }, 50)
                }
            }
        } else {
            rvCategories.requestFocus()
        }
    }

    private fun activateSearchInput() {
        etSearch.isCursorVisible = true
        etSearch.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        val text = etSearch.text?.toString().orEmpty()
        if (text.length >= 2) {
            filterSuggestions(text)
        }
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
            // Caso 1: Si el foco está DENTRO de la lista de sugerencias y el usuario presiona ATRÁS:
            // "si estoy en lilstado y no quiero escoger nada y queiro que se desaparezca el listado voy para atras"
            val isInsideSuggestions = ::rvSuggestions.isInitialized && rvSuggestions.visibility == View.VISIBLE &&
                    (rvSuggestions.hasFocus() || (rvSuggestions.findFocus() != null))
            if (isInsideSuggestions) {
                rvSuggestions.visibility = View.GONE
                if (currentCategory == "🔍 Buscar Todo") {
                    etGlobalSearch.requestFocus()
                } else {
                    etSearch.requestFocus()
                }
                return true
            }

            // Caso 2: El usuario está editando en la caja de texto (teclado virtual en pantalla o cursor activo):
            // "cuando sale la sugernaica enlistado debe el aplicativo permitirme retroceder con el cntroll pero permanecer la lista y tener el foco con el fin que con el teclado poder elegir una opcion del listado"
            val isEditingSearch = ::etSearch.isInitialized && (etSearch.isCursorVisible || etSearch.hasFocus())
            val isEditingGlobal = ::etGlobalSearch.isInitialized && (etGlobalSearch.isCursorVisible || etGlobalSearch.hasFocus())

            if (isEditingSearch || isEditingGlobal) {
                return handleSearchBack(isGlobal = isEditingGlobal)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private val SYNC_INTERVAL_MS = 15 * 60 * 1000L // Intervalo de 15 minutos

    override fun onResume() {
        super.onResume()

        if (!AuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            when (AuthManager.verifyActiveSession(this@MainActivity)) {
                SessionState.DISABLED -> {
                    Toast.makeText(this@MainActivity, "⛔ Tu cuenta ha sido deshabilitada por el administrador en la base de datos.", Toast.LENGTH_LONG).show()
                    AuthManager.logout(this@MainActivity)
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
                SessionState.DEVICE_CONFLICT -> {
                    Toast.makeText(this@MainActivity, "⚠️ Sesión activa en otro dispositivo.", Toast.LENGTH_LONG).show()
                    AuthManager.logout(this@MainActivity)
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
                else -> {}
            }
        }

        // Sincronizar disponibilidad: si falló se marca NO DISPONIBLE, pero si volvió a estar activa
        // se restaura automáticamente la etiqueta que tenía previamente (originalStatus).
        var updated = false
        fun syncItemAvailability(item: EventItem) {
            val is247 = item.status == EventStatus.CHANNEL_24_7 || item.originalStatus == EventStatus.CHANNEL_24_7 || item.category != "Partidos"
            if (is247) {
                if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                    item.status = if (item.originalStatus != EventStatus.REPLAY_UNAVAILABLE) item.originalStatus else EventStatus.CHANNEL_24_7
                    updated = true
                }
                return
            }

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
        for (item in cachedCxtvChannels) syncItemAvailability(item)

        if (updated) {
            cachedDailyEvents = sortEventList(cachedDailyEvents)
            cachedLiveChannels = sortEventList(cachedLiveChannels)
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
                val cxtvJob = async { ApiClient.fetchCxtvChannels(this@MainActivity) }

                communityJob.await()
                val dailyResponse = dailyJob.await()
                val featuredList = featuredJob.await()
                val liveChannels = liveChannelsJob.await()
                val cxtvResult = cxtvJob.await()

                cachedCxtvChannels = cxtvResult.channels
                dynamicCxtvCategories = cxtvResult.categories
                dynamicCxtvCountries = cxtvResult.countries

                // Enriquecer canales de TevePlay que sean partidos cruzándolos con la agenda deportiva diaria
                enrichTeveplayMatches(liveChannels, dailyResponse.events)

                tvDate.text = dailyResponse.date
                val currentVer = AppUpdater.getCurrentVersionName(this@MainActivity)
                tvAppVersion.text = if (dailyResponse.date.isNotEmpty()) "• v$currentVer" else "v$currentVer"
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
                // NOTA: Para canales 24/7 NO se etiqueta nunca como NO DISPONIBLE.
                fun applyAvailability(item: EventItem) {
                    val is247 = item.status == EventStatus.CHANNEL_24_7 || item.originalStatus == EventStatus.CHANNEL_24_7 || item.category != "Partidos"
                    if (is247) {
                        if (item.status == EventStatus.REPLAY_UNAVAILABLE) {
                            item.status = if (item.originalStatus != EventStatus.REPLAY_UNAVAILABLE) item.originalStatus else EventStatus.CHANNEL_24_7
                        }
                        return
                    }

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
                for (item in cachedCxtvChannels) applyAvailability(item)
                indexCxtvCatalog()

                // Ordenar según la regla estricta:
                // 1. En vivo primero (LIVE y STARTING_SOON)
                // 2. Programados después (SCHEDULED)
                // 3. Canales 24/7 luego (los canales 24/7 con NO DISPONIBLE se quedan en su lugar entre los 24/7)
                // 4. Finalizados SIEMPRE al final de la lista
                cachedDailyEvents = sortEventList(todayMatches)
                cachedReplayEvents = replayMatches
                cachedLiveChannels = sortEventList(liveChannels)

                if (currentAppMode == AppMode.WORLD_TV) {
                    val navItems = buildCategoryNavItems()
                    sidebarCategoryAdapter.setItems(navItems, currentCategory)
                    if (currentCategory == "🌍 Países") {
                        rvSidebarCountries.visibility = View.VISIBLE
                        val countryItems = buildCountryNavItems()
                        sidebarCountryAdapter.setItems(countryItems, selectedCountry)
                    } else {
                        rvSidebarCountries.visibility = View.GONE
                    }
                }

                updateSearchSuggestions()
                prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()

                if (!silent) {
                    progressBar.visibility = View.GONE
                }

                filterAndDisplayEvents(silent)

                if (!silent && ::tvHeaderButtonHint.isInitialized && btnRefresh.hasFocus()) {
                    tvHeaderButtonHint.text = "Lista de eventos/canales actualizada"
                    tvHeaderButtonHint.visibility = View.VISIBLE
                }

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
                    channel.startEpochMillis = matched.startEpochMillis
                    channel.status = matched.status
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
        val tokens = q.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val results = mutableListOf<EventItem>()

        fun matchesTokens(item: EventItem): Boolean {
            val target = "${item.title} ${item.subtitle} ${item.category}".lowercase()
            return tokens.all { target.contains(it) }
        }

        for (item in cachedDailyEvents) {
            if (matchesTokens(item)) {
                results.add(item)
            }
        }
        for (item in cachedLiveChannels) {
            if (matchesTokens(item)) {
                results.add(item)
            }
        }
        for (item in cachedReplayEvents) {
            if (matchesTokens(item)) {
                results.add(item)
            }
        }
        return results
    }

    private fun filterAndDisplayEvents(silent: Boolean = false) {
        if (currentAppMode == AppMode.WORLD_TV) {
            rvCategories.visibility = View.GONE
            rvSidebarCategories.visibility = View.VISIBLE
            searchContainer.visibility = View.GONE
            globalSearchContainer.visibility = View.VISIBLE
            etGlobalSearch.hint = "Buscar canal, país o categoría..."
            if (::etSearch.isInitialized) deactivateSearchInput()

            val hasSearch = globalSearchQuery.isNotEmpty()

            if (cxtvCategoryMap.isEmpty() && cachedCxtvChannels.isNotEmpty()) {
                indexCxtvCatalog()
            }

            val displayList: List<EventItem> = if (hasSearch) {
                rvSidebarCountries.visibility = View.GONE
                val tokens = globalSearchQuery.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
                val filtered = cachedCxtvChannels.filter { item ->
                    val target = "${item.title} ${item.subtitle} ${item.category} ${item.country.orEmpty()}".lowercase()
                    tokens.all { target.contains(it) }
                }
                when (currentGlobalSortMode) {
                    GlobalSortMode.NAME_ASC -> filtered.sortedWith(
                        compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                            .thenBy { it.title.lowercase() }
                    )
                    GlobalSortMode.NAME_DESC -> filtered.sortedWith(
                        compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE) 1 else 0 }
                            .thenByDescending { it.title.lowercase() }
                    )
                    GlobalSortMode.DEFAULT -> sortCxtvChannelList(filtered)
                }
            } else if (currentCategory == "🌍 Países") {
                rvSidebarCountries.visibility = View.VISIBLE
                cxtvCountryMap[selectedCountry] ?: emptyList()
            } else if (currentCategory == "⭐ Todos los Canales") {
                rvSidebarCountries.visibility = View.GONE
                preSortedAllChannels
            } else {
                rvSidebarCountries.visibility = View.GONE
                cxtvCategoryMap[currentCategory] ?: emptyList()
            }

            if (displayList.isEmpty()) {
                tvChannelPositionIndicator.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = if (hasSearch) {
                    "No se encontraron canales para \"$globalSearchQuery\"."
                } else {
                    "No hay señales disponibles para esta sección."
                }
                adapter.submitList(emptyList())
            } else {
                tvEmpty.visibility = View.GONE
                adapter.submitList(displayList)
                updateChannelPositionIndicator(0, displayList.size)
                if (!silent && !rvCategories.hasFocus() && !rvSidebarCategories.hasFocus() && (!::rvSidebarCountries.isInitialized || !rvSidebarCountries.hasFocus())
                    && !btnRefresh.hasFocus() && !btnUpdate.hasFocus()
                    && !btnModeSports.hasFocus() && !btnModeWorldTv.hasFocus()
                    && (!::etGlobalSearch.isInitialized || (!etGlobalSearch.hasFocus() && !btnVoiceGlobalSearch.hasFocus() && !btnSearchGlobal.hasFocus() && !btnSortNameGlobal.hasFocus() && (!::btnClearGlobalText.isInitialized || !btnClearGlobalText.hasFocus())))
                    && (!::rvSuggestions.isInitialized || !rvSuggestions.hasFocus())
                ) {
                    rvEvents.requestFocus()
                }
            }
            return
        }

        if (::tvChannelPositionIndicator.isInitialized) {
            tvChannelPositionIndicator.visibility = View.GONE
        }

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
            "🔍 Buscar Todo" -> {
                val base = getFilteredGlobalEvents()
                when (currentGlobalSortMode) {
                    GlobalSortMode.NAME_ASC -> base.sortedWith(
                        compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE || it.status == EventStatus.FINISHED) 1 else 0 }
                            .thenBy { it.title.lowercase() }
                    )
                    GlobalSortMode.NAME_DESC -> base.sortedWith(
                        compareBy<EventItem> { if (it.status == EventStatus.REPLAY_UNAVAILABLE || it.status == EventStatus.FINISHED) 1 else 0 }
                            .thenByDescending { it.title.lowercase() }
                    )
                    GlobalSortMode.DEFAULT -> sortEventList(base)
                }
            }
            "⚽ Partidos del Día" -> cachedDailyEvents
            "🔁 Repeticiones" -> getFilteredAndSortedReplays()
            "🌐 Todos los Canales" -> sortEventList(cachedLiveChannels)
            else -> sortEventList(cachedLiveChannels.filter { it.category == currentCategory })
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
                && !btnModeSports.hasFocus() && !btnModeWorldTv.hasFocus()
                && (!::etSearch.isInitialized || (!etSearch.hasFocus() && !btnSortDate.hasFocus() && !btnSortName.hasFocus() && !btnVoiceSearchReplay.hasFocus() && !btnSearchReplay.hasFocus() && (!::btnClearSearchText.isInitialized || !btnClearSearchText.hasFocus())))
                && (!::etGlobalSearch.isInitialized || (!etGlobalSearch.hasFocus() && !btnVoiceGlobalSearch.hasFocus() && !btnSearchGlobal.hasFocus() && !btnSortNameGlobal.hasFocus() && (!::btnClearGlobalText.isInitialized || !btnClearGlobalText.hasFocus())))
                && (!::rvSuggestions.isInitialized || !rvSuggestions.hasFocus())
            ) {
                rvEvents.requestFocus()
            }
        }
    }

    private fun getFilteredAndSortedReplays(): List<EventItem> {
        var list = cachedReplayEvents
        if (searchQuery.isNotEmpty()) {
            val tokens = searchQuery.lowercase().trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            list = list.filter { item ->
                val target = "${item.title} ${item.subtitle} ${item.time.orEmpty()} ${item.rawDate.orEmpty()}".lowercase()
                tokens.all { target.contains(it) }
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

    /**
     * Ordenación estricta de eventos y canales según la regla oficial:
     * 1. ● EN VIVO van primero (LIVE y STARTING_SOON)
     * 2. PROGRAMADOS van después (SCHEDULED, orden cronológico por hora de inicio)
     * 3. CANALES 24/7 van luego (los canales 24/7 con NO DISPONIBLE se quedan en su lugar entre los 24/7)
     * 4. Partidos no disponibles que no sean 24/7 ni finalizados
     * 5. FINALIZADOS van SIEMPRE al último de todo
     */
    private fun sortEventList(list: List<EventItem>): List<EventItem> {
        return list.sortedWith(
            compareBy<EventItem> { item ->
                when {
                    // Finalizados SIEMPRE van al final de la lista
                    item.status == EventStatus.FINISHED || item.originalStatus == EventStatus.FINISHED -> 100

                    // En vivo van primero
                    item.status == EventStatus.LIVE -> 10
                    item.status == EventStatus.STARTING_SOON -> 20

                    // Programados van después
                    item.status == EventStatus.SCHEDULED -> 30

                    // Luego los canales 24/7 (nunca se etiquetan como NO DISPONIBLE)
                    item.status == EventStatus.CHANNEL_24_7 || item.originalStatus == EventStatus.CHANNEL_24_7 || item.category != "Partidos" -> 40

                    // Repeticiones (en búsqueda global)
                    item.status == EventStatus.REPLAY -> 50

                    // Partidos no disponibles (que no sean 24/7 ni finalizados)
                    item.status == EventStatus.REPLAY_UNAVAILABLE -> 90

                    else -> 60
                }
            }.thenBy { item ->
                // Dentro de la misma prioridad, ordenar cronológicamente si tienen hora programada
                if (item.startEpochMillis > 0L) item.startEpochMillis else Long.MAX_VALUE
            }
            .thenBy { it.category.lowercase().trim() }
            .thenBy { it.title.lowercase().trim() }
        )
    }

    private fun checkAndPromptUpdate(userInitiated: Boolean) {
        lifecycleScope.launch {
            if (userInitiated) {
                Toast.makeText(this@MainActivity, "Buscando actualizaciones...", Toast.LENGTH_SHORT).show()
            }
            val update = AppUpdater.checkForUpdates(this@MainActivity)
            pendingUpdate = update
            if (update != null) {
                tvUpdateLabel.text = "Actualizar (v${update.versionName})"
                AppUpdater.showUpdateDialog(this@MainActivity, update)
            } else {
                tvUpdateLabel.text = "Actualizado"
                if (userInitiated) {
                    Toast.makeText(
                        this@MainActivity,
                        "GarecaTV ya está actualizado (v${AppUpdater.getCurrentVersionName(this@MainActivity)})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupChannelPlaylistIfApplicable(item: EventItem) {
        if (currentCategory != "🔁 Repeticiones" && currentCategory != "⚽ Partidos del Día") {
            val singleStreamItems = adapter.currentList.filter { it.streams.size == 1 }
            val index = singleStreamItems.indexOfFirst { it.title == item.title && it.streams.first().url == item.streams.first().url }
            if (index >= 0 && singleStreamItems.size > 1) {
                val navItems = singleStreamItems.map { ev ->
                    ChannelItem(
                        title = ev.title,
                        subtitle = ev.subtitle.ifEmpty { ev.category },
                        stream = ev.streams.first(),
                        imageUrl = ev.imageUrl
                    )
                }
                ChannelNavigationManager.setPlaylist(navItems, index)
                return
            }
        }
        ChannelNavigationManager.clear()
    }

    private fun handleEventClick(item: EventItem) {
        if (item.streams.isEmpty()) return

        SupabaseLogger.info(
            "event_click",
            "Usuario seleccionó: ${item.title}",
            mapOf("title" to item.title, "time" to item.time, "status" to item.status.name, "options" to item.streams.size)
        )

        if (item.streams.size == 1) {
            setupChannelPlaylistIfApplicable(item)
            playStream(item.streams.first(), item)
        } else {
            ChannelNavigationManager.clear()
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
                    playStream(stream, item)
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

    private fun playStream(stream: StreamOption, parentItem: EventItem? = null) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resolved = ApiClient.resolveStreamUrl(stream)
                progressBar.visibility = View.GONE

                val displayTitle = parentItem?.title ?: stream.title
                SupabaseLogger.info(
                    "play_stream",
                    "Abriendo reproducción: $displayTitle",
                    mapOf("resolved_url" to resolved.url, "type" to resolved.type, "original_type" to stream.type)
                )

                if (resolved.type.equals("exo", ignoreCase = true) || resolved.url.contains(".m3u8") || resolved.url.contains(".mpd")) {
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra("EXTRA_URL", resolved.url)
                        putExtra("EXTRA_TITLE", displayTitle)
                        putExtra("EXTRA_DRM_LICENSE", resolved.drmLicenseUrl)
                        putExtra("EXTRA_USER_AGENT", resolved.userAgent)
                        putExtra("EXTRA_IMAGE_URL", parentItem?.imageUrl)
                        putExtra("EXTRA_SUBTITLE", parentItem?.subtitle)
                        putExtra("EXTRA_ORIGINAL_URL", stream.url)
                        putExtra("EXTRA_ORIGINAL_TYPE", stream.type)
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
