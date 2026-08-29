package io.github.fate_grand_automata.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract

class StartMediaProjection : ActivityResultContract<Unit, Intent?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val mediaProjectionManager = context.getSystemService(MediaProjectionManager::class.java)

        // FGA only ever captures the default display. Asking for the default-display-only
        // config skips the single-app/multi-app picker sheet added in Android 14.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return mediaProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        }

        return mediaProjectionManager.createScreenCaptureIntent()
    }

    override fun parseResult(resultCode: Int, intent: Intent?) =
        if (resultCode != Activity.RESULT_OK)
            null
        else intent
}
