package io.github.fate_grand_automata.scripts.entrypoints

import io.github.fate_grand_automata.scripts.IFgoAutomataApi
import io.github.fate_grand_automata.scripts.Images
import io.github.fate_grand_automata.scripts.enums.GameServer
import io.github.fate_grand_automata.scripts.modules.ConnectionRetry
import io.github.lib_automata.EntryPoint
import io.github.lib_automata.ExitManager
import io.github.lib_automata.Region
import io.github.lib_automata.ScriptAbortException
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@ScriptScope
class AutoSkillUpgrade @Inject constructor(
    private val connectionRetry: ConnectionRetry,
    exitManager: ExitManager,
    api: IFgoAutomataApi
) : EntryPoint(exitManager), IFgoAutomataApi by api {

    sealed class ExitReason {
        data object Done : ExitReason()
        data object RanOutOfQP : ExitReason()
        data object NoServantSelected : ExitReason()
        data object PageRecognitionFailed : ExitReason()
        data class OcrFailed(val skillNumber: Int) : ExitReason()
        data class NoProgress(val skillNumber: Int) : ExitReason()
        data object Abort : ExitReason()
        data class Unexpected(val e: Exception) : ExitReason()
    }

    enum class EnhancementExitReason {
        TargetLevelMet,
        OutOfMaterials,
        OutOfQP,
        ResourceInsufficient,
        SkippedAfterOutOfQP,
        OcrFailed,
        PageRecognitionFailed,
        NoProgress,
    }

    data class Summary(
        val skillNumber: Int,
        val startingLevel: Int?,
        val endLevel: Int?,
        val targetLevel: Int,
        val result: EnhancementExitReason?,
    )

    data class ExitState(val skillSummaryList: List<Summary>)

    class ExitException(
        val reason: ExitReason,
        val state: ExitState,
    ) : Exception(
        when (reason) {
            is ExitReason.Unexpected -> reason.e.message
            else -> reason.toString()
        },
        (reason as? ExitReason.Unexpected)?.e
    )

    private class Finish(val reason: ExitReason) : Exception()

    private data class MutableSummary(
        val skillNumber: Int,
        val targetLevel: Int,
        var startingLevel: Int? = null,
        var endLevel: Int? = null,
        var result: EnhancementExitReason? = null,
    ) {
        fun snapshot() = Summary(skillNumber, startingLevel, endLevel, targetLevel, result)
    }

    private enum class EnhanceAttempt {
        Ready,
        OutOfMaterials,
        OutOfQP,
        NoResponse,
    }

    private val summaries
        get() = mutableSummaries.map(MutableSummary::snapshot)

    private var mutableSummaries: List<MutableSummary> = emptyList()

    override fun script(): Nothing {
        try {
            val targetLevel = if (prefs.skill.upgradeToLevel10) 10 else 9
            mutableSummaries = (1..3).map { MutableSummary(it, targetLevel) }
            upgradeSkills()
        } catch (e: Finish) {
            throw ExitException(e.reason, ExitState(summaries))
        } catch (e: ScriptAbortException) {
            throw ExitException(ExitReason.Abort, ExitState(summaries))
        } catch (e: Exception) {
            throw ExitException(ExitReason.Unexpected(e), ExitState(summaries))
        }
    }

    private fun upgradeSkills(): Nothing {
        if (isServantEmpty()) finish(ExitReason.NoServantSelected)
        if (!isSkillPage()) finish(ExitReason.PageRecognitionFailed)

        for (summary in mutableSummaries) {
            val result = upgradeSkill(summary)
            summary.result = result

            when (result) {
                EnhancementExitReason.OutOfMaterials,
                EnhancementExitReason.ResourceInsufficient -> Unit

                EnhancementExitReason.OutOfQP -> {
                    mutableSummaries
                        .drop(summary.skillNumber)
                        .forEach { it.result = EnhancementExitReason.SkippedAfterOutOfQP }
                    finish(ExitReason.RanOutOfQP)
                }

                EnhancementExitReason.PageRecognitionFailed ->
                    finish(ExitReason.PageRecognitionFailed)

                EnhancementExitReason.NoProgress ->
                    finish(ExitReason.NoProgress(summary.skillNumber))

                EnhancementExitReason.OcrFailed ->
                    finish(ExitReason.OcrFailed(summary.skillNumber))

                EnhancementExitReason.TargetLevelMet,
                EnhancementExitReason.SkippedAfterOutOfQP -> Unit
            }
        }

        finish(ExitReason.Done)
    }

    private fun upgradeSkill(summary: MutableSummary): EnhancementExitReason {
        val selectedLevel = selectAndVerifySkill(summary.skillNumber)
            ?: return when {
                !isSkillPage() -> EnhancementExitReason.PageRecognitionFailed
                isSkillSelected(summary.skillNumber) -> EnhancementExitReason.OcrFailed
                else -> EnhancementExitReason.PageRecognitionFailed
            }

        summary.startingLevel = selectedLevel
        summary.endLevel = selectedLevel

        if (selectedLevel >= summary.targetLevel) {
            return EnhancementExitReason.TargetLevelMet
        }

        var currentLevel = selectedLevel
        var confirmedUpgrades = 0
        val skillDeadline = TimeSource.Monotonic.markNow() + SKILL_TIMEOUT

        while (currentLevel < summary.targetLevel) {
            if (skillDeadline.hasPassedNow() || confirmedUpgrades >= MAX_UPGRADES_PER_SKILL) {
                return EnhancementExitReason.NoProgress
            }
            if (!verifySkillPage(summary.skillNumber)) {
                return EnhancementExitReason.PageRecognitionFailed
            }

            when (openAndConfirmEnhancement()) {
                EnhanceAttempt.OutOfMaterials -> return EnhancementExitReason.OutOfMaterials
                EnhanceAttempt.OutOfQP -> return EnhancementExitReason.OutOfQP
                // The game disables Enhance without any dialog when QP or materials run out
                // (also on ascension-capped skills), so a dead button means "not enough
                // resources" — move on to the next skill.
                EnhanceAttempt.NoResponse -> return EnhancementExitReason.ResourceInsufficient
                EnhanceAttempt.Ready -> Unit
            }

            confirmedUpgrades++
            val verifiedLevel = waitForLevelIncrease(summary.skillNumber, currentLevel)
                ?: return when {
                    !isSkillPage() -> EnhancementExitReason.PageRecognitionFailed
                    else -> EnhancementExitReason.NoProgress
                }

            currentLevel = verifiedLevel
            summary.endLevel = verifiedLevel
        }

        return EnhancementExitReason.TargetLevelMet
    }

    /**
     * A selection is accepted only when both the cyan frame and two equal OCR readings are present.
     */
    private fun selectAndVerifySkill(skillNumber: Int): Int? {
        repeat(SKILL_SELECTION_ATTEMPTS) {
            locations.skill.skillLocation(skillNumber).click()
            SELECTION_WAIT.wait()

            if (isSkillPage() && isSkillSelected(skillNumber)) {
                readStableLevel(skillNumber)?.let { return it }
            }
        }
        return null
    }

    /**
     * Single screenshot glitches (projection frame drops, CN client pulsing the cyan selection
     * frame) must not abort a run, so the page check gets a few retries.
     */
    private fun verifySkillPage(skillNumber: Int): Boolean =
        (1..PAGE_CHECK_ATTEMPTS).any {
            val ok = isSkillPage() && isSkillSelected(skillNumber)
            if (!ok) PAGE_CHECK_WAIT.wait()
            ok
        }

    private fun readStableLevel(skillNumber: Int): Int? {
        var previous: Int? = null

        repeat(OCR_ATTEMPTS) {
            val level = parseSkillLevelText(
                locations.skill.skillLevelRegion(skillNumber).detectDigits()
            )

            if (level != null && level == previous) return level
            previous = level
            OCR_RETRY_WAIT.wait()
        }

        return null
    }

    /**
     * Clicks Enhance and confirms only a recognized dialog. The game disables the button without
     * any dialog when QP or materials are insufficient, so a click that produces no response is
     * retried and then reported as [EnhanceAttempt.NoResponse].
     */
    private fun openAndConfirmEnhancement(): EnhanceAttempt {
        repeat(ENHANCE_CLICK_ATTEMPTS) {
            locations.enhancementClick.click()
            val deadline = TimeSource.Monotonic.markNow() + CONFIRM_DIALOG_TIMEOUT

            while (!deadline.hasPassedNow()) {
                if (connectionRetry.needsToRetry()) {
                    connectionRetry.retry()
                    continue
                }

                val screen = useSameSnapIn {
                    findConfirmationButton()?.let { ConfirmScreen.Confirmation(it.region) }
                        ?: findTemporaryServantButton()?.let { ConfirmScreen.TemporaryServant(it.region) }
                        ?: when {
                            isOutOfQP() -> ConfirmScreen.OutOfQP
                            isOutOfMaterials() -> ConfirmScreen.OutOfMaterials
                            else -> ConfirmScreen.Unknown
                        }
                }

                when (screen) {
                    is ConfirmScreen.Confirmation -> {
                        screen.button.click()
                        return EnhanceAttempt.Ready
                    }

                    is ConfirmScreen.TemporaryServant -> {
                        screen.button.click()
                        CONFIRM_RETRY_WAIT.wait()
                    }

                    ConfirmScreen.OutOfQP -> return EnhanceAttempt.OutOfQP
                    ConfirmScreen.OutOfMaterials -> return EnhanceAttempt.OutOfMaterials
                    ConfirmScreen.Unknown -> Unit
                }

                CONFIRM_RETRY_WAIT.wait()
            }
        }

        return EnhanceAttempt.NoResponse
    }

    private fun waitForLevelIncrease(skillNumber: Int, previousLevel: Int): Int? {
        val deadline = TimeSource.Monotonic.markNow() + LEVEL_CHANGE_TIMEOUT

        while (!deadline.hasPassedNow()) {
            if (connectionRetry.needsToRetry()) {
                connectionRetry.retry()
                continue
            }

            if (isSkillPage() && isSkillSelected(skillNumber)) {
                readStableLevel(skillNumber)?.let { level ->
                    if (level in (previousLevel + 1)..MAX_SKILL_LEVEL) return level
                }
            }

            locations.enhancementSkipRapidClick.click(5)
            LEVEL_POLL_WAIT.wait()
        }

        return null
    }

    private fun findConfirmationButton() =
        locations.skill.confirmationDialogRegion.find(images[Images.Ok])
            ?: if (prefs.gameServer is GameServer.Kr) {
                locations.skill.confirmationDialogRegion.find(images[Images.OkKR])
            } else null

    private fun findTemporaryServantButton() =
        locations.tempServantEnhancementRegion.find(images[Images.Execute])

    private fun isSkillPage() =
        locations.enhancementBannerRegion
            .exists(images[Images.SkillMenuBanner], similarity = 0.72) &&
            isAnySkillSelected()

    private fun isAnySkillSelected() = useSameSnapIn {
        (1..3).any { skillNumber ->
            locations.skill.selectedIndicatorRegion(skillNumber)
                .exists(images[Images.SkillSelected], similarity = 0.8)
        }
    }

    private fun isSkillSelected(skillNumber: Int) =
        locations.skill.selectedIndicatorRegion(skillNumber)
            .exists(images[Images.SkillSelected], similarity = 0.8)

    private fun isOutOfMaterials() =
        locations.skill.insufficientMaterialsRegion
            .exists(images[Images.SkillInsufficientMaterials], similarity = 0.72)

    private fun isOutOfQP() =
        locations.insufficientQPRegion
            .exists(images[Images.SkillInsufficientQP], similarity = 0.72)

    private fun isServantEmpty() =
        images[Images.EmptyEnhance] in locations.emptyEnhanceRegion

    private fun finish(reason: ExitReason): Nothing = throw Finish(reason)

    private sealed class ConfirmScreen {
        data class Confirmation(val button: Region) : ConfirmScreen()
        data class TemporaryServant(val button: Region) : ConfirmScreen()
        data object OutOfQP : ConfirmScreen()
        data object OutOfMaterials : ConfirmScreen()
        data object Unknown : ConfirmScreen()
    }

    private companion object {
        const val MAX_SKILL_LEVEL = 10
        const val OCR_ATTEMPTS = 4
        const val SKILL_SELECTION_ATTEMPTS = 3
        const val PAGE_CHECK_ATTEMPTS = 3
        const val ENHANCE_CLICK_ATTEMPTS = 3
        const val MAX_UPGRADES_PER_SKILL = 9

        val OCR_RETRY_WAIT = 250.milliseconds
        val SELECTION_WAIT = 750.milliseconds
        val PAGE_CHECK_WAIT = 300.milliseconds
        val CONFIRM_RETRY_WAIT = 400.milliseconds
        val LEVEL_POLL_WAIT = 500.milliseconds
        val CONFIRM_DIALOG_TIMEOUT = 2.seconds
        val LEVEL_CHANGE_TIMEOUT = 15.seconds
        val SKILL_TIMEOUT = 180.seconds
    }
}

private val SKILL_LEVEL_TEXT_REGEX = Regex("""(\d{1,2})/10""")

/**
 * The skill panel shows levels as `current/10` (CN: `等级 1/10`). Requiring the `/10` suffix
 * stops digit-whitelisted OCR garbage (e.g. `及1M0` filtered down to "10") from being read as
 * a maxed-out skill.
 */
internal fun parseSkillLevelText(ocrDigits: String): Int? =
    SKILL_LEVEL_TEXT_REGEX.find(ocrDigits)
        ?.groupValues?.get(1)
        ?.toIntOrNull()
        ?.takeIf { it in 1..10 }
