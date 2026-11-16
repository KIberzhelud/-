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
        assertFalse(KeyboardSearchMatcher.matches("Chrome", "xyz"))
    }

    @Test
    fun computeInitialCharNormalizesLatinCyrillicAndDigits() {
        assertEquals('A', AppsRepository.computeInitialChar("Adblock Browser"))
        assertEquals('#', AppsRepository.computeInitialChar("2ГИС"))
        assertEquals('А', AppsRepository.computeInitialChar("Авито"))
        assertEquals('Е', AppsRepository.computeInitialChar("Ёлка"))
    }
}
