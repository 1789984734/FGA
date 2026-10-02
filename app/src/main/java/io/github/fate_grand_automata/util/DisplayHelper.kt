package io.github.fate_grand_automata.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val windowManager: WindowManager
) {
    // WindowManager.defaultDisplay is deprecated since API 30 in favour of Context.display
    private val display: Display
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else defaultDisplay

    @Suppress("DEPRECATION")
    private val defaultDisplay: Display
        get() = windowManager.defaultDisplay

    val metrics: DisplayMetrics
        get() =
        DisplayMetrics().also { display.getRealMetrics(it) }

    val rotation get() = display.rotation
}
