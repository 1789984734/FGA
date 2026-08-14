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
        // Includes `current/10`. Digit OCR preserves `/`, and the parser takes the first number.
        Region(-120 + SKILL_SPACING * (skillNumber - 1), if (isWide) 545 else 585, 150, 56)
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

    fun materialRequiredRegion(slot: Int) = Region(
        -280 + MATERIAL_SPACING * (slot - 1),
        if (isWide) 1035 else 1075,
        110,
        50
    ).xFromCenter()

    fun materialOwnedRegion(slot: Int) = Region(
        -365 + MATERIAL_SPACING * (slot - 1),
        if (isWide) 1085 else 1125,
        195,
        55
    ).xFromCenter()

    val qpRequiredRegion = Region(180, if (isWide) 1225 else 1265, 380, 65).xFromCenter()

    val qpOwnedRegion = Region(60, if (isWide) 1305 else 1345, 510, 70).xFromCenter()

    private companion object {
        const val SKILL_SPACING = 576
        const val MATERIAL_SPACING = 360
    }
}
