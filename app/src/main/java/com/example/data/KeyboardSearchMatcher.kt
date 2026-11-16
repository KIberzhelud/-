package com.example.data

object KeyboardSearchMatcher {

    private val enToRu = mapOf(
        'q' to 'й', 'w' to 'ц', 'e' to 'у', 'r' to 'к', 't' to 'е', 'y' to 'н',
        'u' to 'г', 'i' to 'ш', 'o' to 'щ', 'p' to 'з', '[' to 'х', ']' to 'ъ',
        'a' to 'ф', 's' to 'ы', 'd' to 'в', 'f' to 'а', 'g' to 'п', 'h' to 'р',
        'j' to 'о', 'k' to 'л', 'l' to 'д', ';' to 'ж', '\'' to 'э',
        'z' to 'я', 'x' to 'ч', 'c' to 'с', 'v' to 'м', 'b' to 'и', 'n' to 'т',
        'm' to 'ь', ',' to 'б', '.' to 'ю'
    )

    private val ruToEn = enToRu.entries.associate { (k, v) -> v to k }

    fun matches(target: String, query: String): Boolean {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return true
        val lowerTarget = target.lowercase()

        if (lowerTarget.contains(cleanQuery)) return true

        val swappedRu = cleanQuery.map { enToRu[it] ?: it }.joinToString("")
        if (swappedRu != cleanQuery && lowerTarget.contains(swappedRu)) return true

        val swappedEn = cleanQuery.map { ruToEn[it] ?: it }.joinToString("")
        if (swappedEn != cleanQuery && lowerTarget.contains(swappedEn)) return true

        return isSubsequence(cleanQuery, lowerTarget)
    }

    fun matchScore(target: String, query: String): Int {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return 0
        val lowerTarget = target.lowercase()
        if (lowerTarget.startsWith(cleanQuery)) return 100
        if (lowerTarget.contains(cleanQuery)) return 75
        val swappedRu = cleanQuery.map { enToRu[it] ?: it }.joinToString("")
        if (lowerTarget.startsWith(swappedRu)) return 90
        if (lowerTarget.contains(swappedRu)) return 65
        val swappedEn = cleanQuery.map { ruToEn[it] ?: it }.joinToString("")
        if (lowerTarget.startsWith(swappedEn)) return 90
        if (lowerTarget.contains(swappedEn)) return 65
        return 30
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        if (query.length > target.length) return false
        var qi = 0
        for (ch in target) {
            if (ch == query[qi]) {
                qi++
                if (qi == query.length) return true
            }
        }
        return false
    }

    val latinAlphabet: List<Char> = ('A'..'Z').toList() + '#'
    val cyrillicAlphabet: List<Char> = listOf(
        'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'З', 'И', 'Й',
        'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С', 'Т', 'У',
        'Ф', 'Х', 'Ц', 'Ч', 'Ш', 'Щ', 'Э', 'Ю', 'Я', '#'
    )
}
