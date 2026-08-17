package io.github.fate_grand_automata.scripts.entrypoints

import io.github.fate_grand_automata.scripts.IFgoAutomataApi
import io.github.fate_grand_automata.scripts.Images
import io.github.lib_automata.EntryPoint
import io.github.lib_automata.ExitManager
import io.github.lib_automata.Location
import io.github.lib_automata.Match
import io.github.lib_automata.Swiper
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 音乐鉴赏批量自动开放脚本
 */
@ScriptScope
class AutoSoundPlayer @Inject constructor(
    exitManager: ExitManager,
    api: IFgoAutomataApi,
    private val swipe: Swiper
) : EntryPoint(exitManager), IFgoAutomataApi by api {

    sealed class ExitReason {
        data class Finished(val unlockedCount: Int) : ExitReason()
    }

    class ExitException(val reason: ExitReason) : Exception()

    companion object {
        const val MAX_NULL_STREAK = 3
    }

    override fun script(): Nothing {
        var unlockedCount = 0
        var nullStreak = 0
        val handledYPositionsInCurrentScreen = mutableListOf<Int>()

        while (true) {
            var foundCandidateInThisScreen = false

            // 使用 findAll 动态扫描当前屏幕列表中所有未开放的【所需】标记
            val candidateMatch: Match? = useSameSnapIn {
                val matches = locations.soundPlayer.listCheckRegion
                    .findAll(images[Images.SoundRequire], similarity = 0.55)
                    .sortedBy { it.region.y }
                    .toList()

                matches.firstOrNull { match ->
                    val matchY = match.region.center.y
                    // 如果该 Y 坐标已经处理过（跳过或已解锁），则不再处理
                    handledYPositionsInCurrentScreen.none { abs(it - matchY) < 60 }
                }
            }

            if (candidateMatch != null) {
                foundCandidateInThisScreen = true
                nullStreak = 0
                val targetY = candidateMatch.region.center.y

                // 计算该未开放条目的点击位置（位于【所需】图标左侧 600 像素处，点击卡片主体）
                val clickLoc = Location(candidateMatch.region.center.x - 600, targetY)
                clickLoc.click()
                1.seconds.wait()

                // 判断是否成功进入确认开放页面并能实行
                val canExecute = isExecuteAvailable()

                if (canExecute) {
                    // 点击【实行】按钮
                    locations.soundPlayer.executeClick.click()
                    1.seconds.wait()

                    // 等待并关闭“已开放”提示弹窗
                    val closeDialogSuccess = waitUntilCloseButtonVisible()
                    if (closeDialogSuccess) {
                        locations.soundPlayer.unlockedCloseClick.click()
                        unlockedCount++

                        // 1. 等待关闭弹窗彻底消失
                        waitUntilCloseButtonDisappears()
                        // 2. 将当前处理的 Y 坐标记录，防止重复点击
                        handledYPositionsInCurrentScreen.add(targetY)
                    }
                } else {
                    // 素材不足或无法实行，点击【取消】返回并记录跳过
                    locations.soundPlayer.cancelClick.click()
                    handledYPositionsInCurrentScreen.add(targetY)
                    500.milliseconds.wait()
                }
            }

            // 当前屏幕中没有可操作的未开放条目
            if (!foundCandidateInThisScreen) {
                nullStreak++
                if (nullStreak >= MAX_NULL_STREAK) {
                    throw ExitException(ExitReason.Finished(unlockedCount))
                }

                // 向上滑动列表以展示下方更多曲目
                swipe(
                    locations.soundPlayer.swipeStart,
                    locations.soundPlayer.swipeEnd
                )
                1.5.seconds.wait()
                handledYPositionsInCurrentScreen.clear()
            }
        }
    }

    /**
     * 判断确认弹窗中【实行】按钮是否可用（素材充足）
     */
    private fun isExecuteAvailable(): Boolean {
        val hasExecuteImage = images[Images.SoundExecute] in locations.soundPlayer.executeRegion ||
                locations.soundPlayer.executeRegion.exists(images[Images.SoundExecute], similarity = 0.65) ||
                images[Images.Execute] in locations.soundPlayer.executeRegion

        if (hasExecuteImage) {
            return true
        }

        return try {
            val text = locations.soundPlayer.executeRegion.detectText().lowercase()
            "实行" in text || "执行" in text || "実行" in text || "execute" in text
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 等待开放成功后的【关闭】按钮出现
     */
    private fun waitUntilCloseButtonVisible(): Boolean {
        val checkInterval = 300.milliseconds
        var waited = 0.seconds
        val timeout = 5.seconds

        while (waited < timeout) {
            val hasCloseButton = images[Images.Close] in locations.soundPlayer.unlockedCloseRegion ||
                    locations.soundPlayer.unlockedCloseRegion.exists(images[Images.Close], similarity = 0.65)

            if (hasCloseButton) {
                return true
            }

            try {
                val text = locations.soundPlayer.unlockedCloseRegion.detectText().lowercase()
                if ("关闭" in text || "閉じる" in text || "close" in text) {
                    return true
                }
            } catch (_: Throwable) {
            }

            checkInterval.wait()
            waited += checkInterval
        }

        return false
    }

    /**
     * 等待开放成功弹窗彻底消失
     */
    private fun waitUntilCloseButtonDisappears() {
        val checkInterval = 200.milliseconds
        var waited = 0.seconds
        val timeout = 3.seconds

        while (waited < timeout) {
            val hasClose = images[Images.Close] in locations.soundPlayer.unlockedCloseRegion ||
                    locations.soundPlayer.unlockedCloseRegion.exists(images[Images.Close], similarity = 0.6)
            if (!hasClose) {
                break
            }
            checkInterval.wait()
            waited += checkInterval
        }
        500.milliseconds.wait()
    }
}
