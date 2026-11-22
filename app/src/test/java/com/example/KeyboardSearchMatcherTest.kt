package com.example

import com.example.data.AppsRepository
import com.example.data.KeyboardSearchMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSearchMatcherTest {

    @Test
    fun matchesDirectAndBilingualLayoutQueries() {
        assertTrue(KeyboardSearchMatcher.matches("Chrome", "chr"))
        assertTrue(KeyboardSearchMatcher.matches("Chrome", "срк"))
        assertTrue(KeyboardSearchMatcher.matches("Авито", "ави"))
        assertTrue(KeyboardSearchMatcher.matches("Авито", "fdb"))
        assertTrue(KeyboardSearchMatcher.matches("Яндекс Музыка", "ямз"))
        assertFalse(KeyboardSearchMatcher.matches("Киберпортал", "кот"))
        assertFalse(KeyboardSearchMatcher.matches("Chrome", "xyz"))
    }

    @Test
    fun computeInitialCharNormalizesLatinCyrillicAndDigits() {
        assertEquals('A', AppsRepository.computeInitialChar("Adblock Browser"))
        assertEquals('#', AppsRepository.computeInitialChar("2ГИС"))
        assertEquals('А', AppsRepository.computeInitialChar("Авито"))
        assertEquals('Е', AppsRepository.computeInitialChar("Ёлка"))
    }

    @Test
    fun cachedMatchesRemainConsistentAcrossRepeatedQueriesAndTranslit() {
        val candidates = listOf(
            "Telegram Desktop",
            "Яндекс Музыка",
            "Poweramp Player",
            "VLC Media Player",
            "Киберпортал_2026"
        )
        val queries = listOf("teleg", "вуыл", "zyltrc", "ьгя", "vlc", "kiber", "xyz_miss")
        repeat(3) {
            for (q in queries) {
                val results = candidates.filter { KeyboardSearchMatcher.matches(it, q) }
                when (q) {
                    "teleg" -> assertEquals(listOf("Telegram Desktop"), results)
                    "zyltrc", "ьгя" -> assertEquals(listOf("Яндекс Музыка"), results)
                    "vlc" -> assertEquals(listOf("VLC Media Player"), results)
                    "kiber" -> assertEquals(listOf("Киберпортал_2026"), results)
                    "xyz_miss" -> assertTrue(results.isEmpty())
                }
            }
        }
    }
}
