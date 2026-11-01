package com.gareca.tv.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {

    @Volatile
    private var cachedIsTv: Boolean? = null

    /**
     * Determina con precisión si el dispositivo de ejecución es Android TV, Google TV o TV Box.
     */
    fun isTv(context: Context): Boolean {
        cachedIsTv?.let { return it }

        val appContext = context.applicationContext ?: context

        // 1. Detección oficial vía UiModeManager (UI_MODE_TYPE_TELEVISION)
        val uiModeManager = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            cachedIsTv = true
            return true
        }

        // 2. Detección por características del PackageManager (Leanback / TV)
        val pm = appContext.packageManager
        val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTvHardware = pm.hasSystemFeature("android.hardware.type.television")

        // 3. Ausencia de pantalla táctil en conjunto con características de TV
        val hasNoTouch = !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        val result = hasLeanback || hasTvHardware || hasNoTouch
        cachedIsTv = result
        return result
    }

    /**
     * Determina si el dispositivo es un celular o tablet (dispositivo móvil con interacción táctil).
     */
    fun isMobile(context: Context): Boolean = !isTv(context)
}
