package io.github.fate_grand_automata.imaging

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.github.lib_automata.OcrService
import io.github.lib_automata.Pattern
import io.github.lib_automata.dagger.ScriptScope
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt


@ScriptScope
class MlKitOcrService @Inject constructor() : OcrService {
    private val lock = Any()
    private var textRecognizer: TextRecognizer? = null

    private fun recognizer(): TextRecognizer =
        textRecognizer ?: TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        ).also { textRecognizer = it }

    override fun detectText(pattern: Pattern): String = recognize(pattern)

    override fun detectDigits(pattern: Pattern): String =
        recognize(pattern).filter { it in DIGIT_WHITELIST }

    private fun recognize(pattern: Pattern): String {
        return try {
            synchronized(lock) {
                (pattern as DroidCvPattern).asBitmap().use { bmp ->
                    val usable = bmp.ensureMinSizeForMlKit()
                try {
                    val image = InputImage.fromBitmap(usable, 0)
                    val result = Tasks.await(recognizer().process(image))
                    Timber.v("OCR ${usable.width}x${usable.height} -> '${result.text}'")
                    result.text
                } finally {
                        if (usable !== bmp) usable.recycle()
                    }
                }
            }
        } catch (e: Throwable) {
            Timber.e(e, "ML Kit OCR 识别失败")
            ""
        }
    }

    /**
     * ML Kit rejects any input with a side smaller than 32px, which small OCR crops hit after
     * the 1440p→720p downscale (e.g. the skill level region is 75x28). Upscaling also helps
     * recognition of the tiny digits in such crops.
     */
    private fun Bitmap.ensureMinSizeForMlKit(): Bitmap {
        val minSide = minOf(width, height)
        if (minSide >= MIN_OCR_SIDE) return this

        val factor = TARGET_OCR_SIDE.toDouble() / minSide
        return Bitmap.createScaledBitmap(
            this,
            (width * factor).roundToInt().coerceAtLeast(MIN_OCR_SIDE),
            (height * factor).roundToInt().coerceAtLeast(MIN_OCR_SIDE),
            true
        )
    }

    override fun close() {
        synchronized(lock) {
            textRecognizer?.close()
            textRecognizer = null
        }
    }

    private companion object {
        const val DIGIT_WHITELIST = "0123456789/"
        const val MIN_OCR_SIDE = 32
        const val TARGET_OCR_SIDE = 64
    }
}
