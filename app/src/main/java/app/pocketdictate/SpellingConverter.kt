package app.pocketdictate

import android.content.Context
import java.io.File

/**
 * Converts US spellings to Australian ones using a plain-text word list.
 *
 * The default list ships in assets/au_spelling.txt. On first use it is copied
 * to the app's private storage, where the in-app editor (Settings > Edit word
 * list) can change it. Format, one rule per line:
 *
 *     color > colour      US word, then the Australian word
 *     !Labor              never change this exact word (case-sensitive)
 *     # comment
 */
class SpellingConverter private constructor(
    private val map: Map<String, String>,
    private val keep: Set<String>,
) {
    fun convert(text: String): String = WORD.replace(text) { m ->
        val word = m.value
        if (word in keep) return@replace word
        val replacement = map[word.lowercase()] ?: return@replace word
        matchCase(word, replacement)
    }

    companion object {
        const val FILE_NAME = "au_spelling.txt"
        private val WORD = Regex("[A-Za-z]+")
        private val SEPARATOR = Regex("\\s*(?:>|->|=>|=|\\s)\\s*")

        @Volatile private var cached: SpellingConverter? = null
        @Volatile private var cachedStamp = -1L

        fun userFile(c: Context) = File(c.filesDir, FILE_NAME)

        fun defaultText(c: Context): String =
            c.assets.open(FILE_NAME).bufferedReader().use { it.readText() }

        fun readText(c: Context): String {
            val f = userFile(c)
            if (!f.exists()) f.writeText(defaultText(c))
            return f.readText()
        }

        fun save(c: Context, text: String) {
            userFile(c).writeText(text)
            cached = null
        }

        fun resetToDefault(c: Context) = save(c, defaultText(c))

        /** Loads (and caches) the current list, re-reading it after edits. */
        fun get(c: Context): SpellingConverter {
            val text = readText(c)
            val stamp = userFile(c).lastModified() xor text.length.toLong()
            cached?.let { if (cachedStamp == stamp) return it }
            return parse(text).also { cached = it; cachedStamp = stamp }
        }

        fun parse(text: String): SpellingConverter {
            val map = HashMap<String, String>()
            val keep = HashSet<String>()
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                if (line.startsWith("!")) {
                    keep += line.removePrefix("!").trim(); continue
                }
                val parts = line.split(SEPARATOR, limit = 2)
                if (parts.size != 2) continue
                val from = parts[0].trim().lowercase()
                val to = parts[1].trim()
                if (from.isNotEmpty() && to.isNotEmpty() && from.all { it.isLetter() }) map[from] = to
            }
            return SpellingConverter(map, keep)
        }

        private fun matchCase(original: String, replacement: String): String = when {
            original.length > 1 && original.all { it.isUpperCase() } -> replacement.uppercase()
            original[0].isUpperCase() -> replacement.replaceFirstChar { it.uppercaseChar() }
            else -> replacement
        }
    }
}
