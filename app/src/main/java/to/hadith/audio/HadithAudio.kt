package to.hadith.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.roundToLong

const val FIRST_HADITH_TIMING_URL =
    "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari-timings/n0001.json"
const val FIRST_HADITH_AUDIO_FALLBACK_URL =
    "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari/0001.mp3"
private const val FIRST_HADITH_AUDIO_BASE_URL =
    "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari"

val FIRST_HADITH_AUDIO_SOURCE = HadithAudioSource(
    timingUrl = FIRST_HADITH_TIMING_URL,
    audioBaseUrl = "$FIRST_HADITH_AUDIO_BASE_URL/",
    fallbackAudioUrl = FIRST_HADITH_AUDIO_FALLBACK_URL,
)

private const val TIMING_CONNECT_TIMEOUT_MS = 15_000
private const val TIMING_READ_TIMEOUT_MS = 20_000

enum class AudioStatus { IDLE, LOADING, READY, ERROR }

data class AudioUiState(
    val status: AudioStatus = AudioStatus.IDLE,
    val isPlaying: Boolean = false,
    val previewPlaying: Boolean = false,
    val positionSeconds: Float = 0f,
    val durationSeconds: Float = 0f,
    val wordTimings: List<WordTiming> = emptyList(),
    /** Exact sidecar words when the manifest, rather than the corpus, is the aligned stream. */
    val timedWords: List<String> = emptyList(),
    val errorMessage: String? = null,
    val usingTimingPreview: Boolean = false,
    val isSynthetic: Boolean = false,
) {
    val isLoading: Boolean get() = status == AudioStatus.LOADING
    val statusText: String
        get() = when {
            usingTimingPreview && durationSeconds > 0f -> "Timing preview · audio unavailable"
            usingTimingPreview -> "Audio unavailable"
            isLoading -> "Loading audio timing…"
            errorMessage != null -> errorMessage
            isPlaying && isSynthetic -> "Playing · synthetic narration"
            isPlaying -> "Playing · human narration"
            status == AudioStatus.READY && isSynthetic -> "Synthetic narration · synced"
            status == AudioStatus.READY -> "Human narration · synced"
            else -> "Preparing audio"
        }
}

internal fun resolveFirstHadithAudioUrl(value: String?): String {
    return resolveHadithAudioUrl(FIRST_HADITH_AUDIO_SOURCE, value)
}

internal fun resolveHadithAudioUrl(source: HadithAudioSource, value: String?): String {
    validateTrustedMediaUrl(source.timingUrl)
    validateTrustedMediaUrl(source.audioBaseUrl)
    validateTrustedMediaUrl(source.fallbackAudioUrl)
    val candidate = value?.trim().orEmpty()
    val candidateUri = candidate.takeIf { it.isNotEmpty() }?.let {
        runCatching { URI(it) }.getOrElse { throw IOException("Invalid media URL") }
    }
    val resolved = when {
        candidate.isEmpty() -> source.fallbackAudioUrl
        candidateUri?.isAbsolute == true -> candidate
        else -> URI(source.audioBaseUrl).resolve(candidate.trimStart('/')).toString()
    }
    return validateTrustedMediaUrl(resolved)
}

internal fun validateTrustedMediaUrl(value: String): String {
    val uri = runCatching { URI(value) }.getOrElse { throw IOException("Invalid media URL") }
    if (
        uri.scheme != "https" ||
        uri.userInfo != null ||
        uri.query != null ||
        uri.fragment != null ||
        uri.host !in TRUSTED_MEDIA_HOSTS
    ) {
        throw IOException("Untrusted media URL")
    }
    return value
}

private val TRUSTED_MEDIA_HOSTS = setOf(
    "pub-4c1d62290e264660b4061d58417926be.r2.dev",
    "cdn.hadith.to",
)

/** Fetches the small timing manifest without coupling the network layer to Compose. */
class HadithTimingRepository {
    suspend fun fetch(source: HadithAudioSource = FIRST_HADITH_AUDIO_SOURCE): HadithTiming = withContext(Dispatchers.IO) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val timingUrl = validateTrustedMediaUrl(source.timingUrl)
        val connection = (URL(timingUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMING_CONNECT_TIMEOUT_MS
            readTimeout = TIMING_READ_TIMEOUT_MS
            doInput = true
            useCaches = false
            instanceFollowRedirects = false
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Timing request failed ($responseCode)")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            parseTiming(body, source)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTiming(body: String, source: HadithAudioSource): HadithTiming {
        val root = JSONObject(body)
        val tokensJson = root.optJSONArray("tokens") ?: throw IOException("Timing manifest has no tokens")
        val tokens = buildList(tokensJson.length()) {
            for (index in 0 until tokensJson.length()) {
                val token = tokensJson.optJSONObject(index) ?: continue
                val text = token.optString("text").trim()
                val displayStart = token.optionalFiniteFloat("displayStart")
                val displayEnd = token.optionalFiniteFloat("displayEnd")
                val start = token.optionalFiniteFloat("start") ?: displayStart
                val end = token.optionalFiniteFloat("end") ?: displayEnd
                if (text.isNotEmpty() && start != null && end != null && end >= start) {
                    add(
                        TimingToken(
                            text = text,
                            startSeconds = start,
                            endSeconds = end,
                            displayStartSeconds = displayStart,
                            displayEndSeconds = displayEnd,
                        ),
                    )
                }
            }
        }
        if (tokens.isEmpty()) throw IOException("Timing manifest has no usable tokens")
        return HadithTiming(
            audioUrl = resolveHadithAudioUrl(source, root.optString("audio")),
            durationSeconds = root.optDouble("duration", 0.0).toFloat(),
            tokens = tokens,
            isSynthetic = root.optBoolean("synthetic", false),
        )
    }
}

private fun JSONObject.optionalFiniteFloat(key: String): Float? {
    val value = optDouble(key, Double.NaN).toFloat()
    return value.takeIf { it.isFinite() }
}

/**
 * Owns the player and its main-thread state. A Compose owner should call [release] from
 * DisposableEffect; no Activity or View reference is retained by this class.
 */
class HadithAudioController(
    context: Context,
    private val timingRepository: HadithTimingRepository = HadithTimingRepository(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        setHandleAudioBecomingNoisy(true)
    }
    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var progressJob: Job? = null
    private var released = false
    private var previewDurationSeconds = 0f
    private var prepareGeneration = 0L
    private var activeMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (callbackIsCurrent() && !_state.value.usingTimingPreview) {
                updateFromPlayer()
                if (isPlaying) startPlayerProgressUpdates() else stopPlayerProgressUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!callbackIsCurrent() || _state.value.usingTimingPreview) return
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.value = _state.value.copy(status = AudioStatus.LOADING)
                Player.STATE_READY -> updateFromPlayer()
                Player.STATE_ENDED -> {
                    player.pause()
                    player.seekTo(0)
                    _state.value = _state.value.copy(
                        status = AudioStatus.READY,
                        isPlaying = false,
                        positionSeconds = 0f,
                    )
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (callbackIsCurrent()) enterTimingPreview("Audio unavailable: ${error.errorCodeName}")
        }
    }

    init {
        player.addListener(listener)
    }

    fun prepare(
        hadith: HadithEntry,
        source: HadithAudioSource = FIRST_HADITH_AUDIO_SOURCE,
    ) {
        if (released) return
        val generation = ++prepareGeneration
        loadJob?.cancel()
        previewJob?.cancel()
        progressJob?.cancel()
        previewJob = null
        previewDurationSeconds = hadith.durationSeconds.toFloat()
        activeMediaId = null
        player.stop()
        player.clearMediaItems()
        _state.value = AudioUiState(status = AudioStatus.LOADING)
        loadJob = scope.launch {
            try {
                val timing = timingRepository.fetch(source)
                ensureActive()
                if (generation != prepareGeneration) return@launch
                val timedTokens = timing.tokens.withIndex().filter {
                    normalizeArabicForTiming(it.value.text).isNotEmpty()
                }
                val displayWords: List<String>
                val matches: List<WordTiming>
                if (source.displayTimingTokens) {
                    displayWords = timedTokens.map { it.value.text }
                    matches = timedTokens.mapIndexed { wordIndex, indexed ->
                        WordTiming(
                            wordIndex = wordIndex,
                            tokenIndex = indexed.index,
                            startSeconds = indexed.value.displayStartSeconds ?: indexed.value.startSeconds,
                            endSeconds = indexed.value.displayEndSeconds ?: indexed.value.endSeconds,
                        )
                    }
                } else {
                    displayWords = emptyList()
                    matches = matchTimingTokens(hadith.words.map { it.arabic }, timing.tokens)
                    val expectedMatches = hadith.words.count {
                        normalizeArabicForTiming(it.arabic).isNotEmpty()
                    }
                    if (matches.size != expectedMatches) {
                        throw IOException("Timing manifest did not cover every displayed word")
                    }
                }
                if (matches.isEmpty()) throw IOException("Timing manifest has no aligned words")
                configurePlayer(timing, matches, displayWords, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isActive && generation == prepareGeneration) {
                    enterTimingPreview(failure.message ?: "Timing unavailable")
                }
            }
        }
    }

    private fun configurePlayer(
        timing: HadithTiming,
        absoluteMatches: List<WordTiming>,
        displayWords: List<String>,
        generation: Long,
    ) {
        if (released || generation != prepareGeneration) return
        val clipStartSeconds = absoluteMatches.minOf { it.startSeconds }
        val clipEndSeconds = absoluteMatches.maxOf { it.endSeconds }
        val durationSeconds = clipEndSeconds - clipStartSeconds
        if (!durationSeconds.isFinite() || durationSeconds <= 0f) {
            throw IOException("Timing interval is invalid")
        }
        val relativeMatches = absoluteMatches.map { match ->
            match.copy(
                startSeconds = (match.startSeconds - clipStartSeconds).coerceAtLeast(0f),
                endSeconds = (match.endSeconds - clipStartSeconds).coerceAtLeast(0f),
            )
        }
        val clipStartMs = (clipStartSeconds * 1_000f).roundToLong().coerceAtLeast(0L)
        val clipEndMs = (clipEndSeconds * 1_000f).roundToLong().coerceAtLeast(clipStartMs + 1L)
        val mediaId = "hadith-$generation"
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(timing.audioUrl)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs)
                    .setEndPositionMs(clipEndMs)
                    .build(),
            )
            .build()
        _state.value = AudioUiState(
            status = AudioStatus.LOADING,
            durationSeconds = durationSeconds,
            wordTimings = relativeMatches,
            timedWords = displayWords,
            isSynthetic = timing.isSynthetic,
        )
        previewDurationSeconds = durationSeconds
        activeMediaId = mediaId
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    fun togglePlayPause() {
        if (released || _state.value.isLoading) return
        if (_state.value.usingTimingPreview) {
            if (_state.value.previewPlaying) stopTimingPreview() else startTimingPreview()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
        updateFromPlayer()
    }

    fun seekTo(positionSeconds: Float) {
        if (released) return
        val duration = _state.value.durationSeconds
        val position = positionSeconds.coerceIn(0f, duration.coerceAtLeast(0f))
        if (_state.value.usingTimingPreview) {
            _state.value = _state.value.copy(positionSeconds = position)
        } else {
            player.seekTo((position * 1_000f).roundToLong())
            updateFromPlayer()
        }
    }

    private fun updateFromPlayer() {
        if (!callbackIsCurrent()) return
        val current = _state.value
        val position = (player.currentPosition.coerceAtLeast(0L) / 1_000f)
        val duration = if (current.durationSeconds > 0f) current.durationSeconds
        else (player.duration.coerceAtLeast(0L) / 1_000f)
        _state.value = current.copy(
            status = if (player.playbackState == Player.STATE_BUFFERING) AudioStatus.LOADING else AudioStatus.READY,
            isPlaying = player.isPlaying,
            positionSeconds = position.coerceIn(0f, duration.coerceAtLeast(0f)),
            durationSeconds = duration,
        )
    }

    private fun startPlayerProgressUpdates() {
        if (progressJob?.isActive == true) return
        val mediaId = activeMediaId ?: return
        progressJob = scope.launch {
            while (
                isActive &&
                activeMediaId == mediaId &&
                !_state.value.usingTimingPreview &&
                player.isPlaying
            ) {
                updateFromPlayer()
                delay(100)
            }
            if (isActive && activeMediaId == mediaId && !_state.value.usingTimingPreview) {
                updateFromPlayer()
            }
        }
    }

    private fun stopPlayerProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startTimingPreview() {
        previewJob?.cancel()
        val duration = _state.value.durationSeconds.takeIf { it > 0f } ?: previewDurationSeconds
        previewDurationSeconds = duration
        previewJob = scope.launch {
            _state.value = _state.value.copy(previewPlaying = true, isPlaying = false)
            while (isActive) {
                delay(100)
                val next = _state.value.positionSeconds + 0.1f
                if (next >= duration) {
                    _state.value = _state.value.copy(positionSeconds = 0f, previewPlaying = false)
                    break
                }
                _state.value = _state.value.copy(positionSeconds = next, previewPlaying = true, isPlaying = false)
            }
        }
    }

    private fun stopTimingPreview() {
        previewJob?.cancel()
        previewJob = null
        _state.value = _state.value.copy(previewPlaying = false, isPlaying = false)
    }

    private fun enterTimingPreview(message: String) {
        stopPlayerProgressUpdates()
        player.pause()
        previewJob?.cancel()
        val duration = previewDurationSeconds.takeIf { it > 0f } ?: 0f
        _state.value = _state.value.copy(
            status = AudioStatus.ERROR,
            isPlaying = false,
            previewPlaying = false,
            durationSeconds = duration,
            errorMessage = message,
            usingTimingPreview = true,
        )
    }

    private fun callbackIsCurrent(): Boolean {
        val mediaId = activeMediaId ?: return false
        return player.currentMediaItem?.mediaId == mediaId
    }

    fun stop() {
        if (released) return
        prepareGeneration++
        activeMediaId = null
        loadJob?.cancel()
        previewJob?.cancel()
        progressJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = AudioUiState()
    }

    fun release() {
        if (released) return
        released = true
        prepareGeneration++
        activeMediaId = null
        loadJob?.cancel()
        previewJob?.cancel()
        progressJob?.cancel()
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }
}
