package io.github.lib_automata

import javax.inject.Inject
import kotlin.time.Duration

class StandardAutomataApi @Inject constructor(
    private val screenshotManager: ScreenshotManager,
    private val highlight: Highlighter,
    private val click: Clicker,
    private val imageMatcher: ImageMatcher,
    private val transform: Transformer,
    private val colorManager: ColorManager,
    private val wait: Waiter,
    private val ocrService: OcrService
) : AutomataApi {

    override fun Region.getPattern(tag: String): Pattern =
        screenshotManager.getScreenshot()
            .crop(transform.toImage(this))
            .also { highlight(this, HighlightColor.Info) }
            .copy() // It is important that the image gets cloned here.
            .apply {
                this.tag = tag
            }

    override fun <T> useSameSnapIn(block: () -> T) =
        screenshotManager.useSameSnapIn(block)

    override fun <T> useColor(block: () -> T): T =
        colorManager.useColor(block)

    override fun Duration.wait(applyMultiplier: Boolean) = wait(this, applyMultiplier)

    override fun Location.click(times: Int) = click(this, times)

    override fun Region.exists(
        image: Pattern,
        timeout: Duration,
        similarity: Double?
    ) = imageMatcher.exists(this, image, timeout, similarity)

    override fun Region.waitVanish(
        image: Pattern,
        timeout: Duration,
        similarity: Double?
    ) = imageMatcher.waitVanish(this, image, timeout, similarity)

    override fun Region.findAll(
        pattern: Pattern,
        similarity: Double?
    ) = imageMatcher.findAll(this, pattern, similarity)

    override fun Region.isWhite() = imageMatcher.isWhite(this)

    override fun Region.isBlack() = imageMatcher.isBlack(this)

    override fun Region.detectText(outlinedText: Boolean): String {
        screenshotManager.getScreenshot()
            .crop(transform.toImage(this))
            .threshold(0.5)
            .let {
                if (outlinedText) {
                    it.use {
                        it.fillText()
                    }
                } else it
            }
            .also { highlight(this, HighlightColor.Info) }
            .use {
                return ocrService.detectText(it)
        }
    }

    override fun Region.detectDigits(): String {
        screenshotManager.getScreenshot()
            .crop(transform.toImage(this))
            .threshold(0.5)
            .also { highlight(this, HighlightColor.Info) }
            .use {
                return ocrService.detectDigits(it)
            }
    }

    override fun Region.findNumberInText(
        replacements: Map<Char, Char>,
        outlinedText: Boolean
    ) = findLongInText(replacements, outlinedText)?.toIntExactOrNull()

    override fun Region.findLongInText(
        replacements: Map<Char, Char>,
        outlinedText: Boolean
    ): Long? {
        val normalized = (if (outlinedText) detectText(true) else detectDigits())
            .map { replacements[it] ?: it }
            .joinToString("")

        return NUMBER_REGEX.find(normalized)
            ?.value
            ?.filter(Char::isDigit)
            ?.toLongOrNull()
    }

    override fun Map<Pattern, Region>.exists(
        timeout: Duration, similarity: Double?, requireAll: Boolean,
    ) = imageMatcher.exists(
        items = this,
        timeout = timeout,
        similarity = similarity,
        requireAll = requireAll
    )

    private companion object {
        val NUMBER_REGEX = Regex("""\d[\d,.'’]*""")

        fun Long.toIntExactOrNull() =
            takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
    }
}
