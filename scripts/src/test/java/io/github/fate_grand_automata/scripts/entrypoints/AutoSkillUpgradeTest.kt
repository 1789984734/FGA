package io.github.fate_grand_automata.scripts.entrypoints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoSkillUpgradeTest {
    @Test
    fun `parses the current level out of current slash 10 text`() {
        (1..10).forEach { level ->
            assertEquals(level, parseSkillLevelText("$level/10"), "ocrDigits=$level/10")
        }
    }

    @Test
    fun `accepts trailing garbage after the max level`() {
        assertEquals(1, parseSkillLevelText("1/100"))
        assertEquals(5, parseSkillLevelText("5/1088"))
    }

    @Test
    fun `rejects OCR garbage that lost the slash`() {
        // '及1M0' filtered by the digit whitelist becomes "10", which used to read as level 10
        listOf("", "10", "110", "及1M0", "1/1", "0/10", "11/10", "99/10").forEach { text ->
            assertNull(parseSkillLevelText(text), "ocrDigits=$text")
        }
    }
}
