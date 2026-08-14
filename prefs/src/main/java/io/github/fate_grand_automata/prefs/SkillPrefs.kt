package io.github.fate_grand_automata.prefs

import io.github.fate_grand_automata.prefs.core.SkillPrefsCore
import io.github.fate_grand_automata.scripts.prefs.ISkillPreferences

internal class SkillPrefs(
    prefsCore: SkillPrefsCore
) : ISkillPreferences {
    override var upgradeToLevel10 by prefsCore.upgradeToLevel10
}
