package io.github.fate_grand_automata.scripts.locations

import io.github.lib_automata.Location
import io.github.lib_automata.Region
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject

@ScriptScope
class SoundPlayerLocations @Inject constructor(
    scriptAreaTransforms: IScriptAreaTransforms
) : IScriptAreaTransforms by scriptAreaTransforms {

    /**
     * 右上角“音乐鉴赏”标题区域，用于通过 OCR 或模板检测是否处于音乐鉴赏页面
     */
    val titleRegion = Region(-650, 0, 650, 180).xFromRight()

    /**
     * 列表右侧素材需求框的动态扫描区域（纵向扫描整个列表右侧）
     */
    val listCheckRegion = Region(-380, 180, 250, 1100).xFromRight()

    /**
     * 确认开放弹窗中的【实行】按钮检测区域
     */
    val executeRegion = Region(280, 1060, 400, 120).xFromCenter()

    /**
     * 确认开放弹窗中的【实行】按钮点击位置
     */
    val executeClick = Location(480, 1120).xFromCenter()

    /**
     * 确认开放弹窗中的【取消】按钮点击位置（用于素材不足时跳过）
     */
    val cancelClick = Location(-480, 1120).xFromCenter()

    /**
     * 开放成功弹窗中的【关闭】按钮检测区域
     */
    val unlockedCloseRegion = Region(-200, 1060, 400, 120).xFromCenter()

    /**
     * 开放成功弹窗中的【关闭】按钮点击位置
     */
    val unlockedCloseClick = Location(0, 1120).xFromCenter()

    /**
     * 列表翻页滑动起点（底部）与终点（顶部）
     */
    val swipeStart = Location(-600, 1100).xFromRight()
    val swipeEnd = Location(-600, 300).xFromRight()
}
