package io.github.fate_grand_automata.util

import android.app.Activity
import android.os.Build
import android.view.Surface
import io.github.fate_grand_automata.prefs.core.GameAreaMode
import io.github.fate_grand_automata.prefs.core.PrefsCore
import io.github.lib_automata.Region
import io.github.lib_automata.Size
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CutoutManager @Inject constructor(
    private val display: DisplayHelper,
    private val prefsCore: PrefsCore,
) {
    private var cutoutFound = false

    fun applyCutout(activity: Activity) {
        if (cutoutFound) {
            return
        }

        // Android P added support for display cutouts
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            cutoutFound = true
            return
        }

        val displayCutout = activity.window.decorView.rootWindowInsets.displayCutout
        if (displayCutout == null) {
            cutoutFound = true
            return
        }

        cutoutFound = true
        Timber.d(
            "Detected display cutout: L=${displayCutout.safeInsetLeft} T=${displayCutout.safeInsetTop} " +
                    "R=${displayCutout.safeInsetRight} B=${displayCutout.safeInsetBottom}"
        )
    }

    private fun getScreenSize(): Size {
        val metrics = display.metrics

        return Size(metrics.widthPixels, metrics.heightPixels)
    }

    fun getCutoutAppliedRegion(): Region {
        val (w, h) = getScreenSize()

        return when (prefsCore.gameAreaMode.get()) {
            GameAreaMode.Default -> Region(0, 0, w, h)
            GameAreaMode.Duo -> Region(192, 0, 2400, h)
            GameAreaMode.Custom -> {
                val l = prefsCore.gameOffsetLeft.get()
                val t = prefsCore.gameOffsetTop.get()
                val r = prefsCore.gameOffsetRight.get()
                val b = prefsCore.gameOffsetBottom.get()

                // if the camera is on the right, use the right offset as x
                val x = if (display.rotation == Surface.ROTATION_270) r else l
                Region(x, t, w - l - r, h - t - b)
            }
        }
    }
}
