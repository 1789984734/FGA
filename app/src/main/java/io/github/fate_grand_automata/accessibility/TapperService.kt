package io.github.fate_grand_automata.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.fate_grand_automata.scripts.enums.GameServers
import timber.log.Timber

class TapperService : AccessibilityService() {
    companion object {
        private val mServiceStarted = mutableStateOf(false)
        val serviceStarted: State<Boolean> = mServiceStarted

        var instance: TapperService? = null
            private set(value) {
                field = value
                mServiceStarted.value = value != null
            }

        @Volatile
        private var intentionalDisable = false

        /**
         * User-initiated disable from the main screen. The service must not
         * re-enable itself afterwards.
         */
        fun disableByUser() {
            intentionalDisable = true
            instance?.disableSelf()
        }
    }

    override fun onServiceConnected() {
        Timber.i("Accessibility Service bound to system")

        // We only want events from FGO
        serviceInfo = serviceInfo.apply {
            packageNames = GameServers.packageNames.keys.toTypedArray()
        }

        instance = this
        intentionalDisable = false

        super.onServiceConnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.i("Accessibility Service unbind")
        instance = null

        val healed = reEnableAfterExternalRemoval()

        if (!healed) {
            Toast.makeText(this, "FGA Accessibility stopped", Toast.LENGTH_SHORT).show()
        }

        return super.onUnbind(intent)
    }

    /**
     * BlueStacks' own accessibility service cycles on UI events and rewrites
     * enabled_accessibility_services wholesale, dropping us and unbinding the
     * service. With WRITE_SECURE_SETTINGS (granted via adb by the launcher
     * script) we can put ourselves right back; the system rebinds within a few
     * hundred milliseconds.
     *
     * @return true when we re-enabled ourselves, so no "stopped" toast is shown
     */
    private fun reEnableAfterExternalRemoval(): Boolean {
        if (intentionalDisable) return false

        return try {
            val me = ComponentName(packageName, TapperService::class.java.name).flattenToString()
            val resolver = applicationContext.contentResolver
            val enabled = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            if (enabled.split(':').none { it.equals(me, ignoreCase = true) }) {
                val updated = if (enabled.isBlank()) me else "$enabled:$me"
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updated
                )
                Timber.i("Re-enabled accessibility service after external removal")
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            Timber.d("Cannot self re-enable without WRITE_SECURE_SETTINGS")
            false
        }
    }

    var detectedFgoServer = GameServers.default
        private set

    /**
     * This method is called on any subscribed [AccessibilityEvent] in tapper_service.xml.
     *
     * When the app in the foreground changes, this method will check if the foreground app is one
     * of the FGO APKs.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val foregroundAppName = event.packageName?.toString()
                    ?: return

                GameServers.fromPackageName(foregroundAppName)?.let { server ->
                    Timber.d("Detected FGO: $server")

                    detectedFgoServer = server
                }
            }

            else -> {}
        }
    }

    override fun onInterrupt() {}
}