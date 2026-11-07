package com.gareca.tv.controller

import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gareca.tv.CategoryAdapter
import com.gareca.tv.EventAdapter
import com.gareca.tv.SidebarCategoryAdapter
import com.gareca.tv.model.EventStatus

/**
 * Controlador de navegación D-Pad (Leanback TV) para GarecaTV.
 * Gestiona de forma centralizada las transferencias de foco entre los botones superiores de modo,
 * la barra de búsqueda unificada, las columnas de categorías/países y la lista principal de canales a 60 FPS.
 */
class DpadNavigationController {

    /**
     * Enfoca el primer elemento relevante en la lista de eventos / canales.
     * Si es la sección de Partidos del Día, prioriza automáticamente el primer partido EN VIVO.
     */
    fun focusFirstEventItem(
        rvEvents: RecyclerView,
        adapter: EventAdapter,
        isDailyMatches: Boolean
    ) {
        if (adapter.itemCount == 0 && adapter.currentList.isEmpty()) return
        val lm = rvEvents.layoutManager as? LinearLayoutManager

        val targetPos = if (isDailyMatches) {
            val liveWithStreams = adapter.currentList.indexOfFirst {
                it.status == EventStatus.LIVE && it.streams.isNotEmpty()
            }
            if (liveWithStreams >= 0) liveWithStreams else 0
        } else {
            0
        }

        if (targetPos > 0) {
            lm?.scrollToPositionWithOffset(targetPos, 0)
        } else {
            rvEvents.scrollToPosition(0)
        }

        rvEvents.post {
            val vh = rvEvents.findViewHolderForAdapterPosition(targetPos)
            val view = vh?.itemView ?: lm?.findViewByPosition(targetPos)
            if (view != null) {
                view.requestFocus()
            } else {
                rvEvents.postDelayed({
                    val retryView = rvEvents.findViewHolderForAdapterPosition(targetPos)?.itemView
                        ?: lm?.findViewByPosition(targetPos)
                    retryView?.requestFocus()
                }, 100)
            }
        }
    }

    /**
     * Enfoca el elemento actualmente seleccionado en la lista de países (Columna 2).
     */
    fun focusSelectedCountry(
        rvSidebarCountries: RecyclerView,
        adapter: SidebarCategoryAdapter
    ) {
        val pos = adapter.getSelectedPosition()
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
                    retryView?.requestFocus()
                }, 50)
            }
        }
    }

    /**
     * Enfoca la categoría seleccionada en el menú lateral de TV Mundial (Columna 1).
     */
    fun focusSelectedSidebarCategory(
        rvSidebarCategories: RecyclerView,
        adapter: SidebarCategoryAdapter
    ) {
        val pos = adapter.getSelectedPosition()
        val targetPos = if (pos >= 0) pos else 0
        rvSidebarCategories.scrollToPosition(targetPos)
        rvSidebarCategories.post {
            val vh = rvSidebarCategories.findViewHolderForAdapterPosition(targetPos)
            val view = vh?.itemView ?: rvSidebarCategories.layoutManager?.findViewByPosition(targetPos)
            if (view != null) {
                view.requestFocus()
            } else {
                rvSidebarCategories.postDelayed({
                    val retryView = rvSidebarCategories.findViewHolderForAdapterPosition(targetPos)?.itemView
                        ?: rvSidebarCategories.layoutManager?.findViewByPosition(targetPos)
                    retryView?.requestFocus()
                }, 50)
            }
        }
    }

    /**
     * Enfoca la pestaña seleccionada en la barra horizontal de Deportes.
     */
    fun focusSelectedSportsCategory(
        rvCategories: RecyclerView,
        adapter: CategoryAdapter
    ) {
        val pos = adapter.getSelectedPosition()
        val targetPos = if (pos >= 0) pos else 0
        rvCategories.scrollToPosition(targetPos)
        rvCategories.post {
            val vh = rvCategories.findViewHolderForAdapterPosition(targetPos)
            val view = vh?.itemView ?: rvCategories.layoutManager?.findViewByPosition(targetPos)
            if (view != null) {
                view.requestFocus()
            } else {
                rvCategories.postDelayed({
                    val retryView = rvCategories.findViewHolderForAdapterPosition(targetPos)?.itemView
                        ?: rvCategories.layoutManager?.findViewByPosition(targetPos)
                    retryView?.requestFocus()
                }, 50)
            }
        }
    }

    /**
     * Configura el salto directo D-Pad DOWN desde los botones superiores hacia las pestañas o menú lateral.
     */
    fun setupModeButtonsKeyNavigation(
        btnSports: Button,
        btnWorldTv: Button,
        onFocusSports: () -> Unit,
        onFocusWorldTv: () -> Unit,
        isWorldTvActive: () -> Boolean
    ) {
        btnSports.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                onFocusSports()
                return@setOnKeyListener true
            }
            false
        }

        btnWorldTv.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (isWorldTvActive()) {
                    onFocusWorldTv()
                } else {
                    onFocusSports()
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    /**
     * Configura la navegación de teclas para la caja de búsqueda.
     */
    fun setupSearchKeyListeners(
        etSearch: EditText,
        btnClearSearchText: ImageView?,
        btnVoiceSearch: ImageView?,
        btnSearchReplay: ImageView?,
        rvCategories: RecyclerView,
        rvSuggestions: RecyclerView?,
        hasSuggestions: () -> Boolean,
        onActivateSearchInput: () -> Unit,
        onFocusSuggestions: () -> Unit,
        onFocusFirstEventItem: () -> Unit
    ) {
        etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && !etSearch.isCursorVisible) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (rvSuggestions?.visibility == View.VISIBLE && hasSuggestions()) {
                            onFocusSuggestions()
                        } else {
                            onFocusFirstEventItem()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onActivateSearchInput()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        rvCategories.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        btnVoiceSearch?.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (btnClearSearchText != null && btnClearSearchText.visibility == View.VISIBLE) {
                            btnClearSearchText.requestFocus()
                        } else {
                            btnSearchReplay?.requestFocus()
                        }
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    /**
     * Intercepta la tecla Atrás antes del IME o durante la navegación en sugerencias/búsqueda.
     */
    fun handleSearchBack(
        etSearch: EditText,
        rvSuggestions: RecyclerView?,
        isSuggestionsVisible: Boolean,
        onDeactivateSearch: () -> Unit
    ): Boolean {
        if (isSuggestionsVisible && rvSuggestions != null && rvSuggestions.visibility == View.VISIBLE) {
            rvSuggestions.visibility = View.GONE
            etSearch.requestFocus()
            return true
        }

        if (etSearch.isCursorVisible || etSearch.hasFocus()) {
            onDeactivateSearch()
            return true
        }
        return false
    }
}
