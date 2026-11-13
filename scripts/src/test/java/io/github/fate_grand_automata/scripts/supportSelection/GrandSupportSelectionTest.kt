package io.github.fate_grand_automata.scripts.supportSelection

import io.github.fate_grand_automata.SupportImageKind
import io.github.fate_grand_automata.scripts.IFgoAutomataApi
import io.github.fate_grand_automata.scripts.IImageLoader
import io.github.fate_grand_automata.scripts.Images
import io.github.fate_grand_automata.scripts.enums.BondCEEffectEnum
import io.github.fate_grand_automata.scripts.enums.GameServer
import io.github.fate_grand_automata.scripts.locations.IScriptAreaTransforms
import io.github.fate_grand_automata.scripts.locations.Locations
import io.github.fate_grand_automata.scripts.locations.SupportScreenLocations
import io.github.fate_grand_automata.scripts.prefs.ISupportPreferences
import io.github.lib_automata.Location
import io.github.lib_automata.Match
import io.github.lib_automata.Pattern
import io.github.lib_automata.Region
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrandSupportSelectionTest {
    private val transforms = object : IScriptAreaTransforms {
        override val scriptArea = Region(0, 0, 2560, 1440)
        override val isWide = false
        override val gameServer = GameServer.Cn
        override val canLongSwipe = true
        override fun Location.xFromCenter() = this + Location(scriptArea.center.x, 0)
        override fun Region.xFromCenter() = this + Location(scriptArea.center.x, 0)
        override fun Location.xFromRight() = this + Location(scriptArea.right, 0)
        override fun Region.xFromRight() = this + Location(scriptArea.right, 0)
        override fun Location.yFromBottom() = this + Location(0, scriptArea.bottom)
        override fun Region.yFromBottom() = this + Location(0, scriptArea.bottom)
    }

    private val supportLocations = SupportScreenLocations(transforms)
    private val allLocations = Locations(
        scriptAreaTransforms = transforms,
        fp = mockk(relaxed = true),
        lottery = mockk(relaxed = true),
        support = supportLocations,
        attack = mockk(relaxed = true),
        battle = mockk(relaxed = true),
        skill = mockk(relaxed = true),
        servant = mockk(relaxed = true),
        soundPlayer = mockk(relaxed = true)
    )

    // 第2张卡片（完整显示）的位置，与模拟器实测一致
    private val cardBounds = SupportBounds(
        supportLocations.defaultBounds.copy(y = 668)
    )

    private fun createApi(
        grandPresent: Boolean,
        patternMatches: Map<Pattern, List<Match>> = emptyMap(),
        cePatterns: Map<String, List<Pattern>> = emptyMap()
    ): Pair<IFgoAutomataApi, SupportSelectionGrandChecker> {
        val images = mockk<IImageLoader>(relaxed = true)
        val grandLabelPattern = mockk<Pattern>(relaxed = true)
        every { images[Images.GrandCeLabel] } returns grandLabelPattern
        cePatterns.forEach { (name, patterns) ->
            every { images.loadSupportPattern(SupportImageKind.CE, name) } returns patterns
        }

        val api = mockk<IFgoAutomataApi>(relaxed = true)
        every { api.locations } returns allLocations
        every { api.images } returns images

        // 使用 with(api) 配置扩展函数 Region.findAll
        with(api) {
            every { any<Region>().findAll(any(), any()) } answers {
                val pattern = secondArg<Pattern>()
                patternMatches[pattern] ?: emptyList()
            }
        }

        val grandChecker = mockk<SupportSelectionGrandChecker>()
        every { grandChecker.isGrandPresent(any()) } returns grandPresent

        return api to grandChecker
    }

    @Test
    fun `unselected preferred servants filters out normal servants when grandServant is enabled`() {
        val prefs = mockk<ISupportPreferences>(relaxed = true)
        every { prefs.grandServant } returns true

        val (normalApi, normalGrandChecker) = createApi(grandPresent = false)
        val normalServantSelection = ServantSelection(
            api = normalApi,
            supportPrefs = prefs,
            starChecker = mockk(relaxed = true),
            grandChecker = normalGrandChecker
        )
        assertFalse(normalServantSelection.check(emptyList(), cardBounds))

        val (grandApi, grandChecker) = createApi(grandPresent = true)
        val grandServantSelection = ServantSelection(
            api = grandApi,
            supportPrefs = prefs,
            starChecker = mockk(relaxed = true),
            grandChecker = grandChecker
        )
        assertTrue(grandServantSelection.check(emptyList(), cardBounds))
    }

    @Test
    fun `unselected preferred servants accepts normal servants when grandServant is disabled`() {
        val prefs = mockk<ISupportPreferences>(relaxed = true)
        every { prefs.grandServant } returns false

        val (api, grandChecker) = createApi(grandPresent = false)
        val servantSelection = ServantSelection(
            api = api,
            supportPrefs = prefs,
            starChecker = mockk(relaxed = true),
            grandChecker = grandChecker
        )
        assertTrue(servantSelection.check(emptyList(), cardBounds))
    }

    @Test
    fun `requireBothNormalAndRewardMatch rejects grand servant when only reward slot matches`() {
        val prefs = mockk<ISupportPreferences>(relaxed = true)
        every { prefs.grandServant } returns true
        every { prefs.requireBothNormalAndRewardMatch } returns true
        every { prefs.mlb } returns false
        every { prefs.bondCEEffect } returns BondCEEffectEnum.Ignore

        val lunchtime = mockk<Pattern>(relaxed = true)
        val teatime = mockk<Pattern>(relaxed = true)

        // 模拟：常规槽1为异星之神（未选中），加成槽3为迦勒底午茶时光
        val teatimeSlot3Match = Match(Region(452, 958, 294, 82), 0.97)
        val (api, grandChecker) = createApi(
            grandPresent = true,
            patternMatches = mapOf(
                lunchtime to emptyList(),
                teatime to listOf(teatimeSlot3Match)
            ),
            cePatterns = mapOf(
                "Chaldea Lunchtime.png" to listOf(lunchtime),
                "Chaldea Teatime.png" to listOf(teatime)
            )
        )

        val ceSelection = CESelection(
            api = api,
            supportPrefs = prefs,
            starChecker = mockk(relaxed = true),
            grandChecker = grandChecker
        )

        assertFalse(
            ceSelection.check(listOf("Chaldea Lunchtime.png", "Chaldea Teatime.png"), cardBounds)
        )
    }

    @Test
    fun `requireBothNormalAndRewardMatch accepts grand servant when both slot 1 and slot 3 match`() {
        val prefs = mockk<ISupportPreferences>(relaxed = true)
        every { prefs.grandServant } returns true
        every { prefs.requireBothNormalAndRewardMatch } returns true
        every { prefs.mlb } returns false
        every { prefs.bondCEEffect } returns BondCEEffectEnum.Ignore

        val lunchtime = mockk<Pattern>(relaxed = true)
        val teatime = mockk<Pattern>(relaxed = true)

        val lunchtimeSlot1Match = Match(Region(448, 772, 284, 50), 0.96)
        val teatimeSlot3Match = Match(Region(452, 958, 294, 82), 0.97)
        val (api, grandChecker) = createApi(
            grandPresent = true,
            patternMatches = mapOf(
                lunchtime to listOf(lunchtimeSlot1Match),
                teatime to listOf(teatimeSlot3Match)
            ),
            cePatterns = mapOf(
                "Chaldea Lunchtime.png" to listOf(lunchtime),
                "Chaldea Teatime.png" to listOf(teatime)
            )
        )

        val ceSelection = CESelection(
            api = api,
            supportPrefs = prefs,
            starChecker = mockk(relaxed = true),
            grandChecker = grandChecker
        )

        assertTrue(
            ceSelection.check(listOf("Chaldea Lunchtime.png", "Chaldea Teatime.png"), cardBounds)
        )
    }

    @Test
    fun `findAll allows single CE pattern to match both slot 1 and slot 3`() {
        val prefs = mockk<ISupportPreferences>(relaxed = true)
        every { prefs.grandServant } returns true
        every { prefs.requireBothNormalAndRewardMatch } returns true
        every { prefs.mlb } returns false
        every { prefs.bondCEEffect } returns BondCEEffectEnum.Ignore

        val customCe = mockk<Pattern>(relaxed = true)
        val slot1Match = Match(Region(448, 772, 284, 50), 0.95)
        val slot3Match = Match(Region(452, 958, 294, 82), 0.97)

        val (api, grandChecker) = createApi(
            grandPresent = true,
            patternMatches = mapOf(
                customCe to listOf(slot3Match, slot1Match)
            ),
            cePatterns = mapOf(
                "custom.png" to listOf(customCe)
            )
        )

        val ceSelection = CESelection(
            api = api,
            supportPrefs = prefs,
            starChecker = mockk(relaxed = true),
            grandChecker = grandChecker
        )

        assertTrue(ceSelection.check(listOf("custom.png"), cardBounds))
    }
}
