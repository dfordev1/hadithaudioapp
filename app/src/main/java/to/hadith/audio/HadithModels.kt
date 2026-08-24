package to.hadith.audio

import java.text.Normalizer

/** Small, deliberately local model for the first offline UI slice. */
data class HadithWord(
    val arabic: String,
    val transliteration: String,
    val gloss: String,
    /** Null for words which do not have a lexical root, such as particles. */
    val root: String?,
    val grammaticalCategory: String? = null,
)

data class HadithEntry(
    val collection: String,
    val book: String,
    val number: String,
    val passageLabel: String,
    val isnad: String,
    val narrator: String,
    val arabic: String,
    val english: String,
    val urdu: String,
    val words: List<HadithWord>,
    val durationSeconds: Int,
)

/** A word-level timing record as returned by the Hadith.to timing endpoint. */
data class TimingToken(
    val text: String,
    val startSeconds: Float,
    val endSeconds: Float,
    val displayStartSeconds: Float? = null,
    val displayEndSeconds: Float? = null,
)

data class HadithTiming(
    val audioUrl: String,
    val durationSeconds: Float,
    val tokens: List<TimingToken>,
    val isSynthetic: Boolean = false,
)

/** A locally displayed word matched to one exact timing token. */
data class WordTiming(
    val wordIndex: Int,
    val tokenIndex: Int,
    val startSeconds: Float,
    val endSeconds: Float,
)

/**
 * Normalizes Arabic for matching only. The original text is never changed or displayed.
 * Harakat, tatweel and punctuation are ignored; common alef forms are folded together.
 */
fun normalizeArabicForTiming(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace("ـ", "")
        .replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('ٱ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .filter { it.isLetterOrDigit() }

/**
 * Matches displayed words to timing tokens in order. Unrelated tokens (for example an
 * introduction or punctuation-only token) may be skipped, but a matched word always
 * retains the exact start/end values from its source token.
 */
fun matchTimingTokens(words: List<String>, tokens: List<TimingToken>): List<WordTiming> {
    if (words.isEmpty() || tokens.isEmpty()) return emptyList()
    val matches = mutableListOf<WordTiming>()
    var tokenCursor = 0
    words.forEachIndexed { wordIndex, word ->
        val normalizedWord = normalizeArabicForTiming(word)
        if (normalizedWord.isEmpty()) return@forEachIndexed
        val tokenIndex = tokens.indexOfFirstFrom(tokenCursor) { token ->
            normalizeArabicForTiming(token.text) == normalizedWord
        }
        if (tokenIndex >= 0) {
            val token = tokens[tokenIndex]
            matches += WordTiming(
                wordIndex = wordIndex,
                tokenIndex = tokenIndex,
                startSeconds = token.displayStartSeconds ?: token.startSeconds,
                endSeconds = token.displayEndSeconds ?: token.endSeconds,
            )
            tokenCursor = tokenIndex + 1
        }
    }
    return matches
}

private inline fun <T> List<T>.indexOfFirstFrom(
    startIndex: Int,
    predicate: (T) -> Boolean,
): Int {
    for (index in startIndex.coerceAtLeast(0) until size) {
        if (predicate(this[index])) return index
    }
    return -1
}

/** Finds the exact displayed word whose timing interval contains [positionSeconds]. */
fun wordIndexAtTiming(positionSeconds: Float, timings: List<WordTiming>): Int {
    if (!positionSeconds.isFinite()) return -1
    return timings.firstOrNull { positionSeconds >= it.startSeconds && positionSeconds < it.endSeconds }
        ?.wordIndex
        ?: timings.lastOrNull { positionSeconds >= it.endSeconds }?.wordIndex
        ?: -1
}

val FirstHadith = HadithEntry(
    collection = "Sahih al-Bukhari",
    book = "Book 1 · Revelation",
    number = "Hadith 1",
    passageLabel = "Opening passage",
    isnad = "الْحُمَيْدِيُّ عَبْدُ اللَّهِ بْنُ الزُّبَيْرِ ← سُفْيَانُ ← يَحْيَى بْنُ سَعِيدٍ الْأَنْصَارِيُّ ← مُحَمَّدُ بْنُ إِبْرَاهِيمَ التَّيْمِيُّ",
    narrator = "ʿUmar ibn al-Khattab",
    arabic = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى",
    english = "Actions are but by intentions, and every person shall have only that which they intended.",
    urdu = "اعمال کا دار و مدار نیتوں پر ہے، اور ہر شخص کو وہی ملے گا جس کی اس نے نیت کی۔",
    words = listOf(
        HadithWord("إِنَّمَا", "innamā", "only; indeed", null, "Particle"),
        HadithWord("الأَعْمَالُ", "al-aʿmāl", "the actions", "ع م ل"),
        HadithWord("بِالنِّيَّاتِ", "bin-niyyāt", "by the intentions", "ن و ي"),
        HadithWord("وَإِنَّمَا", "wa-innamā", "and only", null, "Particle"),
        HadithWord("لِكُلِّ", "li-kulli", "for every", "ك ل ل"),
        HadithWord("امْرِئٍ", "imriʾin", "person", "م ر أ"),
        HadithWord("مَا", "mā", "what; whatever", null, "Relative pronoun"),
        HadithWord("نَوَى", "nawā", "he intended", "ن و ي"),
    ),
    durationSeconds = 38,
)

data class LibraryCollection(
    val title: String,
    val subtitle: String,
    val status: String,
    val isAvailable: Boolean,
)

val HadithCollections = listOf(
    LibraryCollection("Sahih al-Bukhari", "97 books · 7,580 narrations", "Open", true),
    LibraryCollection("Sahih Muslim", "Collection adapter planned", "Coming next", false),
    LibraryCollection("Sunan Abi Dawud", "Collection adapter planned", "Coming next", false),
)

val HadithEntry.stableKey: String get() = "$collection|$number"

interface HadithRepository {
    fun all(): List<HadithEntry>
    fun search(query: String): List<HadithEntry>
}

class LocalHadithRepository(
    private val entries: List<HadithEntry> = listOf(FirstHadith),
) : HadithRepository {
    override fun all(): List<HadithEntry> = entries

    override fun search(query: String): List<HadithEntry> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return entries
        return entries.filter { entry ->
            sequenceOf(entry.collection, entry.book, entry.number, entry.arabic, entry.english, entry.urdu)
                .any { it.contains(normalized, ignoreCase = true) }
        }
    }
}

/** Returns the uniformly mapped word index, or -1 when the inputs cannot be mapped. */
fun wordIndexAtTime(positionSeconds: Float, durationSeconds: Int, wordCount: Int): Int {
    if (!positionSeconds.isFinite() || durationSeconds <= 0 || wordCount <= 0) return -1
    val position = positionSeconds.coerceIn(0f, durationSeconds.toFloat())
    return ((position / durationSeconds) * wordCount).toInt().coerceIn(0, wordCount - 1)
}

/** Returns the start time for a word in a uniformly mapped recording, or 0 when invalid. */
fun seekTimeForWord(wordIndex: Int, durationSeconds: Int, wordCount: Int): Float {
    if (wordIndex < 0 || durationSeconds <= 0 || wordCount <= 0) return 0f
    return (wordIndex.coerceAtMost(wordCount - 1).toFloat() / wordCount) * durationSeconds
}
