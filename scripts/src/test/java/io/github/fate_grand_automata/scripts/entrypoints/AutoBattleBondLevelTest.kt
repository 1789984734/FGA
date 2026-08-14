package io.github.fate_grand_automata.scripts.entrypoints

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBattleBondLevelTest {
    @Test
    fun `stops only when the reached level is 11 through 15`() {
        (0..20).forEach { previousLevel ->
            val reachedLevel = previousLevel + 1

            if (reachedLevel in 11..15) {
                assertTrue(
                    isBondLevelUpTo11Through15(previousLevel.toString()),
                    "previousLevel=$previousLevel, reachedLevel=$reachedLevel"
                )
            } else {
                assertFalse(
                    isBondLevelUpTo11Through15(previousLevel.toString()),
                    "previousLevel=$previousLevel, reachedLevel=$reachedLevel"
                )
            }
        }
    }

    @Test
    fun `accepts surrounding whitespace and rejects malformed OCR text`() {
        assertTrue(isBondLevelUpTo11Through15(" 10\n"))
        assertTrue(isBondLevelUpTo11Through15("\t14 "))

        listOf("", "1 0", "10 30", "abc", "I0").forEach { text ->
            assertFalse(isBondLevelUpTo11Through15(text), "ocrText=$text")
        }
    }
}
