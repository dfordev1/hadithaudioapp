package to.hadith.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HadithModelsTest {
    @Test
    fun wordIndexAtTimeMapsBoundariesAndClamps() {
        assertEquals(-1, wordIndexAtTime(1f, 0, 8))
        assertEquals(-1, wordIndexAtTime(1f, 10, 0))
        assertEquals(0, wordIndexAtTime(-1f, 10, 4))
        assertEquals(0, wordIndexAtTime(0f, 10, 4))
        assertEquals(2, wordIndexAtTime(5f, 10, 4))
        assertEquals(3, wordIndexAtTime(10f, 10, 4))
        assertEquals(3, wordIndexAtTime(99f, 10, 4))
    }

    @Test
    fun wordIndexAtTimeRejectsNonFinitePosition() {
        assertEquals(-1, wordIndexAtTime(Float.NaN, 10, 4))
        assertEquals(-1, wordIndexAtTime(Float.POSITIVE_INFINITY, 10, 4))
    }

    @Test
    fun seekTimeForWordMapsAndClamps() {
        assertEquals(0f, seekTimeForWord(-1, 10, 4), 0f)
        assertEquals(0f, seekTimeForWord(0, 10, 4), 0f)
        assertEquals(2.5f, seekTimeForWord(1, 10, 4), 0f)
        assertEquals(7.5f, seekTimeForWord(4, 10, 4), 0f)
        assertEquals(0f, seekTimeForWord(1, 0, 4), 0f)
    }

    @Test
    fun localRepositorySearchesTextAndReturnsAllForBlankQuery() {
        val second = FirstHadith.copy(
            number = "Hadith 2",
            arabic = "الصِّدْقُ",
            english = "A believer is sincere.",
        )
        val repository = LocalHadithRepository(listOf(FirstHadith, second))

        assertEquals(2, repository.search(" ").size)
        assertEquals(listOf(FirstHadith), repository.search("intentions"))
        assertEquals(listOf(FirstHadith), repository.search("النِّيَّاتِ"))
        assertEquals(0, repository.search("does not exist").size)
    }

    @Test
    fun particlesDoNotClaimAThreeLetterRoot() {
        val particle = FirstHadith.words.first()
        assertEquals(null, particle.root)
        assertEquals("Particle", particle.grammaticalCategory)
    }

    @Test
    fun normalizeArabicForTimingIgnoresHarakatTatweelAndPunctuation() {
        assertEquals(
            "انما",
            normalizeArabicForTiming("إِنَّــــمَا،"),
        )
        assertEquals(
            normalizeArabicForTiming("إِنَّمَا"),
            normalizeArabicForTiming("ٱِنَّما"),
        )
        assertEquals(
            normalizeArabicForTiming("نَوَى رَحْمَة"),
            normalizeArabicForTiming("نوي رحمه"),
        )
    }

    @Test
    fun audioUrlResolverSupportsSidecarFilenamesAndAbsoluteUrls() {
        assertEquals(
            FIRST_HADITH_AUDIO_FALLBACK_URL,
            resolveFirstHadithAudioUrl(null),
        )
        assertEquals(
            FIRST_HADITH_AUDIO_FALLBACK_URL,
            resolveFirstHadithAudioUrl("0001.mp3"),
        )
        assertEquals(
            "https://cdn.hadith.to/bukhari/0001.mp3",
            resolveFirstHadithAudioUrl("https://cdn.hadith.to/bukhari/0001.mp3"),
        )
        assertThrows(java.io.IOException::class.java) {
            resolveFirstHadithAudioUrl("http://cdn.hadith.to/bukhari/0001.mp3")
        }
        assertThrows(java.io.IOException::class.java) {
            resolveFirstHadithAudioUrl("https://evil.example/hadith.mp3")
        }
    }

    @Test
    fun matchTimingTokensSkipsIntroAndPreservesExactTokenTimes() {
        val tokens = listOf(
            TimingToken("مقدمة", 0f, 0.5f),
            TimingToken("إِنَّمَا،", 0.51f, 0.92f),
            TimingToken("الأَعْمَالُ", 0.93f, 1.48f),
            TimingToken("بِالنِّيَّاتِ", 1.49f, 2.24f),
        )

        val matches = matchTimingTokens(
            words = FirstHadith.words.take(3).map { it.arabic },
            tokens = tokens,
        )

        assertEquals(3, matches.size)
        assertEquals(1, matches[0].tokenIndex)
        assertEquals(0.51f, matches[0].startSeconds, 0f)
        assertEquals(0.92f, matches[0].endSeconds, 0f)
        assertEquals(2, matches[1].tokenIndex)
        assertEquals(3, matches[2].tokenIndex)
    }

    @Test
    fun wordIndexAtTimingUsesIntervalsAndReturnsPreviousAfterEnd() {
        val timings = listOf(
            WordTiming(0, 0, 1.0f, 1.5f),
            WordTiming(1, 1, 1.6f, 2.0f),
        )
        assertEquals(0, wordIndexAtTiming(1.2f, timings))
        assertEquals(1, wordIndexAtTiming(1.8f, timings))
        assertEquals(1, wordIndexAtTiming(2.4f, timings))
        assertEquals(-1, wordIndexAtTiming(Float.NaN, timings))
    }
}
