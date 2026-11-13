package io.github.fate_grand_automata.prefs.core

/**
 * @property shouldPerformAscension Redirect to ascension and perform it when the servant reaches max level.
 * @property shouldPerformGrail Redirect to palingenesis and perform it when the servant reaches max level.
 */
class ServantEnhancementPrefsCore(maker: PrefMaker) {

    val shouldPerformAscension = maker.bool("level_perform_servant_ascension")

    val shouldPerformGrail = maker.bool("level_perform_servant_grail")
}
