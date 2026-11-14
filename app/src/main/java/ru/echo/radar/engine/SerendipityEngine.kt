package ru.echo.radar.engine

import java.security.MessageDigest
import java.util.Locale
import ru.echo.radar.model.Resonance
import ru.echo.radar.model.Track

/** Pure and deterministic. Missing metadata never becomes a fabricated genre. */
object SerendipityEngine {
    private val aliases = mapOf(
        "электроника" to "electronic", "электронная" to "electronic",
        "джаз" to "jazz", "рок" to "rock", "инди" to "indie",
        "хип-хоп" to "hip hop", "hip-hop" to "hip hop", "рэп" to "hip hop",
        "эмбиент" to "ambient", "классика" to "classical",
    )
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
    private fun genres(value: String): Set<String> = value.split(',', ';', '/')
        .map(::normalize).filter(String::isNotBlank).map { aliases[it] ?: it }.toSet()

    fun resonance(first: Track, second: Track): Resonance {
        val a = genres(first.genre)
        val b = genres(second.genre)
        val shared = a intersect b
        val union = a union b
        val artist = normalize(first.artist) == normalize(second.artist)
        val genreScore = if (union.isEmpty()) 0.0 else shared.size.toDouble() / union.size
        return Resonance(((if (artist) 40 else 0) + 60 * genreScore).toInt(), shared, artist)
    }

    fun archetype(history: List<Track>): String {
        if (history.isEmpty()) return "Слушатель ночного эфира"
        val dominant = history.flatMap { genres(it.genre) }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
        return when (dominant) {
            "ambient" -> "Проводник туманов"
            "jazz" -> "Хранитель полуночных импровизаций"
            "electronic" -> "Картограф ночных частот"
            "rock", "indie" -> "Архивариус забытых кассет"
            "classical" -> "Собиратель тихих созвездий"
            "hip hop" -> "Летописец спящих кварталов"
            else -> {
                val seed = history.map { normalize(it.artist) }.distinct().sorted().joinToString("|")
                val index = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))[0].toInt() and 255
                listOf("Архивариус забытых кассет", "Проводник туманов", "Собиратель городских отзвуков")[index % 3]
            }
        }
    }
}
