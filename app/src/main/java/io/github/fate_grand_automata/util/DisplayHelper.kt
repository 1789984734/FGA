package io.github.fate_grand_automata.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayHelper @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    // ApplicationContext has no associated display, so Context.display throws on API 30+.
    // FGA captures the default display; query it explicitly for both metrics and rotation.
    private val display: Display
        get() = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    val metrics: DisplayMetrics
        get() =
        DisplayMetrics().also { display.getRealMetrics(it) }

    val rotation get() = display.rotation
}
