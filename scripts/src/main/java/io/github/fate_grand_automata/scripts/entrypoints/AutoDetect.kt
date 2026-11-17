package io.github.fate_grand_automata.scripts.entrypoints

import io.github.fate_grand_automata.scripts.IFgoAutomataApi
import io.github.fate_grand_automata.scripts.Images
import io.github.fate_grand_automata.scripts.enums.ScriptModeEnum
import io.github.fate_grand_automata.scripts.modules.AutoSkillSetup
import io.github.fate_grand_automata.scripts.modules.EnhancementScreen
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject

@ScriptScope
class AutoDetect @Inject constructor(
    api: IFgoAutomataApi,
    private val autoSkillSetup: AutoSkillSetup,
    private val enhancementScreen: EnhancementScreen,
) : IFgoAutomataApi by api {
    fun get(): ScriptModeEnum {
        val mode = useSameSnapIn { detectMode() }

        if (mode == ScriptModeEnum.Skill) {
            autoSkillSetup.readSkillScreen()
        }

        return mode
    }

    private fun detectMode(): ScriptModeEnum {
        val emberSearchRegion = locations.scriptArea.let {
            it.copy(width = it.width / 3)
        }

        return when {
            images[Images.FriendSummon] in locations.fp.summonCheck ||
                    findImage(locations.fp.continueSummonRegion, Images.FPSummonContinue) ||
                    images[Images.FriendSummon] in locations.fp.initialSummonCheck ->
                ScriptModeEnum.FP

            images[Images.LotteryBoxFinished] in locations.lottery.checkRegion || images[Images.LotteryBoxFinished] in locations.lottery.finishedRegion ->
                ScriptModeEnum.Lottery

            listOf(images[Images.GoldXP], images[Images.SilverXP], images[Images.Gold5StarXP]) in emberSearchRegion ->
                ScriptModeEnum.PresentBox

            locations.support.confirmSetupButtonRegion.exists(images[Images.SupportConfirmSetupButton], similarity = 0.75) ->
                ScriptModeEnum.SupportImageMaker

            enhancementScreen.isSkillEnhancementMenu() ->
                ScriptModeEnum.Skill

            mapOf(
                images[Images.ServantAutoSelect] to locations.servant.servantAutoSelectRegion,
                images[Images.ServantAutoSelectOff] to locations.servant.servantAutoSelectRegion,
                images[Images.ServantAscensionBanner] to locations.enhancementBannerRegion
            ).exists()->
                ScriptModeEnum.ServantLevel

            enhancementScreen.isNothingSelected() ->
                ScriptModeEnum.CEBomb

            isSoundPlayerPage() ->
                ScriptModeEnum.SoundPlayer

            else -> ScriptModeEnum.Battle
        }
    }

    private fun isSoundPlayerPage(): Boolean {
        // 1. 优先使用 OpenCV 模板匹配右上角标题
        if (locations.soundPlayer.titleRegion.exists(images[Images.SoundPlayer], similarity = 0.65)) {
            return true
        }

        // 2. 备用：检查列表中是否有未开放的【所需】标记
        val hasSoundRequire = locations.soundPlayer.listCheckRegion.exists(images[Images.SoundRequire], similarity = 0.55)
        if (hasSoundRequire) {
            return true
        }

        // 3. 备用：本地 OCR 识别
        return try {
            val text = locations.soundPlayer.titleRegion.detectText().lowercase()
            "音乐鉴赏" in text ||
                    "音乐" in text ||
                    "鉴赏" in text ||
                    "sound" in text ||
                    "player" in text ||
                    "サウンド" in text ||
                    "プレイヤー" in text
        } catch (_: Throwable) {
            false
        }
    }
}
