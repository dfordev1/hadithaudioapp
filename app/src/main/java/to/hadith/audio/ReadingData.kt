package to.hadith.audio

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

enum class ReadingAppearance(val label: String) { LIGHT("Light"), SEPIA("Sepia"), DARK("Dark") }
enum class ArabicTypeface(val label: String) { SCHEHERAZADE("Scheherazade New"), AMIRI("Amiri"), NASKH("Noto Naskh") }
enum class ReadingLanguage(val label: String) { ENGLISH("English"), URDU("اردو"), BOTH("English & اردو") }

data class ReadingSettings(
    val appearance: ReadingAppearance = ReadingAppearance.LIGHT,
    val typeface: ArabicTypeface = ArabicTypeface.SCHEHERAZADE,
    val arabicSize: Float = 32f,
    val translationSize: Float = 18f,
    val lineSpacing: Float = 1.8f,
    val wordSpacing: Float = 2f,
    val language: ReadingLanguage = ReadingLanguage.ENGLISH,
    val wordMeanings: Boolean = true,
    val speed: Float = 1f,
    val repeat: Boolean = false,
    val autoplay: Boolean = true,
    val wifiOnly: Boolean = true,
) {
    fun constrained() = copy(
        arabicSize = arabicSize.finiteOr(32f).coerceIn(24f, 44f),
        translationSize = translationSize.finiteOr(18f).coerceIn(14f, 26f),
        lineSpacing = lineSpacing.finiteOr(1.8f).coerceIn(1.4f, 2.3f),
        wordSpacing = wordSpacing.finiteOr(2f).coerceIn(0f, 12f),
        speed = speed.finiteOr(1f).coerceIn(.75f, 2f),
    )
}

private fun Float.finiteOr(fallback: Float) = takeIf { it.isFinite() } ?: fallback

data class ReadingRecord(val ref: CatalogRef, val entry: HadithEntry, val source: HadithAudioSource) {
    val key: String get() = ref.key
}
data class RecentReading(val ref: CatalogRef, val openedAt: Long, val position: Float = 0f)
data class SavedWord(val ref: CatalogRef, val word: HadithWord) {
    val key: String get() = "${ref.key}:${word.arabic}"
}
data class ReadingLibrary(
    val saved: List<CatalogRef> = emptyList(),
    val recent: List<RecentReading> = emptyList(),
    val words: List<SavedWord> = emptyList(),
    val settings: ReadingSettings = ReadingSettings(),
)
val CatalogRef.key: String get() = "$collectionSlug:${normalizedNumber}"
val CatalogRef.title: String get() = CatalogCollections.firstOrNull { it.slug == collectionSlug }?.title ?: collectionSlug
val CatalogRef.url: String get() = "https://www.hadith.to/#${collectionSlug}:${normalizedNumber}"

internal fun canonicalRef(slug: String, number: String): CatalogRef {
    require(CatalogCollections.any { it.slug == slug }) { "Unknown collection" }
    return CatalogRef(slug, CatalogParser.normalizeHadithId(number))
}

internal fun ReadingLibrary.toggleSaved(ref: CatalogRef): ReadingLibrary {
    val value = canonicalRef(ref.collectionSlug, ref.hadithNumber)
    return copy(saved = if (saved.any { it.key == value.key }) saved.filterNot { it.key == value.key }
        else listOf(value) + saved)
}
internal fun ReadingLibrary.opened(ref: CatalogRef, now: Long): ReadingLibrary {
    val value = canonicalRef(ref.collectionSlug, ref.hadithNumber)
    val position = recent.firstOrNull { it.ref.key == value.key }?.position ?: 0f
    return copy(recent = (listOf(RecentReading(value, now, position)) + recent.filterNot { it.ref.key == value.key }).take(100))
}

/** JSON is escaped, never interpolated into UI markup; Arabic is stored verbatim. */
internal fun encodeJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> buildString {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u${c.code.toString(16).padStart(4, '0')}") else append(c)
        } }
        append('"')
    }
    is Boolean -> value.toString()
    is Number -> value.toDouble().takeIf { it.isFinite() }?.let { value.toString() } ?: "null"
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { encodeJson(it.key.toString()) + ":" + encodeJson(it.value) }
    is Iterable<*> -> value.joinToString(",", "[", "]") { encodeJson(it) }
    else -> error("Unsupported JSON value")
}

internal object ReadingCodec {
    private fun CatalogRef.json() = mapOf("slug" to collectionSlug, "n" to normalizedNumber)
    private fun ref(value: Any?): CatalogRef {
        val m = value as Map<*, *>
        return canonicalRef(m["slug"] as String, m["n"] as String)
    }
    private fun word(value: HadithWord) = mapOf("ar" to value.arabic, "tr" to value.transliteration, "gloss" to value.gloss,
        "root" to value.root, "category" to value.grammaticalCategory, "ur" to value.urduGloss)
    private fun word(value: Map<*, *>) = HadithWord(value.text("ar"), value.text("tr"), value.text("gloss"),
        value["root"] as? String, value["category"] as? String, value.text("ur"))
    fun record(value: ReadingRecord): String = encodeJson(mapOf(
        "version" to 1, "ref" to value.ref.json(), "entry" to value.entry.let { e -> mapOf(
            "collection" to e.collection, "book" to e.book, "number" to e.number, "label" to e.passageLabel,
            "isnad" to e.isnad, "narrator" to e.narrator, "ar" to e.arabic, "en" to e.english, "ur" to e.urdu,
            "words" to e.words.map(::word), "duration" to e.durationSeconds,
        ) }, "source" to value.source.let { s -> mapOf("timing" to s.timingUrl, "base" to s.audioBaseUrl,
            "audio" to s.fallbackAudioUrl, "tokens" to s.displayTimingTokens, "synthetic" to s.syntheticOverride) },
    ))
    fun record(json: String): ReadingRecord {
        val m = JsonLite.parse(json) as Map<*, *>; require(m["version"] == 1L)
        val e = m["entry"] as Map<*, *>; val s = m["source"] as Map<*, *>
        return ReadingRecord(ref(m["ref"]), HadithEntry(e.text("collection"), e.text("book"), e.text("number"),
            e.text("label"), e.text("isnad"), e.text("narrator"), e.text("ar"), e.text("en"), e.text("ur"),
            e.list("words").map { word(it as Map<*, *>) }, (e["duration"] as? Number)?.toInt() ?: 0),
            HadithAudioSource(validateTrustedMediaUrl(s.text("timing")), validateTrustedMediaUrl(s.text("base")),
                validateTrustedMediaUrl(s.text("audio")), s["tokens"] == true, s["synthetic"] as? Boolean))
    }
    fun library(value: ReadingLibrary): String = encodeJson(mapOf(
        "version" to 1, "saved" to value.saved.map { it.json() },
        "recent" to value.recent.map { mapOf("ref" to it.ref.json(), "at" to it.openedAt, "position" to it.position) },
        "words" to value.words.map { mapOf("ref" to it.ref.json(), "word" to word(it.word)) },
        "settings" to value.settings.let { s -> mapOf("appearance" to s.appearance.name, "font" to s.typeface.name,
            "arSize" to s.arabicSize, "enSize" to s.translationSize, "line" to s.lineSpacing, "spacing" to s.wordSpacing,
            "language" to s.language.name, "gloss" to s.wordMeanings, "speed" to s.speed, "repeat" to s.repeat,
            "autoplay" to s.autoplay, "wifi" to s.wifiOnly) },
    ))
    fun library(json: String): ReadingLibrary {
        val m = JsonLite.parse(json) as Map<*, *>; require(m["version"] == 1L)
        val s = m["settings"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return ReadingLibrary(
            saved = m.list("saved").mapNotNull { runCatching { ref(it) }.getOrNull() }.distinctBy { it.key },
            recent = m.list("recent").mapNotNull { runCatching {
                val r = it as Map<*, *>; RecentReading(ref(r["ref"]), (r["at"] as Number).toLong(), r.float("position", 0f).finiteOr(0f).coerceAtLeast(0f))
            }.getOrNull() }.distinctBy { it.ref.key }.take(100),
            words = m.list("words").mapNotNull { runCatching { val w = it as Map<*, *>; SavedWord(ref(w["ref"]), word(w["word"] as Map<*, *>)) }.getOrNull() },
            settings = ReadingSettings(
                appearance = runCatching { ReadingAppearance.valueOf(s.text("appearance")) }.getOrDefault(ReadingAppearance.LIGHT),
                typeface = runCatching { ArabicTypeface.valueOf(s.text("font")) }.getOrDefault(ArabicTypeface.SCHEHERAZADE),
                arabicSize = s.float("arSize", 32f), translationSize = s.float("enSize", 18f), lineSpacing = s.float("line", 1.8f),
                wordSpacing = s.float("spacing", 2f), language = runCatching { ReadingLanguage.valueOf(s.text("language")) }.getOrDefault(ReadingLanguage.ENGLISH),
                wordMeanings = s["gloss"] != false, speed = s.float("speed", 1f), repeat = s["repeat"] == true,
                autoplay = s["autoplay"] != false, wifiOnly = s["wifi"] != false,
            ).constrained(),
        )
    }
    fun timing(value: HadithTiming): String = encodeJson(mapOf("audio" to value.audioUrl, "duration" to value.durationSeconds,
        "synthetic" to value.isSynthetic, "tokens" to value.tokens.map { mapOf("text" to it.text, "start" to it.startSeconds,
            "end" to it.endSeconds, "displayStart" to it.displayStartSeconds, "displayEnd" to it.displayEndSeconds) }))
    fun timing(json: String): HadithTiming {
        val m = JsonLite.parse(json) as Map<*, *>
        val tokens = m.list("tokens").map { val t = it as Map<*, *>; TimingToken(t.text("text"), t.float("start", 0f), t.float("end", 0f),
            (t["displayStart"] as? Number)?.toFloat(), (t["displayEnd"] as? Number)?.toFloat()) }
        require(tokens.isNotEmpty() && tokens.all { it.startSeconds.isFinite() && it.endSeconds.isFinite() && it.endSeconds >= it.startSeconds })
        return HadithTiming(validateTrustedMediaUrl(m.text("audio")), m.float("duration", 0f), tokens, m["synthetic"] == true)
    }
    private fun Map<*, *>.text(key: String) = this[key] as? String ?: ""
    private fun Map<*, *>.list(key: String) = this[key] as? List<*> ?: emptyList<Any>()
    private fun Map<*, *>.float(key: String, default: Float) = (this[key] as? Number)?.toFloat() ?: default
}

data class OfflineAudio(val file: File, val timing: HadithTiming)

/** Private app storage. Only complete, atomically published recordings count as offline. */
class ReadingStore(private val root: File) {
    private val records = File(root, "passages").apply { mkdirs() }
    private val downloads = File(root, "recordings").apply { mkdirs() }
    private val cache = File(root, "catalog").apply { mkdirs() }
    private val library = File(root, "library.json")
    private fun stem(ref: CatalogRef): String = canonicalRef(ref.collectionSlug, ref.hadithNumber).let { "${it.collectionSlug}_${it.normalizedNumber}" }
    fun readLibrary(): ReadingLibrary = runCatching { ReadingCodec.library(library.readText()) }.getOrDefault(ReadingLibrary())
    @Synchronized fun writeLibrary(value: ReadingLibrary) = atomicWrite(library, ReadingCodec.library(value))
    @Synchronized fun save(record: ReadingRecord) = atomicWrite(File(records, "${stem(record.ref)}.json"), ReadingCodec.record(record))
    fun read(ref: CatalogRef): ReadingRecord? = runCatching { ReadingCodec.record(File(records, "${stem(ref)}.json").readText()).takeIf { it.key == ref.key } }.getOrNull()
    fun openedRecords(): List<ReadingRecord> = records.listFiles().orEmpty().filter { it.extension == "json" }
        .sortedByDescending { it.lastModified() }.take(500).mapNotNull { runCatching { ReadingCodec.record(it.readText()) }.getOrNull() }
    fun cachedText(url: String): File {
        validateCatalogUrl(url)
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cache, "$hash.json")
    }
    fun partial(ref: CatalogRef) = File(downloads, "${stem(ref)}.part")
    fun offline(ref: CatalogRef): OfflineAudio? = runCatching {
        val file = File(downloads, "${stem(ref)}.audio"); require(file.isFile && file.length() > 0)
        OfflineAudio(file, ReadingCodec.timing(File(downloads, "${stem(ref)}.json").readText()))
    }.getOrNull()
    @Synchronized fun finishDownload(ref: CatalogRef, timing: HadithTiming) {
        val part = partial(ref); require(part.isFile && part.length() > 0)
        atomicWrite(File(downloads, "${stem(ref)}.json"), ReadingCodec.timing(timing))
        Files.move(part.toPath(), File(downloads, "${stem(ref)}.audio").toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    fun offlineRecords(): List<ReadingRecord> = downloads.listFiles().orEmpty().filter { it.extension == "audio" }.mapNotNull { file ->
        val name = file.nameWithoutExtension; val split = name.lastIndexOf('_')
        if (split < 0) null else runCatching { canonicalRef(name.substring(0, split), name.substring(split + 1)) }.getOrNull()
            ?.let { ref -> read(ref)?.takeIf { offline(ref) != null } }
    }
    fun downloadedBytes(): Long = downloads.listFiles().orEmpty().filter { it.extension == "audio" }.sumOf { it.length() }
    @Synchronized fun removeDownload(ref: CatalogRef) {
        File(downloads, "${stem(ref)}.audio").delete(); File(downloads, "${stem(ref)}.json").delete(); partial(ref).delete()
    }
    companion object {
        @Synchronized internal fun atomicWrite(file: File, text: String) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { out -> out.write(text.toByteArray(Charsets.UTF_8)); out.fd.sync() }
            try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
    }
}
