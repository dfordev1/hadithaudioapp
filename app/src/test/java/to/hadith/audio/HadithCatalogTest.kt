package to.hadith.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HadithCatalogTest {
    @Test fun exposesTheCompleteLiveHadithToShelf() {
        assertEquals(12, CatalogCollections.size)
        assertEquals(65_647, CatalogCollections.sumOf { it.totalCount ?: 0 })
        assertEquals(
            listOf(
                "bukhari", "muslim", "abudawud", "tirmidhi", "nasai", "ibnmajah",
                "malik", "riyad", "musnad-ahmad", "nawawi40", "qudsi40",
                "shahwaliullah40",
            ),
            CatalogCollections.map { it.slug },
        )
    }

    @Test fun parsesAllBookTitlesFromTheReaderContract() {
        val rows = (1..97).joinToString(",") { number ->
            """{"book":$number,"count":1,"from":$number,"to":$number}"""
        }
        val books = BukhariCatalogParser.parseIndex("""{"books":[$rows]}""")

        assertEquals("Revelation", books.first().title)
        assertEquals("Oneness of Allah (Tawheed)", books.last().title)
    }

    @Test fun parsesIndexBooksAndReportBookSuffixIds() {
        val json = """{"books":[{"book":1,"count":2,"from":1,"to":2},{"book":2,"count":1,"from":3,"to":3}],"reportBook":{"0001":1,"0402B":2}}"""
        // The production endpoint has 97 rows; this focused fixture exercises report parsing separately.
        assertEquals(mapOf("1" to 1, "402b" to 2), BukhariCatalogParser.parseReportBook(json))
        assertTrue(BukhariCatalogParser.isValidHadithId("402b"))
        assertFalse(BukhariCatalogParser.isValidHadithId("402x2"))
        assertEquals("1", BukhariCatalogParser.normalizeHadithId("0001"))
        assertEquals("402b", BukhariCatalogParser.normalizeHadithId("0402B"))
        assertEquals("1433abc", CatalogParser.normalizeHadithId("01433ABC"))
    }

    @Test fun genericIndexAcceptsCollectionSpecificBookRangesIncludingZero() {
        val ibnMajah = CatalogCollections.first { it.slug == "ibnmajah" }
        val books = CatalogParser.parseIndex(
            """{"books":[{"book":0,"count":2,"from":1,"to":2},{"book":1,"count":1,"from":3,"to":3}]}""",
            ibnMajah,
        )

        assertEquals(listOf(0, 1), books.map { it.number })
        assertEquals("ibnmajah", books.first().collection.slug)
    }

    @Test fun parsesArabicTokensAndMapsToExistingEntryShape() {
        val json = """{"book":1,"hadith":[{"n":"0001","isnad":"راوٍ","tokens":[{"id":"x","text":"إِنَّمَا"},{"id":"y","text":"الأَعْمَالُ"}]}]}"""
        val book = BukhariBook(1, 1, 1, 1, "Revelation")
        val entry = BukhariCatalogParser.parseBook(json, book).single()
        assertEquals("1", entry.number)
        assertEquals("راوٍ", entry.isnad)
        assertEquals("إِنَّمَا الأَعْمَالُ", entry.arabic)
        assertEquals("Sahih al-Bukhari", entry.asHadithEntry().collection)
    }

    @Test fun parsesTranslationAndFiltersRequestedHadith() {
        val json = """{"hadiths":[{"hadithnumber":1,"text":"first"},{"hadithnumber":"0002","text":"second"}]}"""
        assertEquals("second", BukhariCatalogParser.parseTranslation(json, "eng", "0002")?.text)
        assertEquals(null, BukhariCatalogParser.parseTranslation(json, "urd", "9"))
    }

    @Test fun validatesOnlyTrustedHttpsHosts() {
        assertEquals("https://www.hadith.to/bukhari/index.json", validateCatalogUrl("https://www.hadith.to/bukhari/index.json"))
        assertEquals("https://cdn.jsdelivr.net/gh/a", validateCatalogUrl("https://cdn.jsdelivr.net/gh/a"))
        assertFails { validateCatalogUrl("http://www.hadith.to/bukhari/index.json") }
        assertFails { validateCatalogUrl("https://evil.example/bukhari/index.json") }
        assertFails { validateCatalogUrl("https://www.hadith.to/bukhari/index.json?redirect=evil") }
    }

    @Test fun suffixHadithKeepsItsOwnUnavailableMediaPaths() {
        val detail = CatalogHadith(
            number = "402b",
            book = BukhariBook(8, 1, 402, 402, "Prayers (Salat)"),
            isnad = "",
            words = listOf(HadithWord("قَالَ", "", "", null)),
        ).asBukhariDetail()

        assertEquals(
            "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari-timings/n402b.json",
            detail.audioSource.timingUrl,
        )
        assertEquals(
            "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari/402b.mp3",
            detail.audioSource.fallbackAudioUrl,
        )
        assertTrue(detail.audioSource.displayTimingTokens)
    }

    @Test fun repositoryNormalizesLeadingZerosAndFetchesTheIndexOnce() = runBlocking {
        val rows = (1..97).joinToString(",") { number ->
            """{"book":$number,"count":1,"from":$number,"to":$number}"""
        }
        val index = """{"books":[$rows],"reportBook":{"1":1}}"""
        val calls = mutableListOf<String>()
        val repository = BukhariCatalogRepository { url ->
            calls += url
            when (url) {
                BukhariCatalogRepository.INDEX_URL -> index
                "${BukhariCatalogRepository.BASE_URL}/book-1.json" ->
                    """{"book":1,"hadith":[{"n":"1","isnad":"","tokens":[{"text":"قَالَ"}]}]}"""
                else -> error("Unexpected URL $url")
            }
        }

        assertEquals("1", repository.hadith("0001").number)
        assertEquals(1, calls.count { it == BukhariCatalogRepository.INDEX_URL })
    }

    @Test fun repositoryCachesIndexesAndBooksPerCollectionWithoutIdCollisions() = runBlocking {
        val bukhari = CatalogCollections.first { it.slug == "bukhari" }
        val muslim = CatalogCollections.first { it.slug == "muslim" }
        val calls = mutableListOf<String>()
        val repository = HadithCatalogRepository(
            getText = { url ->
                calls += url
                when (url) {
                    "https://www.hadith.to/bukhari/index.json" ->
                        """{"books":[{"book":1,"count":1,"from":1,"to":1}],"reportBook":{"1":1}}"""
                    "https://www.hadith.to/muslim/index.json" ->
                        """{"books":[{"book":1,"count":1,"from":1,"to":1}],"reportBook":{"1":1}}"""
                    "https://www.hadith.to/bukhari/book-1.json" ->
                        """{"book":1,"hadith":[{"n":"1","isnad":"b","tokens":[{"text":"بخاري"}]}]}"""
                    "https://www.hadith.to/muslim/book-1.json" ->
                        """{"book":1,"hadith":[{"n":"1","isnad":"m","tokens":[{"text":"مسلم"}]}]}"""
                    else -> error("Unexpected URL $url")
                }
            },
            collections = listOf(bukhari, muslim),
        )

        assertEquals("بخاري", repository.hadith(CatalogRef("bukhari", "1")).words.single().arabic)
        assertEquals("مسلم", repository.hadith(CatalogRef("muslim", "1")).words.single().arabic)
        repository.hadith(CatalogRef("bukhari", "1"))

        assertEquals(1, calls.count { it.endsWith("/bukhari/index.json") })
        assertEquals(1, calls.count { it.endsWith("/muslim/index.json") })
        assertEquals(1, calls.count { it.endsWith("/bukhari/book-1.json") })
        assertEquals(1, calls.count { it.endsWith("/muslim/book-1.json") })
    }

    @Test fun fortyCollectionsUseVirtualListsAndExactRecitationContracts() = runBlocking {
        val nawawi = CatalogCollections.first { it.slug == "nawawi40" }
        val calls = mutableListOf<String>()
        val repository = HadithCatalogRepository(
            getText = { url ->
                calls += url
                """{"tokens":[{"text":"عَنْ"},{"text":"عُمَرَ"}]}"""
            },
            collections = listOf(nawawi),
        )

        assertEquals(42, repository.hadiths("nawawi40", 1).size)
        val hadith = repository.hadith(CatalogRef("nawawi40", "1"))
        val detail = hadith.asCatalogDetail()

        assertEquals(listOf("عَنْ", "عُمَرَ"), hadith.words.map { it.arabic })
        assertEquals(
            "https://hadith.to/recitation/nawawi-arbain.001.json",
            detail.audioSource?.timingUrl,
        )
        assertEquals(
            "https://cdn.hadith.to/recitation/nawawi-arbain.001.mp3",
            detail.audioSource?.fallbackAudioUrl,
        )
        assertEquals(true, detail.audioSource?.syntheticOverride)
        assertEquals(listOf("https://hadith.to/recitation/nawawi-arbain.001.json"), calls)
    }

    @Test fun parsesRiyadAndFortyBulkTranslations() {
        val riyad = """{"translations":[{"n":1,"en":"Intentions","ur":"نیت"}]}"""
        val forty = """{"translations":[{"reportNumber":1,"full":"Full narration","matn":"Matn"}]}"""

        assertEquals(
            "Intentions",
            CatalogParser.parseBulkTranslations(riyad, "eng", "riyad")["1"]?.text,
        )
        assertEquals(
            "نیت",
            CatalogParser.parseBulkTranslations(riyad, "urd", "riyad")["1"]?.text,
        )
        assertEquals(
            "Full narration",
            CatalogParser.parseBulkTranslations(forty, "eng", "nawawi40")["1"]?.text,
        )
    }

    @Test fun collectionAudioPathsPreserveSuffixesAndMusnadWidth() {
        val tirmidhi = CatalogCollections.first { it.slug == "tirmidhi" }
        val tirmidhiBook = CatalogBook(1, 1, 1433, 1433, "Book 1", tirmidhi)
        val suffix = CatalogHadith("1433abc", tirmidhiBook, "", emptyList()).asCatalogDetail()
        assertEquals(
            "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/tirmidhi-timings/n1433abc.json",
            suffix.audioSource?.timingUrl,
        )

        val musnad = CatalogCollections.first { it.slug == "musnad-ahmad" }
        val musnadBook = CatalogBook(1, 1, 1, 1, "Part 1", musnad)
        val detail = CatalogHadith("1", musnadBook, "", emptyList()).asCatalogDetail()
        assertEquals(
            "https://cdn.hadith.to/musnad-ahmad/r00001.ogg",
            detail.audioSource?.fallbackAudioUrl,
        )
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); throw AssertionError("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
}
