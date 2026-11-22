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

    private val WORD_SPLIT_REGEX = Regex("[\\s\\p{Punct}_]+")
    private const val TARGET_CACHE_LIMIT = 640
    private const val QUERY_CACHE_LIMIT = 48

    private data class PreparedTarget(
        val norm: String,
        val words: List<String>,
        val latinVariants: List<String>,
        val ruVariants: List<String>,
        val skeleton: String
    )

    private data class PreparedQuery(
        val norm: String,
        val altRu: String,
        val altEn: String,
        val latinVariants: List<String>,
        val ruVariants: List<String>,
        val skeleton: String
    )

    private val targetCacheLock = Any()
    private val targetCache = object : LinkedHashMap<String, PreparedTarget>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreparedTarget>?): Boolean =
            size > TARGET_CACHE_LIMIT
    }

    private val queryCacheLock = Any()
    private val queryCache = object : LinkedHashMap<String, PreparedQuery>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreparedQuery>?): Boolean =
            size > QUERY_CACHE_LIMIT
    }

    fun normalize(text: String): String = text.lowercase().replace('ё', 'е')

    private fun prepareTarget(candidate: String): PreparedTarget {
        synchronized(targetCacheLock) {
            targetCache[candidate]?.let { return it }
        }
        val norm = normalize(candidate)
        val words = WORD_SPLIT_REGEX.split(norm).filter { it.isNotEmpty() }
        val latVars = transliterateRuToLatinVariants(norm)
        val ruVars = transliterateLatinToRuVariants(norm)
        val skel = consonantSkeletonFromLatin(latVars.firstOrNull() ?: norm)
        val prepared = PreparedTarget(
            norm = norm,
            words = words,
            latinVariants = latVars,
            ruVariants = ruVars,
            skeleton = skel
        )
        synchronized(targetCacheLock) {
            targetCache[candidate] = prepared
        }
        return prepared
    }

    private fun prepareQuery(trimmedQuery: String): PreparedQuery {
        synchronized(queryCacheLock) {
            queryCache[trimmedQuery]?.let { return it }
        }
        val norm = normalize(trimmedQuery)
        val altRu = normalize(convertLayoutEnToRu(trimmedQuery))
        val altEn = normalize(convertLayoutRuToEn(trimmedQuery))
        val latVars = transliterateRuToLatinVariants(norm)
        val ruVars = transliterateLatinToRuVariants(norm)
        val skel = consonantSkeletonFromLatin(latVars.firstOrNull() ?: norm)
        val prepared = PreparedQuery(
            norm = norm,
            altRu = altRu,
            altEn = altEn,
            latinVariants = latVars,
            ruVariants = ruVars,
            skeleton = skel
        )
        synchronized(queryCacheLock) {
            queryCache[trimmedQuery] = prepared
        }
        return prepared
    }

    fun convertLayoutEnToRu(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (i in text.indices) {
            val ch = text[i].lowercaseChar()
            sb.append(enToRuLayout[ch] ?: ch)
        }
        return sb.toString()
    }

    fun convertLayoutRuToEn(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (i in text.indices) {
            val ch = text[i].lowercaseChar()
            sb.append(ruToEnLayout[ch] ?: ch)
        }
        return sb.toString()
    }

    fun transliterateRuToLatinVariants(text: String): List<String> {
        val norm = normalize(text)
        var multi = norm
        for ((ru, en) in ruMultiTranslit) {
            if (multi.contains(ru)) {
                multi = multi.replace(ru, en)
            }
        }
        val sb1 = StringBuilder(multi.length)
        for (i in multi.indices) {
            val ch = multi[i]
            val mapped = ruSingleTranslit[ch]
            if (mapped != null) {
                if (mapped != ' ') sb1.append(mapped)
            } else {
                sb1.append(ch)
            }
        }
        val sb2 = StringBuilder(norm.length)
        for (i in norm.indices) {
            val ch = norm[i]
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
        val cToK = if (v1.contains('k')) v1.replace('k', 'c') else v1
        val out = ArrayList<String>(3)
        if (v1.isNotBlank()) out.add(v1)
        if (v2.isNotBlank() && v2 != v1) out.add(v2)
        if (cToK.isNotBlank() && cToK != v1 && cToK != v2) out.add(cToK)
        return out
    }

    fun transliterateLatinToRuVariants(text: String): List<String> {
        var working = normalize(text)
        for ((digraph, ru) in enDigraphToRu) {
            if (working.contains(digraph)) {
                working = working.replace(digraph, ru)
            }
        }
        val sb = StringBuilder(working.length)
        for (i in working.indices) {
            val ch = working[i]
            sb.append(enSingleToRu[ch] ?: ch.toString())
        }
        val base = sb.toString()
        val cAsTs = if (working.contains('c')) working.replace('c', 'с') else working
        val sbTs = StringBuilder(cAsTs.length)
        for (i in cAsTs.indices) {
            val ch = cAsTs[i]
            sbTs.append(enSingleToRu[ch] ?: ch.toString())
        }
        val tsStr = sbTs.toString()
        // e.g. "cat" -> "кат" or "кэт" or "кот" sound-alikes don't replace vowels unless 'a'/'o'
        val cToS = text.lowercase().let { lower ->
            val replaced = if (lower.contains('c')) lower.replace('c', 's') else lower
            val out = StringBuilder(replaced.length)
            for (i in replaced.indices) {
                val ch = replaced[i]
                out.append(enSingleToRu[ch] ?: ch.toString())
            }
            out.toString()
        }
        val out = ArrayList<String>(3)
        if (base.isNotBlank()) out.add(base)
        if (tsStr.isNotBlank() && tsStr != base) out.add(tsStr)
        if (cToS.isNotBlank() && cToS != base && cToS != tsStr) out.add(cToS)
        return out
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
        if (candidate.isEmpty()) return false

        // Zero-cache-miss ultra-fast check for plain case-insensitive substring
        if (candidate.contains(query, ignoreCase = true)) return true

        val pq = prepareQuery(query)
        val pt = prepareTarget(candidate)
        return matchesPrepared(pt, pq)
    }

    private fun matchesPrepared(pt: PreparedTarget, pq: PreparedQuery): Boolean {
        val normTarget = pt.norm
        val normQuery = pq.norm

        // 1. Direct normalized substring (handles ё -> е)
        if (normTarget.contains(normQuery)) return true

        // 2. Hardware layout swap (QWERTY <-> ЙЦУКЕН)
        if (pq.altRu != normQuery && normTarget.contains(pq.altRu)) return true
        if (pq.altEn != normQuery && normTarget.contains(pq.altEn)) return true
        if (pq.altEn != normQuery && pq.altEn.length >= 3 && pt.latinVariants.any { it.contains(pq.altEn) }) return true
        if (pq.altRu != normQuery && pq.altRu.length >= 3 && pt.ruVariants.any { it.contains(pq.altRu) }) return true

        // 3. Phonetic / RU <-> EN transliteration on both query & candidate
        for (qLat in pq.latinVariants) {
            if (qLat.length >= 2) {
                if (normTarget.contains(qLat)) return true
                for (tLat in pt.latinVariants) {
                    if (tLat.contains(qLat)) return true
                }
            }
        }

        for (qRu in pq.ruVariants) {
            if (qRu.length >= 2) {
                if (normTarget.contains(qRu)) return true
                for (tRu in pt.ruVariants) {
                    if (tRu.contains(qRu)) return true
                }
            }
        }

        // 4. Multi-word acronym / abbreviation matching (e.g. "Яндекс Музыка" -> "ямз")
        if (pt.words.size >= 2) {
            if (matchesMultiWordAbbrev(pt.words, normQuery)) return true
            if (pq.altRu != normQuery && matchesMultiWordAbbrev(pt.words, pq.altRu)) return true
            if (pq.altEn != normQuery && matchesMultiWordAbbrev(pt.words, pq.altEn)) return true
            for (qLat in pq.latinVariants) {
                if (qLat.length >= 2 && matchesMultiWordAbbrev(pt.words, qLat)) return true
            }
        }

        // 5. Handle short/strict phonetic pairs like "cat" <-> "kot"
        if (normQuery.length >= 3 && normTarget.length >= 3 &&
            pq.skeleton.length >= 2 &&
            normTarget.length <= normQuery.length + 3 &&
            pt.skeleton == pq.skeleton
        ) {
            return true
        }

        return false
    }

    private fun matchesMultiWordAbbrev(words: List<String>, normQuery: String): Boolean {
        if (normQuery.length < 2 || words.size < 2) return false

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

    private fun isLatinVowelOrSpace(ch: Char): Boolean = when (ch) {
        'a', 'e', 'i', 'o', 'u', 'y', ' ' -> true
        else -> false
    }

    private fun consonantSkeletonFromLatin(lat: String): String {
        val sb = StringBuilder(lat.length)
        for (i in lat.indices) {
            val mapped = if (lat[i] == 'c') 'k' else lat[i]
            if (mapped.isLetter() && !isLatinVowelOrSpace(mapped)) {
                sb.append(mapped)
            }
        }
        return sb.toString()
    }

    fun matchScore(candidate: String, rawQuery: String): Int {
        val query = rawQuery.trim()
        if (query.isEmpty()) return 0
        if (candidate.isEmpty()) return 0

        val pq = prepareQuery(query)
        val pt = prepareTarget(candidate)
        val t = pt.norm
        val q = pq.norm
        val altRu = pq.altRu
        val altEn = pq.altEn

        return when {
            t == q || t == altRu || t == altEn -> 100
            t.startsWith(q) || t.startsWith(altRu) || t.startsWith(altEn) -> 85
            t.contains(q) || t.contains(altRu) || t.contains(altEn) -> 70
            pt.latinVariants.any { it.startsWith(q) } ||
                pq.latinVariants.any { qLat -> t.startsWith(qLat) } -> 60
            pq.ruVariants.any { qRu -> t.contains(qRu) } ||
                pq.latinVariants.any { qLat ->
                    pt.latinVariants.any { it.contains(qLat) }
                } -> 52
            matchesPrepared(pt, pq) -> 42
            else -> 0
        }
    }
}
