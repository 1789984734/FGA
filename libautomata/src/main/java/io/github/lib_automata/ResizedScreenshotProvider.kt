package io.github.lib_automata

class ResizedScreenshotProvider(
    private val original: ScreenshotService,
    private val scale: Double,
    platformImpl: PlatformImpl
): ScreenshotService {
    private val resizeTarget = platformImpl.getResizableBlankPattern()

    override fun takeScreenshot(region: Region?): Pattern {
        // Cropping after the resize keeps the output identical to cropping a resized full shot:
        // interpolation near the crop edges would differ if the original were cropped first.
        val shot = original.takeScreenshot()
        shot.resize(resizeTarget, shot.size * scale)

        return if (region != null) resizeTarget.crop(region) else resizeTarget
    }

    override fun startRecording() = original.startRecording()

    override fun close() {
        original.close()
        resizeTarget.close()
    }
}