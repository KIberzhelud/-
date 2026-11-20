package com.example.data

import com.example.data.KeyboardSearchMatcher.matches

/**
 * Matches user search queries against Russian and Latin app/file/music names, handling:
 * 1. Exact substring matching (normalized: ё -> е)
 * 2. Wrong hardware keyboard layout typing (ЙЦУКЕН <-> QWERTY, e.g. "ntktuhfv" <-> "телеграм")
 * 3. Phonetic / transliteration RU <-> EN equivalence (e.g. "кот" <-> "kot" <-> "cat",
 *    "telegram" <-> "телеграм", "yandex" <-> "яндекс", "музыка" <-> "muzyka").
 *
 * Fuzzy non-contiguous subsequence matching is intentionally disabled so unrelated words
 * (e.g. "кот" vs "киберпортал") never false-positive.
 */
object KeyboardSearchMatcher {

    private val enToRuLayout = mapOf(
        'q' to 'й', 'w' to 'ц', 'e' to 'у', 'r' to 'к', 't' to 'е', 'y' to 'н',
        'u' to 'г', 'i' to 'ш', 'o' to 'щ', 'p' to 'з', '[' to 'х', ']' to 'ъ',
        'a' to 'ф', 's' to 'ы', 'd' to 'в', 'f' to 'а', 'g' to 'п', 'h' to 'р',
        'j' to 'о', 'k' to 'л', 'l' to 'д', ';' to 'ж', '\'' to 'э',
        'z' to 'я', 'x' to 'ч', 'c' to 'с', 'v' to 'м', 'b' to 'и', 'n' to 'т',
        'm' to 'ь', ',' to 'б', '.' to 'ю', '`' to 'ё'
    )

    private val ruToEnLayout = enToRuLayout.entries.associate { (k, v) -> v to k }

    private val ruMultiTranslit = listOf(
        "щ" to "shch",
        "ш" to "sh",
        "ч" to "ch",
        "ж" to "zh",
        "ю" to "yu",
        "я" to "ya",
        "ё" to "yo",
        "й" to "y",
        "ц" to "ts",
        "х" to "kh",
        "кс" to "x"
    )

    private val ruSingleTranslit = mapOf(
        'а' to 'a', 'б' to 'b', 'в' to 'v', 'г' to 'g', 'д' to 'd',
        'е' to 'e', 'ё' to 'e', 'ж' to 'j', 'з' to 'z', 'и' to 'i',
        'й' to 'y', 'к' to 'k', 'л' to 'l', 'м' to 'm', 'н' to 'n',
        'о' to 'o', 'п' to 'p', 'р' to 'r', 'с' to 's', 'т' to 't',
        'у' to 'u', 'ф' to 'f', 'х' to 'h', 'ц' to 'c', 'ч' to 'c',
        'ш' to 's', 'щ' to 's', 'ъ' to ' ', 'ы' to 'y', 'ь' to ' ',
        'э' to 'e', 'ю' to 'u', 'я' to 'a'
    )

    private val enDigraphToRu = listOf(
        "shch" to "щ",
        "sch" to "щ",
        "zh" to "ж",
        "kh" to "х",
        "ts" to "ц",
        "tc" to "ц",
        "ch" to "ч",
        "sh" to "ш",
        "yu" to "ю",
        "ju" to "ю",
        "ya" to "я",
        "ja" to "я",
        "yo" to "ё",
        "jo" to "ё",
        "ye" to "е",
        "ph" to "ф",
        "oo" to "у",
        "ee" to "и",
        "ea" to "и",
        "th" to "т",
        "ck" to "к",
        "qu" to "кв"
    )

    private val enSingleToRu: Map<Char, String> = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е",
        'f' to "ф", 'g' to "г", 'h' to "х", 'i' to "и", 'j' to "дж",
        'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о",
        'p' to "п", 'q' to "к", 'r' to "р", 's' to "с", 't' to "т",
        'u' to "у", 'v' to "в", 'w' to "в", 'x' to "кс", 'y' to "й",
        'z' to "з"
    )

    val latinAlphabet: List<Char> = ('A'..'Z').toList() + '#'
    val cyrillicAlphabet: List<Char> = listOf(
        'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'З', 'И', 'Й',
        'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С', 'Т', 'У',
        'Ф', 'Х', 'Ц', 'Ч', 'Ш', 'Щ', 'Э', 'Ю', 'Я', '#'
    )

    // Special phonetic equivalence clusters for rail & query normalization
    private val initialTranslitMap: Map<Char, Set<Char>> = mapOf(
        'A' to setOf('A', 'А', 'Э'),
        'А' to setOf('А', 'A'),
        'B' to setOf('B', 'Б', 'В'),
        'Б' to setOf('Б', 'B'),
        'C' to setOf('C', 'С', 'К', 'Ц'),
        'С' to setOf('С', 'S', 'C'),
        'Ц' to setOf('Ц', 'C', 'T'),
        'D' to setOf('D', 'Д'),
        'Д' to setOf('Д', 'D'),
        'E' to setOf('E', 'Е', 'Э', 'И'),
        'Е' to setOf('Е', 'E', 'Ё'),
        'Э' to setOf('Э', 'E', 'A'),
        'F' to setOf('F', 'Ф'),
        'Ф' to setOf('Ф', 'F', 'P'),
        'G' to setOf('G', 'Г', 'Д'),
        'Г' to setOf('Г', 'G', 'H'),
        'H' to setOf('H', 'Х', 'Г'),
        'Х' to setOf('Х', 'H', 'K', 'X'),
        'I' to setOf('I', 'И', 'А', 'Й'),
        'И' to setOf('И', 'I', 'E'),
        'Й' to setOf('Й', 'Y', 'I', 'J'),
        'J' to setOf('J', 'Д', 'Ж', 'Й'),
        'Ж' to setOf('Ж', 'J', 'Z'),
        'K' to setOf('K', 'К', 'Х'),
        'К' to setOf('К', 'K', 'C', 'Q'),
        'L' to setOf('L', 'Л'),
        'Л' to setOf('Л', 'L'),
        'M' to setOf('M', 'М'),
        'М' to setOf('М', 'M'),
        'N' to setOf('N', 'Н'),
        'Н' to setOf('Н', 'N'),
        'O' to setOf('O', 'О'),
        'О' to setOf('О', 'O'),
        'P' to setOf('P', 'П', 'Ф'),
        'П' to setOf('П', 'P'),
        'Q' to setOf('Q', 'К'),
        'R' to setOf('R', 'Р'),
        'Р' to setOf('Р', 'R'),
        'S' to setOf('S', 'С', 'Ш', 'Щ'),
        'Ш' to setOf('Ш', 'S'),
        'Щ' to setOf('Щ', 'S'),
        'T' to setOf('T', 'Т'),
        'Т' to setOf('Т', 'T'),
        'U' to setOf('U', 'У', 'Ю'),
        'У' to setOf('У', 'U', 'W'),
        'Ю' to setOf('Ю', 'Y', 'U', 'J'),
        'V' to setOf('V', 'В'),
        'В' to setOf('В', 'V', 'W', 'B'),
        'W' to setOf('W', 'В', 'У'),
        'X' to setOf('X', 'Х', 'К'),
        'Y' to setOf('Y', 'Я', 'Ю', 'Й', 'Ы'),
        'Я' to setOf('Я', 'Y', 'J', 'R'),
        'Ы' to setOf('Ы', 'Y'),
        'Z' to setOf('Z', 'З'),
        'З' to setOf('З', 'Z'),
        'Ч' to setOf('Ч', 'C')
    )

    fun normalize(text: String): String = text.lowercase().replace('ё', 'е')

    fun convertLayoutEnToRu(text: String): String =
        text.lowercase().map { enToRuLayout[it] ?: it }.joinToString("")

    fun convertLayoutRuToEn(text: String): String =
        text.lowercase().map { ruToEnLayout[it] ?: it }.joinToString("")

    fun transliterateRuToLatinVariants(text: String): List<String> {
        val norm = normalize(text)
        var multi = norm
        for ((ru, en) in ruMultiTranslit) {
            multi = multi.replace(ru, en)
        }
        val sb1 = StringBuilder()
        for (ch in multi) {
            val mapped = ruSingleTranslit[ch]
            if (mapped != null) {
                if (mapped != ' ') sb1.append(mapped)
            } else {
                sb1.append(ch)
            }
        }
        val sb2 = StringBuilder()
        for (ch in norm) {
            val mapped = ruSingleTranslit[ch]
            if (mapped != null) {
                if (mapped != ' ') sb2.append(mapped)
            } else {
                sb2.append(ch)
            }
        }
        val v1 = sb1.toString()
        val v2 = sb2.toString()
        // Handle 'c' <-> 'k' variation for Latin words like cat/kot or music/muzyka
        val cToK = v1.replace("k", "c")
        return listOf(v1, v2, cToK).filter { it.isNotBlank() }.distinct()
    }

    fun transliterateLatinToRuVariants(text: String): List<String> {
        var working = normalize(text)
        for ((digraph, ru) in enDigraphToRu) {
            working = working.replace(digraph, ru)
        }
        val sb = StringBuilder()
        for (ch in working) {
            sb.append(enSingleToRu[ch] ?: ch.toString())
        }
        val base = sb.toString()
        val cAsTs = working.replace('c', 'с')
        val sbTs = StringBuilder()
        for (ch in cAsTs) {
            sbTs.append(enSingleToRu[ch] ?: ch.toString())
        }
        // e.g. "cat" -> "кат" or "кэт" or "кот" sound-alikes don't replace vowels unless 'a'/'o'
        val cToS = text.lowercase().replace('c', 's').let { s ->
            val out = StringBuilder()
            for (ch in s) out.append(enSingleToRu[ch] ?: ch.toString())
            out.toString()
        }
        return listOf(base, sbTs.toString(), cToS).filter { it.isNotBlank() }.distinct()
    }

    /**
     * Checks if [label] matches an alphabet rail selection [letter] using either exact initial
     * OR RU<->EN phonetic transliteration (e.g. 'Т' matches "Telegram" & "Телеграм", 'T' matches "Телеграм").
     */
    fun matchesInitialWithTranslit(label: String, initialChar: Char, selectedLetter: Char): Boolean {
        val target = if (selectedLetter == 'Ё') 'Е' else selectedLetter.uppercaseChar()
        val normInitial = if (initialChar == 'Ё') 'Е' else initialChar.uppercaseChar()
        if (normInitial == target) return true
        if (target == '#') return normInitial == '#'
        val allowed = initialTranslitMap[target] ?: return false
        if (allowed.contains(normInitial)) return true
        // Handle common digraphs at start of label (Sh->Ш, Ch->Ч, Zh->Ж, Yu->Ю, Ya->Я)
        val low = normalize(label.trim())
        return when (target) {
            'Ш' -> low.startsWith("sh")
            'Щ' -> low.startsWith("shch") || low.startsWith("sch")
            'Ч' -> low.startsWith("ch")
            'Ж' -> low.startsWith("zh") || low.startsWith("j")
            'Ю' -> low.startsWith("yu") || low.startsWith("ju")
            'Я' -> low.startsWith("ya") || low.startsWith("ja")
            'Ц' -> low.startsWith("ts") || low.startsWith("c")
            else -> false
        }
    }

    fun matches(candidate: String, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return true

        val normTarget = normalize(candidate)
        val normQuery = normalize(query)

        // 1. Direct normalized substring
        if (normTarget.contains(normQuery)) return true

        // 2. Hardware layout swap (QWERTY <-> ЙЦУКЕН)
        val altRu = normalize(convertLayoutEnToRu(query))
        if (altRu != normQuery && normTarget.contains(altRu)) return true

        val altEn = normalize(convertLayoutRuToEn(query))
        if (altEn != normQuery && normTarget.contains(altEn)) return true

        // 3. Phonetic / RU <-> EN transliteration on both query & candidate
        val queryLatinVariants = transliterateRuToLatinVariants(normQuery)
        val targetLatinVariants = transliterateRuToLatinVariants(normTarget)
        for (qLat in queryLatinVariants) {
            if (qLat.length >= 2) {
                if (normTarget.contains(qLat)) return true
                if (targetLatinVariants.any { it.contains(qLat) }) return true
            }
        }

        val queryRuVariants = transliterateLatinToRuVariants(normQuery)
        val targetRuVariants = transliterateLatinToRuVariants(normTarget)
        for (qRu in queryRuVariants) {
            if (qRu.length >= 2) {
                if (normTarget.contains(qRu)) return true
                if (targetRuVariants.any { it.contains(qRu) }) return true
            }
        }

        // 4. Multi-word acronym / abbreviation matching (e.g. "Яндекс Музыка" -> "ямз")
        if (matchesMultiWordAbbrev(normTarget, normQuery)) return true
        if (altRu != normQuery && matchesMultiWordAbbrev(normTarget, altRu)) return true
        if (altEn != normQuery && matchesMultiWordAbbrev(normTarget, altEn)) return true
        if (queryLatinVariants.any { it.length >= 2 && matchesMultiWordAbbrev(normTarget, it) }) return true

        // 5. Handle short/strict phonetic pairs like "cat" <-> "kot"
        if (matchesCompactPhoneticPair(normTarget, normQuery)) return true

        return false
    }

    private fun matchesMultiWordAbbrev(normTarget: String, normQuery: String): Boolean {
        if (normQuery.length < 2) return false
        val words = normTarget.split(Regex("[\\s\\p{Punct}_]+"))
            .filter { it.isNotEmpty() }
        if (words.size < 2) return false

        fun search(wordIdx: Int, qIdx: Int, wordsTouched: Int): Boolean {
            if (qIdx == normQuery.length) return wordsTouched >= 2
            for (w in wordIdx until words.size) {
                val word = words[w]
                if (word[0] != normQuery[qIdx]) continue
                var charPos = 1
                var nextQ = qIdx + 1
                if (search(w + 1, nextQ, wordsTouched + 1)) return true
                while (nextQ < normQuery.length && charPos < word.length) {
                    val foundAt = word.indexOf(normQuery[nextQ], startIndex = charPos)
                    if (foundAt < 0) break
                    charPos = foundAt + 1
                    nextQ++
                    if (search(w + 1, nextQ, wordsTouched + 1)) return true
                }
            }
            return false
        }

        return search(0, 0, 0)
    }

    private fun matchesCompactPhoneticPair(normTarget: String, normQuery: String): Boolean {
        if (normQuery.length < 3 || normTarget.length < 3) return false
        val qSkeleton = consonantSkeleton(normQuery)
        val tSkeleton = consonantSkeleton(normTarget)
        if (qSkeleton.length >= 2 && normTarget.length <= normQuery.length + 3) {
            // Check exact consonant skeleton equality when lengths are close (handles cat<->kot)
            if (tSkeleton == qSkeleton) return true
        }
        return false
    }

    private fun consonantSkeleton(s: String): String {
        val lat = transliterateRuToLatinVariants(s).firstOrNull() ?: s
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y', ' ')
        return lat.map { if (it == 'c') 'k' else it }
            .filter { it.isLetter() && it !in vowels }
            .joinToString("")
    }

    fun matchScore(candidate: String, rawQuery: String): Int {
        val q = normalize(rawQuery.trim())
        if (q.isEmpty()) return 0
        val t = normalize(candidate)
        val altRu = normalize(convertLayoutEnToRu(rawQuery.trim()))
        val altEn = normalize(convertLayoutRuToEn(rawQuery.trim()))

        return when {
            t == q || t == altRu || t == altEn -> 100
            t.startsWith(q) || t.startsWith(altRu) || t.startsWith(altEn) -> 85
            t.contains(q) || t.contains(altRu) || t.contains(altEn) -> 70
            transliterateRuToLatinVariants(t).any { it.startsWith(q) } ||
                transliterateRuToLatinVariants(q).any { qLat -> t.startsWith(qLat) } -> 60
            transliterateLatinToRuVariants(q).any { qRu -> t.contains(qRu) } ||
                transliterateRuToLatinVariants(q).any { qLat ->
                    transliterateRuToLatinVariants(t).any { it.contains(qLat) }
                } -> 52
            matches(candidate, rawQuery) -> 42
            else -> 0
        }
    }
}
