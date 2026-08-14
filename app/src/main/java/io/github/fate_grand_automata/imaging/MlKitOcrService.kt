package io.github.fate_grand_automata.imaging

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.github.lib_automata.OcrService
import io.github.lib_automata.Pattern
import io.github.lib_automata.dagger.ScriptScope
import timber.log.Timber
import javax.inject.Inject


@ScriptScope
class MlKitOcrService @Inject constructor() : OcrService {
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    override fun detectText(pattern: Pattern): String = recognize(pattern)

    override fun detectDigits(pattern: Pattern): String =
        recognize(pattern).filter { it in DIGIT_WHITELIST }

    private fun recognize(pattern: Pattern): String {
        return try {
            (pattern as DroidCvPattern).asBitmap().use { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val result = Tasks.await(recognizer.process(image))
                result.text
            }
        } catch (e: Exception) {
            Timber.e(e, "ML Kit OCR 识别失败")
            ""
        }
    }

    protected fun finalize() {
        recognizer.close()
    }

    private companion object {
        const val DIGIT_WHITELIST = "0123456789/"
    }
}
