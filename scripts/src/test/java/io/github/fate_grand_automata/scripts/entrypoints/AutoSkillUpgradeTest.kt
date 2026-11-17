package io.github.fate_grand_automata.scripts.entrypoints

import io.github.fate_grand_automata.scripts.modules.SkillLevel
import io.github.fate_grand_automata.scripts.modules.parseSkillLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoSkillUpgradeTest {
    @Test
    fun `parses the current level out of current slash 10 text`() {
        (1..10).forEach { level ->
            assertEquals(SkillLevel.Known(level), parseSkillLevel("$level/10"), "ocrDigits=$level/10")
        }
    }

    @Test
    fun `ignores the max level after the slash`() {
        assertEquals(SkillLevel.Known(1), parseSkillLevel("1/100"))
        assertEquals(SkillLevel.Known(5), parseSkillLevel("5/1088"))
        assertEquals(SkillLevel.Known(1), parseSkillLevel("1/1"))
    }

    @Test
    fun `rejects OCR garbage and invalid levels`() {
        // '及1M0' filtered by the digit whitelist becomes "10", which used to read as level 10
        listOf("10", "110", "及1M0", "0/10", "11/10", "99/10").forEach { text ->
            assertEquals(SkillLevel.Unreadable, parseSkillLevel(text), "ocrDigits=$text")
        }
    }
}
