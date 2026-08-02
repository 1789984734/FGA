package io.github.fate_grand_automata.prefs.core

/**
 * @param shouldRedirectAscension If the servant is max level, it will check if it can redirect to ascension
 * @param shouldPerformAscension If the servant is max level, it will perform ascension after redirecting to ascension
 * @param shouldRedirectGrail If the servant is max level, it will check if it can redirect to grail
 * @param shouldPerformGrail If the servant is max level, it will perform palingenesis after redirecting
 */
class ServantEnhancementPrefsCore(maker: PrefMaker) {

    val shouldRedirectAscension = maker.bool("level_redirect_servant_ascension")

    val shouldPerformAscension = maker.bool("level_perform_servant_ascension")

    val shouldRedirectGrail = maker.bool("level_redirect_servant_grail")

    val shouldPerformGrail = maker.bool("level_perform_servant_grail")
}
