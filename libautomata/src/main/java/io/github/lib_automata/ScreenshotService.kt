package io.github.lib_automata

/**
 * Interface for classes which can take screenshots.
 */
interface ScreenshotService : AutoCloseable {
    /**
     * Takes a screenshot.
     *
     * @param region area of the screen the caller is interested in, in image pixels, or `null`
     * for the whole screen. Implementations may use it to skip processing parts of the screen
     * that would be cropped away anyway.
     * @return an [Pattern] with the image data, covering [region] (or the whole screen)
     */
    fun takeScreenshot(region: Region? = null): Pattern

    /**
     * Starts recording
     *
     * @return [AutoCloseable] which can be closed to stop recording, or null if recording is not supported
     */
    fun startRecording(): AutoCloseable?
}