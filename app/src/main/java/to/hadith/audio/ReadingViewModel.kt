package to.hadith.audio

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

enum class ReadingPage { LIBRARY, COLLECTION, HADITHS, READER, PLAYER, SEARCH, DOWNLOADS, SETTINGS, APPEARANCE, SOURCES, REPORT, LICENCES }
enum class LibraryTab { COLLECTIONS, SAVED, RECENT }
enum class ShelfFilter(val label: String) { ALL("All"), SIX("Six books"), FORTY("Forty") }
enum class ReaderSheet { NONE, WORD, PLAYBACK, TOOLS, LANGUAGE }
enum class DownloadStatus { QUEUED, RUNNING, PAUSED, WAITING_WIFI, FAILED }
data class ReadingDownload(val ref: CatalogRef, val status: DownloadStatus, val bytes: Long = 0, val total: Long = -1, val message: String? = null)

data class ReadingState(
    val page: ReadingPage = ReadingPage.LIBRARY,
    val library: ReadingLibrary = ReadingLibrary(),
    val tab: LibraryTab = LibraryTab.COLLECTIONS,
    val filter: ShelfFilter = ShelfFilter.ALL,
    val collection: CatalogCollection = CatalogCollections.first(),
    val books: List<CatalogBook> = emptyList(),
    val book: CatalogBook? = null,
    val hadiths: List<CatalogHadith> = emptyList(),
    val current: ReadingRecord? = null,
    val requestedRef: CatalogRef? = null,
    val opened: List<ReadingRecord> = emptyList(),
    val savedRecords: List<ReadingRecord> = emptyList(),
    val offlineRecords: List<ReadingRecord> = emptyList(),
    val offlineBytes: Long = 0,
    val downloads: List<ReadingDownload> = emptyList(),
    val online: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val previous: CatalogRef? = null,
    val next: CatalogRef? = null,
    val study: Boolean = false,
    val meaning: Boolean = false,
    val focused: Boolean = false,
    val sheet: ReaderSheet = ReaderSheet.NONE,
    val wordIndex: Int? = null,
    val query: String = "",
    val searchCollection: String = "all",
    val exactResult: ReadingRecord? = null,
    val searching: Boolean = false,
    val searchMessage: String? = null,
    val sleepMinutes: Int = 0,
    val sleepAtEnd: Boolean = false,
    val message: String? = null,
    val sendingReport: Boolean = false,
    val reportResult: String? = null,
) {
    val settings: ReadingSettings get() = library.settings
    val words: List<HadithWord> get() = current?.entry?.words.orEmpty()
    fun isSaved(ref: CatalogRef) = library.saved.any { it.key == ref.key }
}

sealed interface ReadingAction {
    data class Go(val page: ReadingPage) : ReadingAction
    data object Back : ReadingAction
    data class Collection(val value: CatalogCollection) : ReadingAction
    data class Book(val value: CatalogBook) : ReadingAction
    data class Open(val ref: CatalogRef, val play: Boolean = false) : ReadingAction
    data class Tab(val value: LibraryTab) : ReadingAction
    data class Filter(val value: ShelfFilter) : ReadingAction
    data class Save(val ref: CatalogRef) : ReadingAction
    data class Settings(val value: ReadingSettings) : ReadingAction
    data object ToggleStudy : ReadingAction
    data object ToggleMeaning : ReadingAction
    data object ToggleFocus : ReadingAction
    data class Sheet(val value: ReaderSheet) : ReadingAction
    data class Word(val index: Int) : ReadingAction
    data class SaveWord(val value: HadithWord) : ReadingAction
    data object HearWord : ReadingAction
    data object PlayPause : ReadingAction
    data class Seek(val seconds: Float) : ReadingAction
    data class Skip(val seconds: Int) : ReadingAction
    data class Neighbor(val forward: Boolean) : ReadingAction
    data class Sleep(val minutes: Int = 0, val atEnd: Boolean = false) : ReadingAction
    data class Query(val value: String) : ReadingAction
    data class SearchCollection(val slug: String) : ReadingAction
    data object Find : ReadingAction
    data class Download(val ref: CatalogRef) : ReadingAction
    data class DownloadBook(val value: CatalogBook) : ReadingAction
    data class PauseDownload(val ref: CatalogRef) : ReadingAction
    data class CancelDownload(val ref: CatalogRef) : ReadingAction
    data class RemoveDownload(val ref: CatalogRef) : ReadingAction
    data class Report(val type: String, val note: String, val includeWord: Boolean) : ReadingAction
    data object Retry : ReadingAction
    data object DismissMessage : ReadingAction
}

class ReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ReadingStore(java.io.File(application.filesDir, "reading"))
    private val network = ReadingNetwork(application, store)
    private val catalog = HadithCatalogRepository(network::catalog)
    val audio = HadithAudioController(application)
    private val mutable = MutableStateFlow(ReadingState(online = network.online()))
    val state = mutable.asStateFlow()
    private val history = mutableListOf<ReadingPage>()
    private var readingJob: Job? = null
    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var downloadJob: Job? = null
    private val writes = Channel<ReadingLibrary>(Channel.CONFLATED)
    private var readerGeneration = 0
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged()
        override fun onLost(network: Network) = networkChanged()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = networkChanged()
    }

    init {
        viewModelScope.launch {
            for (value in writes) {
                try { withContext(Dispatchers.IO + NonCancellable) { store.writeLibrary(value) } }
                catch (_: IOException) { notify("Device storage is full. Changes are kept for this session.") }
            }
        }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.readLibrary() }
            mutable.update { it.copy(library = loaded) }
            refreshLocal()
            audio.setSpeed(loaded.settings.speed); audio.setRepeat(loaded.settings.repeat)
        }
        audio.onEnded = { if (mutable.value.settings.autoplay) mutable.value.next?.let { open(it, true, mutable.value.page) } }
        audio.onSleepFinished = { mutable.update { it.copy(sleepMinutes = 0, sleepAtEnd = false) } }
        connectivity.registerDefaultNetworkCallback(networkCallback)
        viewModelScope.launch {
            while (true) { delay(5000); rememberPosition() }
        }
    }

    fun dispatch(action: ReadingAction) {
        when (action) {
            is ReadingAction.Go -> go(action.page)
            ReadingAction.Back -> back()
            is ReadingAction.Collection -> selectCollection(action.value)
            is ReadingAction.Book -> selectBook(action.value)
            is ReadingAction.Open -> open(action.ref, action.play)
            is ReadingAction.Tab -> mutable.update { it.copy(tab = action.value) }
            is ReadingAction.Filter -> mutable.update { it.copy(filter = action.value) }
            is ReadingAction.Save -> { mutable.update { it.copy(library = it.library.toggleSaved(action.ref)) }; persist(); viewModelScope.launch { refreshLocal() } }
            is ReadingAction.Settings -> { mutable.update { it.copy(library = it.library.copy(settings = action.value.constrained())) }; persist(); audio.setSpeed(action.value.speed); audio.setRepeat(action.value.repeat); startNextDownload() }
            ReadingAction.ToggleStudy -> mutable.update { it.copy(study = !it.study) }
            ReadingAction.ToggleMeaning -> mutable.update { it.copy(meaning = !it.meaning) }
            ReadingAction.ToggleFocus -> mutable.update { it.copy(focused = !it.focused, sheet = ReaderSheet.NONE) }
            is ReadingAction.Sheet -> mutable.update { it.copy(sheet = action.value) }
            is ReadingAction.Word -> mutable.update { it.copy(wordIndex = action.index, sheet = ReaderSheet.WORD) }
            is ReadingAction.SaveWord -> saveWord(action.value)
            ReadingAction.HearWord -> mutable.value.wordIndex?.let(audio::playWord)
            ReadingAction.PlayPause -> if (audio.state.value.status == AudioStatus.READY && !audio.state.value.usingTimingPreview || audio.state.value.isPlaying) audio.togglePlayPause()
            is ReadingAction.Seek -> audio.seekTo(action.seconds)
            is ReadingAction.Skip -> audio.seekTo(audio.state.value.positionSeconds + action.seconds)
            is ReadingAction.Neighbor -> (if (action.forward) mutable.value.next else mutable.value.previous)?.let { open(it, audio.state.value.isPlaying, mutable.value.page) }
            is ReadingAction.Sleep -> { audio.setSleepTimer(action.minutes, action.atEnd); mutable.update { it.copy(sleepMinutes = action.minutes, sleepAtEnd = action.atEnd) } }
            is ReadingAction.Query -> { searchJob?.cancel(); mutable.update { it.copy(query = action.value, exactResult = null, searchMessage = null, searching = false) } }
            is ReadingAction.SearchCollection -> { searchJob?.cancel(); mutable.update { it.copy(searchCollection = action.slug, exactResult = null, searchMessage = null, searching = false) } }
            ReadingAction.Find -> find()
            is ReadingAction.Download -> enqueue(listOf(action.ref))
            is ReadingAction.DownloadBook -> viewModelScope.launch {
                try { enqueue(catalog.hadiths(action.value.collection.slug, action.value.number).map { CatalogRef(action.value.collection.slug, it.number) }); go(ReadingPage.DOWNLOADS) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { notify("Could not load the book. Check your connection.") }
            }
            is ReadingAction.PauseDownload -> pauseDownload(action.ref)
            is ReadingAction.CancelDownload -> cancelDownload(action.ref)
            is ReadingAction.RemoveDownload -> viewModelScope.launch { withContext(Dispatchers.IO) { store.removeDownload(action.ref) }; refreshLocal() }
            is ReadingAction.Report -> sendReport(action)
            ReadingAction.Retry -> retry()
            ReadingAction.DismissMessage -> mutable.update { it.copy(message = null) }
        }
    }

    private fun go(page: ReadingPage, opening: Boolean = false) {
        if (page == ReadingPage.PLAYER && mutable.value.current == null && !opening) {
            open(mutable.value.library.recent.firstOrNull()?.ref ?: CatalogRef("bukhari", "1"), false, ReadingPage.PLAYER); return
        }
        val old = mutable.value.page
        if (old != page) {
            if (page in setOf(ReadingPage.LIBRARY, ReadingPage.SEARCH, ReadingPage.PLAYER)) history.clear() else history += old
        }
        mutable.update { it.copy(page = page, sheet = ReaderSheet.NONE, focused = false, error = null, reportResult = null) }
    }
    private fun back() {
        when {
            mutable.value.sheet != ReaderSheet.NONE -> mutable.update { it.copy(sheet = ReaderSheet.NONE) }
            mutable.value.focused -> mutable.update { it.copy(focused = false) }
            else -> {
                val previous = history.removeLastOrNull() ?: ReadingPage.LIBRARY
                mutable.update { it.copy(page = previous, error = null) }
            }
        }
    }
    private fun selectCollection(collection: CatalogCollection) {
        go(ReadingPage.COLLECTION)
        mutable.update { it.copy(collection = collection, books = emptyList(), loading = true) }
        catalogJob?.cancel(); catalogJob = viewModelScope.launch {
            try { val books = catalog.books(collection.slug); mutable.update { it.copy(books = books, loading = if (it.page == ReadingPage.COLLECTION) false else it.loading) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { if (it.page == ReadingPage.COLLECTION) it.copy(loading = false, error = catalogError(failure), online = network.online()) else it } }
        }
    }
    private fun selectBook(book: CatalogBook) {
        go(ReadingPage.HADITHS)
        mutable.update { it.copy(book = book, hadiths = emptyList(), loading = true) }
        catalogJob?.cancel(); catalogJob = viewModelScope.launch {
            try { val list = catalog.hadiths(book.collection.slug, book.number); mutable.update { it.copy(hadiths = list, loading = if (it.page == ReadingPage.HADITHS) false else it.loading) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { if (it.page == ReadingPage.HADITHS) it.copy(loading = false, error = catalogError(failure), online = network.online()) else it } }
        }
    }

    private suspend fun load(ref: CatalogRef): ReadingRecord {
        withContext(Dispatchers.IO) { store.read(ref) }?.let { return it }
        return try {
            val hadith = catalog.hadith(ref)
            val translations = coroutineScope {
                val english = async { translation(ref, "eng") }; val urdu = async { translation(ref, "urd") }
                english.await() to urdu.await()
            }
            hadith.asCatalogDetail(translations.first, translations.second).let { ReadingRecord(ref, it.entry, it.audioSource) }
                .also { withContext(Dispatchers.IO) { store.save(it) } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (ref.key == "bukhari:1" && !network.online()) ReadingRecord(ref, FirstHadith, FIRST_HADITH_AUDIO_SOURCE)
            else throw failure
        }
    }
    private suspend fun translation(ref: CatalogRef, lang: String): String = try { catalog.translation(ref, lang)?.text.orEmpty() }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { "" }

    private fun open(ref: CatalogRef, play: Boolean = false, page: ReadingPage = ReadingPage.READER) {
        rememberPosition()
        val canonical = canonicalRef(ref.collectionSlug, ref.normalizedNumber)
        if (mutable.value.current?.key == canonical.key && !mutable.value.loading) {
            go(page); if (play && !audio.state.value.isPlaying) audio.togglePlayPause(); return
        }
        go(page, opening = true)
        val generation = ++readerGeneration
        readingJob?.cancel(); audio.stop()
        mutable.update { it.copy(loading = true, current = null, requestedRef = canonical, previous = null, next = null, wordIndex = null, meaning = false) }
        readingJob = viewModelScope.launch {
            try {
                val record = load(canonical)
                val offline = withContext(Dispatchers.IO) { store.save(record); store.offline(canonical) }
                if (generation != readerGeneration) return@launch
                mutable.update { it.copy(current = record, loading = false, library = it.library.opened(canonical, System.currentTimeMillis()), online = network.online()) }
                persist(); refreshLocal()
                val position = mutable.value.library.recent.firstOrNull { it.ref.key == canonical.key }?.position ?: 0f
                audio.prepare(record.entry, record.source, offline, play, if (play) 0f else position)
                neighbors(canonical, generation)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == readerGeneration) mutable.update { it.copy(loading = false, error = catalogError(failure), online = network.online()) } }
        }
    }
    private suspend fun neighbors(ref: CatalogRef, generation: Int) {
        try {
            val current = catalog.hadith(ref); val books = catalog.books(ref.collectionSlug)
            val list = catalog.hadiths(ref.collectionSlug, current.book.number)
            val index = list.indexOfFirst { it.number == ref.normalizedNumber }; val bookIndex = books.indexOfFirst { it.number == current.book.number }
            val before = list.getOrNull(index - 1)?.number ?: books.getOrNull(bookIndex - 1)?.let { catalog.hadiths(ref.collectionSlug, it.number).lastOrNull()?.number }
            val after = list.getOrNull(index + 1)?.number ?: books.getOrNull(bookIndex + 1)?.let { catalog.hadiths(ref.collectionSlug, it.number).firstOrNull()?.number }
            if (generation == readerGeneration) mutable.update { it.copy(previous = before?.let { n -> CatalogRef(ref.collectionSlug, n) }, next = after?.let { n -> CatalogRef(ref.collectionSlug, n) }) }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Offline text can still be read. */ }
    }
    private fun find() {
        val query = mutable.value.query.trim(); val slug = mutable.value.searchCollection
        if (!CatalogParser.isValidHadithId(query)) return
        if (slug == "all") { mutable.update { it.copy(searchMessage = "Choose a collection for an exact hadith number.") }; return }
        searchJob?.cancel(); mutable.update { it.copy(searching = true, searchMessage = null) }
        searchJob = viewModelScope.launch {
            try { val result = load(canonicalRef(slug, query)); mutable.update { it.copy(exactResult = result, searching = false) }; refreshLocal() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(searching = false, searchMessage = catalogError(failure)) } }
        }
    }
    private fun saveWord(word: HadithWord) {
        val ref = mutable.value.current?.ref ?: return; val item = SavedWord(ref, word)
        mutable.update { state -> state.copy(library = state.library.copy(words = if (state.library.words.any { it.key == item.key })
            state.library.words.filterNot { it.key == item.key } else listOf(item) + state.library.words)) }
        persist()
    }
    private fun persist() {
        writes.trySend(mutable.value.library)
    }
    private fun rememberPosition() {
        val ref = mutable.value.current?.ref ?: return
        val position = audio.state.value.positionSeconds
        mutable.update { it.copy(library = it.library.copy(recent = it.library.recent.map { item -> if (item.ref.key == ref.key) item.copy(position = position) else item })) }
        persist()
    }
    private suspend fun refreshLocal() {
        val library = mutable.value.library
        val local = withContext(Dispatchers.IO) { Triple(store.openedRecords(), library.saved.mapNotNull(store::read), store.offlineRecords()) to store.downloadedBytes() }
        mutable.update { it.copy(opened = local.first.first, savedRecords = local.first.second, offlineRecords = local.first.third, offlineBytes = local.second) }
    }

    private fun enqueue(refs: List<CatalogRef>) {
        mutable.update { state -> state.copy(downloads = state.downloads + refs.filter { ref -> state.downloads.none { it.ref.key == ref.key } && state.offlineRecords.none { it.key == ref.key } }
            .distinctBy { it.key }.map { ReadingDownload(it, DownloadStatus.QUEUED) }) }
        startNextDownload()
    }
    private fun startNextDownload() {
        if (!viewModelScope.isActive) return
        if (downloadJob?.isActive == true) return
        val next = mutable.value.downloads.firstOrNull { it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.WAITING_WIFI) } ?: return
        if (mutable.value.settings.wifiOnly && !network.onWifi()) { updateDownload(next.ref) { it.copy(status = DownloadStatus.WAITING_WIFI) }; return }
        downloadJob = viewModelScope.launch {
            updateDownload(next.ref) { it.copy(status = DownloadStatus.RUNNING, message = null) }
            try {
                val record = load(next.ref)
                withContext(Dispatchers.IO) { store.save(record) }
                val timing = HadithTimingRepository().fetch(record.source)
                network.download(next.ref, timing, { mutable.value.settings.wifiOnly }) { bytes, total -> updateDownload(next.ref) { it.copy(bytes = bytes, total = total) } }
                mutable.update { it.copy(downloads = it.downloads.filterNot { d -> d.ref.key == next.ref.key }) }
                refreshLocal()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: WifiRequired) { updateDownload(next.ref) { it.copy(status = DownloadStatus.WAITING_WIFI) } }
            catch (_: Exception) { updateDownload(next.ref) { it.copy(status = DownloadStatus.FAILED, message = "Download interrupted. Retry when connected.") } }
            finally { downloadJob = null; startNextDownload() }
        }
    }
    private fun updateDownload(ref: CatalogRef, change: (ReadingDownload) -> ReadingDownload) = mutable.update { s -> s.copy(downloads = s.downloads.map { if (it.ref.key == ref.key) change(it) else it }) }
    private fun pauseDownload(ref: CatalogRef) {
        val item = mutable.value.downloads.firstOrNull { it.ref.key == ref.key } ?: return
        if (item.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.WAITING_WIFI)) {
            updateDownload(ref) { it.copy(status = DownloadStatus.PAUSED) }
            if (item.status == DownloadStatus.RUNNING) downloadJob?.cancel()
        } else { updateDownload(ref) { it.copy(status = DownloadStatus.QUEUED, bytes = 0) }; startNextDownload() }
    }
    private fun cancelDownload(ref: CatalogRef) {
        val running = mutable.value.downloads.any { it.ref.key == ref.key && it.status == DownloadStatus.RUNNING }
        mutable.update { it.copy(downloads = it.downloads.filterNot { d -> d.ref.key == ref.key }) }
        val job = if (running) downloadJob else null
        job?.cancel()
        viewModelScope.launch { job?.join(); withContext(Dispatchers.IO) { store.partial(ref).delete() } }
    }
    private fun networkChanged() {
        mutable.update { it.copy(online = network.online()) }
        viewModelScope.launch { startNextDownload() }
    }
    private fun sendReport(action: ReadingAction.Report) {
        val record = mutable.value.current ?: run { notify("Open a passage first to report an error."); return }
        if (mutable.value.sendingReport) return
        mutable.update { it.copy(sendingReport = true, reportResult = null) }
        val word = if (action.includeWord) displayedReadingWords(mutable.value, audio.state.value).getOrNull(mutable.value.wordIndex ?: -1)?.arabic else null
        viewModelScope.launch {
            try { val id = HadithReportRepository().send(record, action.type, action.note, word); mutable.update { it.copy(sendingReport = false, reportResult = "Thank you. Report $id was received.") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(sendingReport = false, reportResult = "Could not send the report. Check your connection and try again.") } }
        }
    }
    private fun retry() {
        mutable.update { it.copy(online = network.online()) }
        when (mutable.value.page) {
            ReadingPage.COLLECTION -> selectCollection(mutable.value.collection)
            ReadingPage.HADITHS -> mutable.value.book?.let(::selectBook)
            ReadingPage.READER, ReadingPage.PLAYER -> {
                val s = mutable.value
                if (s.current != null) {
                    viewModelScope.launch { audio.prepare(s.current.entry, s.current.source, withContext(Dispatchers.IO) { store.offline(s.current.ref) }) }
                } else open(s.requestedRef ?: s.library.recent.firstOrNull()?.ref ?: CatalogRef("bukhari", "1"), page = s.page)
            }
            else -> { go(ReadingPage.LIBRARY); viewModelScope.launch { refreshLocal() } }
        }
    }
    private fun notify(message: String) = mutable.update { it.copy(message = message) }
    override fun onCleared() {
        writes.close()
        connectivity.unregisterNetworkCallback(networkCallback)
        audio.release()
        super.onCleared()
    }
}

internal fun catalogError(failure: Exception): String = when (failure) {
    is NoSuchElementException -> "That exact hadith number was not found in this collection."
    is IOException -> "Connect to the internet and try again. Your downloaded passages are still available."
    else -> "This passage could not be loaded. Please try again."
}

internal fun displayedReadingWords(state: ReadingState, audio: AudioUiState): List<HadithWord> {
    val source = state.words
    if (audio.timedWords.isEmpty()) return source
    // Keep sidecar Arabic authoritative; bring over a gloss only for the corresponding source token.
    var cursor = 0
    return audio.timedWords.map { text ->
        val found = (cursor until source.size).firstOrNull { normalizeArabicForTiming(source[it].arabic) == normalizeArabicForTiming(text) }
        if (found == null) HadithWord(text, "", "", null)
        else source[found].copy(arabic = text).also { cursor = found + 1 }
    }
}
