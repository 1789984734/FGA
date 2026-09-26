package io.github.fate_grand_automata.scripts.locations

import io.github.lib_automata.Location
import io.github.lib_automata.Region
import javax.inject.Inject

class SkillLocations @Inject constructor(
    transforms: IScriptAreaTransforms
) : IScriptAreaTransforms by transforms {

    fun skillLocation(skillNumber: Int) =
        Location(-339 + SKILL_SPACING * (skillNumber - 1), 519).xFromCenter()

    fun skillLevelRegion(skillNumber: Int) =
        // Only the `current/10` digits. Including the `等级`/`Lv.` prefix makes ML Kit emit CJK
        // garbage (e.g. '及1M0'), and the parser requires the `/10` suffix.
        Region(-90 + SKILL_SPACING * (skillNumber - 1), if (isWide) 545 else 585, 130, 60)
            .xFromCenter()

    /** A narrow slice of the cyan selection frame, excluding the skill icon itself. */
    fun selectedIndicatorRegion(skillNumber: Int) =
        Region(-470 + SKILL_SPACING * (skillNumber - 1), if (isWide) 356 else 396, 28, 96)
            .xFromCenter()

    val confirmationDialogRegion = when (isWide) {
        true -> Region(280, 1035, 290, 165).xFromCenter()
        false -> Region(280, 1075, 290, 165).xFromCenter()
    }

    val insufficientMaterialsRegion = when (isWide) {
        true -> Region(-520, 188, 760, 65).xFromCenter()
        false -> Region(-520, 218, 760, 65).xFromCenter()
    }

    private companion object {
        const val SKILL_SPACING = 576
    }
}
