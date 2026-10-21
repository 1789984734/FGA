package io.github.fate_grand_automata.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.fate_grand_automata.scripts.prefs.IGesturesPreferences
import io.github.lib_automata.GestureService
import io.github.lib_automata.Location
import io.github.lib_automata.Waiter
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.*

/**
 * Class to perform gestures using Android's [AccessibilityService].
 */
class AccessibilityGestures @Inject constructor(
    private val gesturePrefs: IGesturesPreferences,
    private val wait: Waiter
) : GestureService {
    fun Path.moveTo(location: Location) = apply {
        moveTo(location.x.toFloat(), location.y.toFloat())
    }

    fun Path.lineTo(location: Location) = apply {
        lineTo(location.x.toFloat(), location.y.toFloat())
    }

    /**
     * On Android 7, swipe is like a flick.
     * If the swipe distance is too long, FGO won't detect it correctly and have occasional weird behaviour like sudden jumps
     */
    fun swipe7(start: Location, end: Location) {
        val swipePath = Path()
            .moveTo(start)
            .lineTo(end)

        val swipeStroke = GestureDescription.StrokeDescription(
            swipePath,
            0,
            gesturePrefs.swipeDuration.inWholeMilliseconds
        )
        performGesture(swipeStroke)

        wait(gesturePrefs.swipeWaitTime)
    }

    /**
     * Android 8+ swipe is precise due to use of continued gestures.
     *
     * Instead of swiping the whole distance as a single gesture,
     * it is split into multiple small swipes, which is similar to how events are sent if a real human is doing it.
     * There is a finger down delay, followed by multiple small swipe events, followed by a finger lift delay.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun swipe8(start: Location, end: Location) {
        val xDiff = (end.x - start.x).toFloat()
        val yDiff = (end.y - start.y).toFloat()
        val direction = atan2(xDiff, yDiff)
        var distanceLeft = sqrt(xDiff.pow(2) + yDiff.pow(2))

        val swipeDelay = 1L
        val swipeDuration = 1L

        val timesToSwipe = gesturePrefs.swipeDuration.inWholeMilliseconds / (swipeDelay + swipeDuration)
        val thresholdDistance = distanceLeft / timesToSwipe

        var from = start
        val mouseDownPath = Path().moveTo(start)

        var lastStroke = GestureDescription.StrokeDescription(
            mouseDownPath,
            0,
            200L,
            true
        ).also {
            performGesture(it)
        }

        while (distanceLeft > 0) {
            val distanceToScroll = minOf(thresholdDistance, distanceLeft)

            val x = (from.x + distanceToScroll * sin(direction)).roundToInt()
            val y = (from.y + distanceToScroll * cos(direction)).roundToInt()
            val to = Location(x, y)

            val swipePath = Path()
                .moveTo(from)
                .lineTo(to)

            lastStroke = lastStroke.continueStroke(
                swipePath,
                swipeDelay,
                swipeDuration,
                true
            ).also {
                performGesture(it)
            }

            from = to
            distanceLeft -= distanceToScroll
        }

        val mouseUpPath = Path().moveTo(from)

        lastStroke.continueStroke(
            mouseUpPath,
            1,
            400L,
            false
        ).also {
            performGesture(it)
        }
    }

    override fun swipe(start: Location, end: Location) {
        Timber.d("swipe $start, $end")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            swipe8(start, end)
        } else swipe7(start, end)
    }

    override fun click(location: Location, times: Int) {
        val swipePath = Path().moveTo(location)

        Timber.d("click $location x$times")

        val clickDelay = gesturePrefs.clickDelay.inWholeMilliseconds
        val clickDuration = gesturePrefs.clickDuration.inWholeMilliseconds
        val clickStep = (clickDelay + clickDuration).coerceAtLeast(1)

        /*
         * One dispatchGesture call can carry several strokes, so taps are batched instead of
         * waiting for a callback per tap (lottery multi-taps are the hot case). Staggered start
         * times keep the taps sequential, exactly like dispatching them one by one.
         */
        var dispatched = 0
        while (dispatched < times) {
            // Strokes past the per-gesture caps would make GestureDescription.Builder.build() throw
            val batchSize = minOf(
                times - dispatched,
                MAX_STROKES_PER_GESTURE,
                ((MAX_GESTURE_DURATION_MS - clickDelay - clickDuration) / clickStep + 1)
                    .toInt()
                    .coerceAtLeast(1)
            )

            val gestureDesc = GestureDescription.Builder().apply {
                repeat(batchSize) { i ->
                    addStroke(
                        GestureDescription.StrokeDescription(
                            swipePath,
                            clickDelay + i * clickStep,
                            clickDuration
                        )
                    )
                }
            }.build()

            performGesture(gestureDesc)
            dispatched += batchSize
        }

        wait(gesturePrefs.clickWaitTime)
    }

    private fun performGesture(gestureDesc: GestureDescription): Boolean {
        val service = TapperService.instance
        if (service == null) {
            Timber.w("Accessibility service not running, gesture skipped")
            return false
        }

        val timeoutMs = (0 until gestureDesc.strokeCount).maxOfOrNull {
            val stroke = gestureDesc.getStroke(it)
            stroke.startTime + stroke.duration
        }?.plus(2000L)?.coerceAtLeast(3000L) ?: 3000L

        val latch = CountDownLatch(1)
        var result = false

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result = true
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                result = false
                latch.countDown()
            }
        }

        val dispatched = service.dispatchGesture(gestureDesc, callback, null)
        if (!dispatched) {
            Timber.w("dispatchGesture refused the gesture")
            return false
        }

        // 使用 CountDownLatch 进行硬超时等待，绝不依赖协程 EventLoop，超时后内核必定强制唤醒当前线程
        val completed = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Timber.w("Gesture interrupted while waiting")
            false
        }

        if (!completed) {
            Timber.w("Gesture dispatch timed out after ${timeoutMs}ms (forced unlock)")
            return false
        }

        return result
    }

    private fun performGesture(stroke: GestureDescription.StrokeDescription) =
        performGesture(
            GestureDescription.Builder()
                .addStroke(stroke)
                .build()
        )

    override fun close() {}

    companion object {
        // Platform caps: gestures with more strokes or a longer runtime are rejected
        private const val MAX_STROKES_PER_GESTURE = 10
        private const val MAX_GESTURE_DURATION_MS = 60_000L
    }
}
