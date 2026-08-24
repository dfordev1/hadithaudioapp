package to.hadith.audio

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** A collection is a routing contract; payloads are fetched only when selected. */
data class CatalogCollection(
    val slug: String,
    val title: String,
    val bookCount: Int? = null,
    val totalCount: Int? = null,
    val kind: Kind = Kind.BOOKS,
    val translationSlug: String? = slug,
    val audioSlug: String? = slug,
    val corpusSlug: String? = null,
) { enum class Kind { BOOKS, FORTY, MUSNAD } }

data class CatalogBook(
    val number: Int,
    val count: Int,
    val from: Int,
    val to: Int,
    val title: String = bukhariBookTitle(number),
    val collection: CatalogCollection = CATALOG_COLLECTIONS.first(),
)
typealias BukhariBook = CatalogBook

data class CatalogRef(val collectionSlug: String, val hadithNumber: String) {
    init { require(CatalogParser.isValidHadithId(hadithNumber)) { "Invalid hadith id" } }
    val normalizedNumber: String get() = CatalogParser.normalizeHadithId(hadithNumber)
}

data class CatalogHadith(
    val number: String,
    val book: CatalogBook,
    val isnad: String,
    val words: List<HadithWord>,
) {
    val arabic: String get() = words.map { it.arabic }.joinToString(" ")
    fun asHadithEntry(english: String = "", urdu: String = ""): HadithEntry = HadithEntry(
        collection = book.collection.title,
        book = when (book.collection.kind) {
            CatalogCollection.Kind.MUSNAD -> "Part ${book.number}"
            CatalogCollection.Kind.FORTY -> "Complete collection"
            CatalogCollection.Kind.BOOKS -> if (book.title == "Book ${book.number}") {
                "Book ${book.number}"
            } else {
                "Book ${book.number} · ${book.title}"
            }
        }, number = "Hadith $number",
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
    /** Overrides a legacy timing manifest which does not disclose synthetic speech. */
    val syntheticOverride: Boolean? = null,
)

data class CatalogHadithDetail(val entry: HadithEntry, val audioSource: HadithAudioSource)
typealias BukhariHadithDetail = CatalogHadithDetail

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

fun CatalogHadith.asCatalogDetail(english: String = "", urdu: String = ""): CatalogHadithDetail {
    val c = book.collection
    val width = when (c.kind) {
        CatalogCollection.Kind.MUSNAD -> 5
        CatalogCollection.Kind.FORTY -> 3
        CatalogCollection.Kind.BOOKS -> 4
    }
    val file = CatalogParser.normalizeHadithId(number).padStart(width, '0')
    val base = "https://pub-4c1d62290e264660b4061d58417926be.r2.dev"
    val source = when (c.kind) {
        CatalogCollection.Kind.MUSNAD -> HadithAudioSource(
            timingUrl = "https://cdn.hadith.to/musnad-ahmad-timings/r$file.json",
            audioBaseUrl = "https://cdn.hadith.to/musnad-ahmad/",
            fallbackAudioUrl = "https://cdn.hadith.to/musnad-ahmad/r$file.ogg",
            displayTimingTokens = true,
        )
        CatalogCollection.Kind.FORTY -> HadithAudioSource(
            timingUrl = "https://hadith.to/recitation/${c.corpusSlug ?: c.audioSlug ?: c.slug}.$file.json",
            audioBaseUrl = "https://cdn.hadith.to/recitation/",
            fallbackAudioUrl = "https://cdn.hadith.to/recitation/${c.corpusSlug ?: c.audioSlug ?: c.slug}.$file.mp3",
            displayTimingTokens = true,
            syntheticOverride = true,
        )
        CatalogCollection.Kind.BOOKS -> requireNotNull(c.audioSlug) {
            "${c.title} has no audio routing slug"
        }.let { slug ->
            HadithAudioSource(
                timingUrl = "$base/$slug-timings/n$file.json",
                audioBaseUrl = "$base/$slug/",
                fallbackAudioUrl = "$base/$slug/$file.mp3",
                displayTimingTokens = true,
            )
        }
    }
    return CatalogHadithDetail(asHadithEntry(english, urdu), source)
}

object CatalogParser {
    fun parseIndex(json: String, collection: CatalogCollection = CATALOG_COLLECTIONS.first()): List<CatalogBook> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Index is not an object")
        val books = root["books"] as? List<*> ?: error("Index has no books")
        return books.map { row ->
            val value = row as? Map<*, *> ?: error("Invalid book row")
            CatalogBook(value.int("book"), value.int("count"), value.int("from"), value.int("to"),
                title = catalogBookTitle(collection.slug, value.int("book")), collection = collection)
        }.also { require(it.isNotEmpty()) { "Index has no books" } }
    }

    fun parseReportBook(json: String): Map<String, Int> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Index is not an object")
        return (root["reportBook"] as? Map<*, *>)?.mapNotNull { (key, value) ->
            val book = (value as? Number)?.toInt() ?: return@mapNotNull null
            val id = key.toString().takeIf(::isValidHadithId)?.let(::normalizeHadithId)
                ?: return@mapNotNull null
            id to book
        }?.toMap() ?: emptyMap()
    }

    fun parseBook(json: String, book: CatalogBook): List<CatalogHadith> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Book is not an object")
        require((root["book"] as? Number)?.toInt() == book.number) { "Book number mismatch" }
        val rows = root["hadith"] as? List<*> ?: error("Book has no hadith")
        return rows.map { row ->
            val value = row as? Map<*, *> ?: error("Invalid hadith row")
            val number = value["n"]?.toString()?.takeIf(::isValidHadithId)?.let(::normalizeHadithId)
                ?: error("Invalid hadith id")
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
            val id = value["hadithnumber"]?.toString()?.takeIf(::isValidHadithId)?.let(::normalizeHadithId)
                ?: return@mapNotNull null
            if (requestedId != null && id != normalizeHadithId(requestedId)) return@mapNotNull null
            CatalogTranslation(language, id, value["text"]?.toString().orEmpty())
        }.firstOrNull()
    }

    fun parseDetail(json: String, book: CatalogBook, id: String): CatalogHadith {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Detail is not an object")
        val tokens = (root["tokens"] as? List<*>) ?: (root["hadith"] as? List<*>)?.firstOrNull()?.let { (it as? Map<*, *>)?.get("tokens") as? List<*> } ?: emptyList()
        return CatalogHadith(id, book, root["isnad"]?.toString().orEmpty(), tokens.mapNotNull { token ->
            (token as? Map<*, *>)?.get("text")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { HadithWord(it, "", "", null) }
        })
    }

    fun parseBulkTranslations(json: String, language: String, collectionSlug: String): Map<String, CatalogTranslation> {
        val root = JsonLite.parse(json) as? Map<*, *> ?: error("Translation is not an object")
        val rows = root["translations"] as? List<*> ?: return emptyMap()
        return rows.mapNotNull { row ->
            val value = row as? Map<*, *> ?: return@mapNotNull null
            val id = (value["reportNumber"] ?: value["n"])?.toString()?.let { normalizeHadithId(it) } ?: return@mapNotNull null
            val text = if (collectionSlug == "riyad" && language == "urd") {
                value["ur"]?.toString().orEmpty()
            } else {
                (value["full"] ?: value["en"] ?: value["matn"])?.toString().orEmpty()
            }
            id to CatalogTranslation(language, id, text)
        }.toMap()
    }

    fun isValidHadithId(id: String): Boolean = Regex("^[0-9]+[a-z]*$", RegexOption.IGNORE_CASE).matches(id) && id.dropLastWhile { it.isLetter() }.toLongOrNull() != null
    fun normalizeHadithId(id: String): String {
        require(isValidHadithId(id)) { "Invalid hadith id" }
        val lowered = id.lowercase()
        val suffix = lowered.takeLastWhile { it.isLetter() }
        val digits = lowered.dropLast(suffix.length).toLong().toString()
        return digits + suffix
    }
    private fun Map<*, *>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: error("Missing $key")
}

val CatalogCollections = listOf(
    CatalogCollection("bukhari", "Sahih al-Bukhari", 97, 7580),
    CatalogCollection("muslim", "Sahih Muslim", 56, 7357),
    CatalogCollection("abudawud", "Sunan Abi Dawud", null, 5274),
    CatalogCollection("tirmidhi", "Jami` at-Tirmidhi", null, 3924),
    CatalogCollection("nasai", "Sunan an-Nasa'i", null, 5679),
    CatalogCollection("ibnmajah", "Sunan Ibn Majah", 38, 4338),
    CatalogCollection("malik", "Muwatta Malik", null, 1829),
    CatalogCollection("riyad", "Riyad as-Salihin", null, 1896, translationSlug = "riyad"),
    CatalogCollection("musnad-ahmad", "Musnad Ahmad", null, 27648, CatalogCollection.Kind.MUSNAD, null, "musnad-ahmad"),
    CatalogCollection("nawawi40", "Nawawi's Forty", kind = CatalogCollection.Kind.FORTY, totalCount = 42, translationSlug = "nawawi40", audioSlug = "nawawi", corpusSlug = "nawawi-arbain"),
    CatalogCollection("qudsi40", "Forty Hadith Qudsi", kind = CatalogCollection.Kind.FORTY, totalCount = 40, translationSlug = "qudsi40", audioSlug = "qudsi", corpusSlug = "qudsi-arbain"),
    CatalogCollection("shahwaliullah40", "Shah Waliullah's Forty", kind = CatalogCollection.Kind.FORTY, totalCount = 40, translationSlug = "shahwaliullah40", audioSlug = "shahwaliullah", corpusSlug = "shahwaliullah-arbain"),
)
private val CATALOG_COLLECTIONS: List<CatalogCollection> get() = CatalogCollections

private fun fortyCorpus(collection: CatalogCollection): String = collection.corpusSlug ?: collection.slug

fun catalogBookTitle(collectionSlug: String, number: Int): String =
    if (collectionSlug == "bukhari") bukhariBookTitle(number)
    else nonBukhariBookTitle(collectionSlug, number) ?: "Book $number"

/** Source-compatible facade for the original Bukhari-only API. */
object BukhariCatalogParser {
    fun parseIndex(json: String): List<BukhariBook> = CatalogParser.parseIndex(json).also {
        require(it.size == 97 && it.map { b -> b.number } == (1..97).toList()) { "Bukhari index must contain books 1..97" }
    }
    fun parseReportBook(json: String): Map<String, Int> = CatalogParser.parseReportBook(json)
    fun parseBook(json: String, book: BukhariBook): List<CatalogHadith> = CatalogParser.parseBook(json, book)
    fun parseTranslation(json: String, language: String, requestedId: String? = null) = CatalogParser.parseTranslation(json, language, requestedId)
    fun isValidHadithId(id: String) = CatalogParser.isValidHadithId(id)
    fun normalizeHadithId(id: String) = CatalogParser.normalizeHadithId(id)
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

class HadithCatalogRepository(
    private val getText: suspend (String) -> String = ::fetchCatalogText,
    private val collections: List<CatalogCollection> = CATALOG_COLLECTIONS,
) {
    private val indexes = mutableMapOf<String, List<CatalogBook>>()
    private val reports = mutableMapOf<String, Map<String, Int>>()
    private val bookCache = mutableMapOf<Pair<String, Int>, List<CatalogHadith>>()
    private val translationCache = mutableMapOf<Pair<String, String>, Map<String, CatalogTranslation>>()
    private val bulkTranslationMutex = Mutex()
    private fun spec(slug: String) = collections.firstOrNull { it.slug == slug }
        ?: throw IllegalArgumentException("Unknown collection $slug")

    suspend fun books(collectionSlug: String): List<CatalogBook> {
        val c = spec(collectionSlug)
        if (c.kind == CatalogCollection.Kind.FORTY) return listOf(CatalogBook(1, c.totalCount ?: 40, 1, c.totalCount ?: 40, "Narrations", c))
        return indexes[c.slug] ?: getText("https://www.hadith.to/${c.slug}/index.json").let { body ->
            CatalogParser.parseIndex(body, c).also { parsed ->
                indexes[c.slug] = parsed
                reports[c.slug] = CatalogParser.parseReportBook(body)
            }
        }
    }

    suspend fun hadiths(collectionSlug: String, book: Int): List<CatalogHadith> {
        val c = spec(collectionSlug)
        if (c.kind == CatalogCollection.Kind.FORTY) {
            require(book == 1) { "Forty collections have one virtual book" }
            val total = c.totalCount ?: 40
            val metadata = CatalogBook(1, total, 1, total, "Complete collection", c)
            return (1..total).map { CatalogHadith(it.toString(), metadata, "", emptyList()) }
        }
        val metadata = books(c.slug).firstOrNull { it.number == book }
            ?: throw NoSuchElementException("Unknown $collectionSlug book $book")
        val key = c.slug to book
        return bookCache[key] ?: getText("https://www.hadith.to/${c.slug}/book-$book.json").let { body ->
            CatalogParser.parseBook(body, metadata).also { bookCache[key] = it }
        }
    }

    suspend fun hadith(ref: CatalogRef): CatalogHadith {
        val c = spec(ref.collectionSlug)
        val id = ref.normalizedNumber
        if (c.kind == CatalogCollection.Kind.FORTY) {
            val n = id.toIntOrNull() ?: throw NoSuchElementException("Unknown ${c.slug} hadith $id")
            if (n !in 1..(c.totalCount ?: 40)) {
                throw NoSuchElementException("Unknown ${c.title} hadith $id")
            }
            val book = CatalogBook(1, c.totalCount ?: 40, 1, c.totalCount ?: 40, "Narrations", c)
            val body = getText("https://hadith.to/recitation/${fortyCorpus(c)}.${n.toString().padStart(3, '0')}.json")
            return CatalogParser.parseDetail(body, book, id)
        }
        val index = books(c.slug)
        val bookNumber = reports[c.slug]?.get(id) ?: index.firstOrNull { id.toLongOrNull()?.let { n -> n in it.from..it.to } == true }?.number
            ?: throw NoSuchElementException("Unknown ${c.title} hadith $id")
        return hadiths(c.slug, bookNumber).firstOrNull { it.number == id }
            ?: throw NoSuchElementException("Hadith $id is not in book $bookNumber")
    }

    suspend fun translation(ref: CatalogRef, language: String): CatalogTranslation? {
        require(language == "eng" || language == "urd") { "Language must be eng or urd" }
        val c = spec(ref.collectionSlug); val id = ref.normalizedNumber
        if (c.kind == CatalogCollection.Kind.FORTY && language != "eng") return null
        val slug = c.translationSlug ?: return null
        val key = c.slug to language
        if (c.kind == CatalogCollection.Kind.FORTY) {
            val values = translationCache[key] ?: CatalogParser.parseBulkTranslations(
                getText("https://www.hadith.to/translation/$slug.json"), language, c.slug,
            ).also { translationCache[key] = it }
            return values[id]
        }
        if (c.slug == "riyad") {
            bulkTranslationMutex.lock()
            try {
                if (translationCache[c.slug to "eng"] == null || translationCache[c.slug to "urd"] == null) {
                    val body = getText("https://www.hadith.to/translation/$slug.json")
                    translationCache[c.slug to "eng"] = CatalogParser.parseBulkTranslations(body, "eng", c.slug)
                    translationCache[c.slug to "urd"] = CatalogParser.parseBulkTranslations(body, "urd", c.slug)
                }
            } finally {
                bulkTranslationMutex.unlock()
            }
            return translationCache[key]?.get(id)
        }
        val url = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/${language}-$slug/$id.json"
        return CatalogParser.parseTranslation(getText(url), language, id)
    }
}

class BukhariCatalogRepository(private val getText: suspend (String) -> String = ::fetchCatalogText) {
    private val delegate = HadithCatalogRepository(getText, listOf(CATALOG_COLLECTIONS.first()))
    suspend fun books(): List<BukhariBook> = delegate.books("bukhari")
    suspend fun findBookForHadith(id: String): BukhariBook {
        return delegate.hadith(CatalogRef("bukhari", id)).book
    }
    suspend fun hadiths(book: Int) = delegate.hadiths("bukhari", book)
    suspend fun hadith(id: String) = delegate.hadith(CatalogRef("bukhari", id))
    suspend fun translation(id: String, language: String) = delegate.translation(CatalogRef("bukhari", id), language)
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
    require(uri.host in setOf("www.hadith.to", "hadith.to", "cdn.jsdelivr.net", "cdn.hadith.to", "pub-4c1d62290e264660b4061d58417926be.r2.dev")) { "Untrusted catalog host" }
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
