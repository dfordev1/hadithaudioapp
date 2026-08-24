package to.hadith.audio

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Network-backed Bukhari catalog. Payloads are intentionally fetched per book/hadith. */
data class BukhariBook(
    val number: Int,
    val count: Int,
    val from: Int,
    val to: Int,
    val title: String = bukhariBookTitle(number),
)

data class CatalogHadith(
    val number: String,
    val book: BukhariBook,
    val isnad: String,
    val words: List<HadithWord>,
) {
    val arabic: String get() = words.map { it.arabic }.joinToString(" ")
    fun asHadithEntry(english: String = "", urdu: String = ""): HadithEntry = HadithEntry(
        collection = "Sahih al-Bukhari", book = "Book ${book.number} · ${book.title}", number = "Hadith $number",
        passageLabel = "Full narration", isnad = isnad, narrator = "",
        arabic = arabic, english = english, urdu = urdu, words = words, durationSeconds = 0,
    )
}

data class CatalogTranslation(val language: String, val hadithNumber: String, val text: String)

/** Playback locations are carried with the selected hadith rather than hard-coded in UI code. */
data class HadithAudioSource(
    val timingUrl: String,
    val audioBaseUrl: String,
    val fallbackAudioUrl: String,
    /** The timing sidecar is the authoritative, fully aligned Arabic stream. */
    val displayTimingTokens: Boolean = false,
)

data class BukhariHadithDetail(val entry: HadithEntry, val audioSource: HadithAudioSource)

fun CatalogHadith.asBukhariDetail(english: String = "", urdu: String = ""): BukhariHadithDetail {
    val file = number.padStart(4, '0')
    val base = "https://pub-4c1d62290e264660b4061d58417926be.r2.dev"
    return BukhariHadithDetail(
        entry = asHadithEntry(english, urdu),
        audioSource = HadithAudioSource(
            timingUrl = "$base/bukhari-timings/n$file.json",
            audioBaseUrl = "$base/bukhari/",
            fallbackAudioUrl = "$base/bukhari/$file.mp3",
            displayTimingTokens = true,
        ),
    )
}

object BukhariCatalogParser {
    fun parseIndex(json: String): List<BukhariBook> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Index is not an object")
        val books = root["books"] as? List<*> ?: error("Index has no books")
        return books.map { row ->
            val value = row as? Map<*, *> ?: error("Invalid book row")
            BukhariBook(value.int("book"), value.int("count"), value.int("from"), value.int("to"))
        }.also { require(it.size == 97 && it.map { b -> b.number } == (1..97).toList()) { "Bukhari index must contain books 1..97" } }
    }

    fun parseReportBook(json: String): Map<String, Int> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Index is not an object")
        return (root["reportBook"] as? Map<*, *>)?.mapNotNull { (key, value) ->
            val book = (value as? Number)?.toInt() ?: return@mapNotNull null
            key.toString() to book
        }?.toMap() ?: error("Index has no reportBook")
    }

    fun parseBook(json: String, book: BukhariBook): List<CatalogHadith> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Book is not an object")
        require((root["book"] as? Number)?.toInt() == book.number) { "Book number mismatch" }
        val rows = root["hadith"] as? List<*> ?: error("Book has no hadith")
        return rows.map { row ->
            val value = row as? Map<*, *> ?: error("Invalid hadith row")
            val number = value["n"]?.toString()?.takeIf(::isValidHadithId) ?: error("Invalid hadith id")
            val tokens = value["tokens"] as? List<*> ?: emptyList<Any?>()
            CatalogHadith(
                number,
                book,
                value["isnad"]?.toString().orEmpty(),
                tokens.mapNotNull { token ->
                    val text = (token as? Map<*, *>)?.get("text")?.toString()?.trim().orEmpty()
                    text.takeIf { it.isNotEmpty() }?.let { HadithWord(it, "", "", null) }
                },
            )
        }
    }

    fun parseTranslation(json: String, language: String, requestedId: String? = null): CatalogTranslation? {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Translation is not an object")
        val rows = root["hadiths"] as? List<*> ?: return null
        return rows.asSequence().mapNotNull { row ->
            val value = row as? Map<*, *> ?: return@mapNotNull null
            val id = value["hadithnumber"]?.toString() ?: return@mapNotNull null
            if (requestedId != null && id != requestedId) return@mapNotNull null
            CatalogTranslation(language, id, value["text"]?.toString().orEmpty())
        }.firstOrNull()
    }

    fun isValidHadithId(id: String): Boolean = Regex("^[0-9]+[a-z]?$", RegexOption.IGNORE_CASE).matches(id) && id.dropLastWhile { it.isLetter() }.toLongOrNull() != null
    fun normalizeHadithId(id: String): String {
        require(isValidHadithId(id)) { "Invalid hadith id" }
        val lowered = id.lowercase()
        val suffix = lowered.lastOrNull()?.takeIf { it.isLetter() }?.toString().orEmpty()
        val digits = lowered.dropLast(suffix.length).toLong().toString()
        return digits + suffix
    }
    private fun Map<*, *>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: error("Missing $key")
}

/** Titles are part of the upstream reader contract but not repeated in index.json. */
internal fun bukhariBookTitle(number: Int): String = BUKHARI_BOOK_TITLES.getOrNull(number - 1)
    ?: "Book $number"

private val BUKHARI_BOOK_TITLES = listOf(
    "Revelation",
    "Belief",
    "Knowledge",
    "Ablutions (Wudu')",
    "Bathing (Ghusl)",
    "Menstrual Periods",
    "Dry Ablution (Tayammum)",
    "Prayers (Salat)",
    "Times of Prayer",
    "Call to Prayer (Adhaan)",
    "Friday Prayer",
    "Fear Prayer",
    "The Two Festivals (Eids)",
    "Witr Prayer",
    "Prayer for Rain (Istisqaa)",
    "Eclipses",
    "Prostration during Qur'an Recital",
    "Shortening the Prayers",
    "Night Prayer (Tahajjud)",
    "Virtues of the Mosques of Makkah and Madinah",
    "Actions while Praying",
    "Forgetfulness in Prayer",
    "Funerals",
    "Zakat",
    "Hajj",
    "Umrah",
    "Pilgrims Prevented from Completing Hajj",
    "Hunting while on Pilgrimage",
    "Virtues of Madinah",
    "Fasting",
    "Night Prayer in Ramadan (Taraweeh)",
    "Virtues of the Night of Qadr",
    "Retreat in the Mosque (I'tikaf)",
    "Sales and Trade",
    "Advance Payment (Salam)",
    "Pre-emption (Shuf'a)",
    "Hiring",
    "Transfer of Debt (Hawala)",
    "Guarantees (Kafalah)",
    "Representation and Agency",
    "Agriculture",
    "Distribution of Water",
    "Loans and Bankruptcy",
    "Disputes",
    "Lost Property",
    "Oppressions",
    "Partnership",
    "Mortgaging",
    "Manumission of Slaves",
    "Contracts of Manumission",
    "Gifts",
    "Witnesses",
    "Peacemaking",
    "Conditions",
    "Wills and Testaments",
    "Striving in Allah's Cause (Jihad)",
    "One-fifth of Booty (Khumus)",
    "Jizyah and Treaties",
    "Beginning of Creation",
    "Prophets",
    "Virtues of the Prophet and Companions",
    "Companions of the Prophet",
    "Merits of the Ansar",
    "Military Expeditions",
    "Prophetic Commentary on the Qur'an",
    "Virtues of the Qur'an",
    "Marriage",
    "Divorce",
    "Supporting the Family",
    "Food and Meals",
    "Aqiqah",
    "Hunting and Slaughtering",
    "Eid Sacrifice",
    "Drinks",
    "Patients",
    "Medicine",
    "Dress",
    "Good Manners (Adab)",
    "Asking Permission",
    "Supplications",
    "Heart-Softening Narrations (Riqaq)",
    "Divine Will (Qadar)",
    "Oaths and Vows",
    "Expiation for Broken Oaths",
    "Inheritance",
    "Legal Punishments",
    "Blood Money",
    "Apostates",
    "Coercion",
    "Legal Stratagems",
    "Interpretation of Dreams",
    "Afflictions and the End of the World",
    "Judgments",
    "Wishes",
    "Accepting Reliable Reports",
    "Holding Fast to Qur'an and Sunnah",
    "Oneness of Allah (Tawheed)",
)

class BukhariCatalogRepository(
    private val getText: suspend (String) -> String = ::fetchCatalogText,
) {
    private var index: List<BukhariBook>? = null
    private var reportBook: Map<String, Int>? = null
    private val bookCache = mutableMapOf<Int, List<CatalogHadith>>()

    private suspend fun ensureIndex() {
        if (index != null && reportBook != null) return
        val body = getText(INDEX_URL)
        index = BukhariCatalogParser.parseIndex(body)
        reportBook = BukhariCatalogParser.parseReportBook(body)
    }

    suspend fun books(): List<BukhariBook> {
        ensureIndex()
        return checkNotNull(index)
    }

    suspend fun findBookForHadith(id: String): BukhariBook {
        require(BukhariCatalogParser.isValidHadithId(id)) { "Invalid hadith id" }
        val normalizedId = BukhariCatalogParser.normalizeHadithId(id)
        ensureIndex()
        val mapped = checkNotNull(reportBook)
        val number = mapped[normalizedId]
            ?: throw NoSuchElementException("Unknown Bukhari hadith $normalizedId")
        return books().first { it.number == number }
    }

    suspend fun hadiths(book: Int): List<CatalogHadith> {
        require(book in 1..97) { "Bukhari book must be 1..97" }
        return bookCache[book] ?: getText("$BASE_URL/book-$book.json").let {
            BukhariCatalogParser.parseBook(it, books().first { b -> b.number == book }).also { parsed ->
                bookCache[book] = parsed
            }
        }
    }

    suspend fun hadith(id: String): CatalogHadith {
        val normalizedId = BukhariCatalogParser.normalizeHadithId(id)
        val book = findBookForHadith(normalizedId)
        return hadiths(book.number).firstOrNull { it.number == normalizedId }
            ?: throw NoSuchElementException("Hadith $normalizedId is not in book ${book.number}")
    }

    suspend fun translation(id: String, language: String): CatalogTranslation? {
        require(language == "eng" || language == "urd") { "Language must be eng or urd" }
        require(BukhariCatalogParser.isValidHadithId(id)) { "Invalid hadith id" }
        val normalizedId = BukhariCatalogParser.normalizeHadithId(id)
        return getText("$TRANSLATION_BASE/${language}-bukhari/$normalizedId.json").let {
            BukhariCatalogParser.parseTranslation(it, language, normalizedId)
        }
    }

    companion object {
        const val BASE_URL = "https://www.hadith.to/bukhari"
        const val INDEX_URL = "$BASE_URL/index.json"
        const val TRANSLATION_BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions"
    }
}

internal suspend fun fetchCatalogText(url: String): String = withContext(Dispatchers.IO) {
    validateCatalogUrl(url)
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000; connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        if (connection.responseCode !in 200..299) throw IOException("Catalog request failed: ${connection.responseCode}")
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally { connection.disconnect() }
}

internal fun validateCatalogUrl(value: String): String {
    val uri = URI(value)
    require(uri.scheme == "https" && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Only trusted HTTPS catalog URLs are allowed" }
    require(uri.host in setOf("www.hadith.to", "hadith.to", "cdn.jsdelivr.net")) { "Untrusted catalog host" }
    return value
}

/** Tiny JSON parser to keep the catalog dependency-free and unit-testable on the JVM. */
private object JsonLite {
    fun parse(input: String): Any? = Reader(input).parse()
    private class Reader(private val s: String) { var i = 0
        fun parse(): Any? { skip(); val v = value(); skip(); require(i == s.length); return v }
        private fun value(): Any? { skip(); return when (s[i]) { '{' -> obj(); '[' -> array(); '"' -> string(); 't' -> literal("true", true); 'f' -> literal("false", false); 'n' -> literal("null", null); else -> number() } }
        private fun obj(): Map<String, Any?> { i++; val out = linkedMapOf<String, Any?>(); skip(); if (s[i] == '}') { i++; return out }; while (true) { skip(); val k = string(); skip(); require(s[i++] == ':'); out[k] = value(); skip(); if (s[i] == '}') { i++; return out }; require(s[i++] == ',') } }
        private fun array(): List<Any?> { i++; val out = mutableListOf<Any?>(); skip(); if (s[i] == ']') { i++; return out }; while (true) { out += value(); skip(); if (s[i] == ']') { i++; return out }; require(s[i++] == ',') } }
        private fun string(): String { require(s[i++] == '"'); val out = StringBuilder(); while (true) { val c = s[i++]; if (c == '"') return out.toString(); if (c == '\\') { val e = s[i++]; out.append(when (e) { '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'u' -> s.substring(i, i + 4).also { i += 4 }.toInt(16).toChar(); else -> error("Bad escape") }) } else out.append(c) } }
        private fun literal(text: String, result: Any?): Any? { require(s.startsWith(text, i)); i += text.length; return result }
        private fun number(): Number { val start = i; while (i < s.length && s[i] !in ",]} \t\r\n") i++; val n = s.substring(start, i); return n.toLongOrNull() ?: n.toDouble() }
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
    }
}
