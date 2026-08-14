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

    private sealed class ResourceCheck {
        data object Ready : ResourceCheck()
        data object OutOfMaterials : ResourceCheck()
        data object OutOfQP : ResourceCheck()
        data object OcrFailed : ResourceCheck()
    }

    private data class Cost(val required: Long?, val owned: Long?)

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
                EnhancementExitReason.OutOfMaterials -> Unit

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
            if (!isSkillPage() || !isSkillSelected(summary.skillNumber)) {
                return EnhancementExitReason.PageRecognitionFailed
            }

            when (checkResources()) {
                ResourceCheck.OutOfMaterials -> return EnhancementExitReason.OutOfMaterials
                ResourceCheck.OutOfQP -> return EnhancementExitReason.OutOfQP
                ResourceCheck.OcrFailed -> return EnhancementExitReason.OcrFailed
                ResourceCheck.Ready -> Unit
            }

            when (openAndConfirmEnhancement()) {
                ResourceCheck.OutOfMaterials -> return EnhancementExitReason.OutOfMaterials
                ResourceCheck.OutOfQP -> return EnhancementExitReason.OutOfQP
                ResourceCheck.OcrFailed -> return EnhancementExitReason.NoProgress
                ResourceCheck.Ready -> Unit
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

    private fun readStableLevel(skillNumber: Int): Int? {
        var previous: Int? = null

        repeat(OCR_ATTEMPTS) {
            val level = locations.skill.skillLevelRegion(skillNumber)
                .findNumberInText()
                ?.takeIf { it in MIN_SKILL_LEVEL..MAX_SKILL_LEVEL }

            if (level != null && level == previous) return level
            previous = level
            OCR_RETRY_WAIT.wait()
        }

        return null
    }

    private fun checkResources(): ResourceCheck {
        val recognizedFailure = useSameSnapIn {
            when {
                isOutOfQP() -> ResourceCheck.OutOfQP
                isOutOfMaterials() -> ResourceCheck.OutOfMaterials
                else -> null
            }
        }
        if (recognizedFailure != null) return recognizedFailure

        val qpCost = readStableCost(
            locations.skill.qpRequiredRegion,
            locations.skill.qpOwnedRegion,
            optional = false
        ) ?: return ResourceCheck.OcrFailed

        val requiredQP = qpCost.required ?: return ResourceCheck.OcrFailed
        val ownedQP = qpCost.owned ?: return ResourceCheck.OcrFailed
        if (ownedQP < requiredQP) return ResourceCheck.OutOfQP

        for (slot in 1..MATERIAL_SLOTS) {
            val cost = readStableCost(
                locations.skill.materialRequiredRegion(slot),
                locations.skill.materialOwnedRegion(slot),
                optional = slot > 1
            ) ?: return ResourceCheck.OcrFailed

            if (cost.required == null && cost.owned == null) continue
            val required = cost.required ?: return ResourceCheck.OcrFailed
            val owned = cost.owned ?: return ResourceCheck.OcrFailed
            if (owned < required) return ResourceCheck.OutOfMaterials
        }

        return ResourceCheck.Ready
    }

    private fun readStableCost(
        requiredRegion: Region,
        ownedRegion: Region,
        optional: Boolean,
    ): Cost? {
        var previous: Cost? = null

        repeat(OCR_ATTEMPTS) {
            val cost = useSameSnapIn {
                Cost(
                    requiredRegion.findLongInText(),
                    ownedRegion.findLongInText()
                )
            }

            val isAbsent = cost.required == null && cost.owned == null
            val isValid = cost.required?.let { required ->
                cost.owned?.let { owned -> required > 0 && owned >= 0 }
            } == true

            if (cost == previous && (isValid || optional && isAbsent)) return cost
            previous = cost
            OCR_RETRY_WAIT.wait()
        }

        return null
    }

    /**
     * Clicks Enhance at most once for this level and confirms only a recognized dialog.
     */
    private fun openAndConfirmEnhancement(): ResourceCheck {
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
                    return ResourceCheck.Ready
                }

                is ConfirmScreen.TemporaryServant -> {
                    screen.button.click()
                    CONFIRM_RETRY_WAIT.wait()
                }

                ConfirmScreen.OutOfQP -> return ResourceCheck.OutOfQP
                ConfirmScreen.OutOfMaterials -> return ResourceCheck.OutOfMaterials
                ConfirmScreen.Unknown -> Unit
            }

            CONFIRM_RETRY_WAIT.wait()
        }

        // The button produced no recognized transition. Do not guess whether QP or materials caused
        // it, because guessing "materials" could incorrectly continue after an OCR-missed QP stop.
        return ResourceCheck.OcrFailed
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

            locations.enhancementSkipRapidClick.click()
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
                .exists(images[Images.SkillSelected], similarity = 0.85)
        }
    }

    private fun isSkillSelected(skillNumber: Int) =
        locations.skill.selectedIndicatorRegion(skillNumber)
            .exists(images[Images.SkillSelected], similarity = 0.85)

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
        const val MIN_SKILL_LEVEL = 1
        const val MAX_SKILL_LEVEL = 10
        const val MATERIAL_SLOTS = 2
        const val OCR_ATTEMPTS = 4
        const val SKILL_SELECTION_ATTEMPTS = 3
        const val MAX_UPGRADES_PER_SKILL = 9

        val OCR_RETRY_WAIT = 250.milliseconds
        val SELECTION_WAIT = 750.milliseconds
        val CONFIRM_RETRY_WAIT = 400.milliseconds
        val LEVEL_POLL_WAIT = 500.milliseconds
        val CONFIRM_DIALOG_TIMEOUT = 5.seconds
        val LEVEL_CHANGE_TIMEOUT = 15.seconds
        val SKILL_TIMEOUT = 180.seconds
    }
}
