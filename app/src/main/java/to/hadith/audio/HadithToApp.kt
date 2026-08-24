package to.hadith.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class Destination { LISTEN, LIBRARY, SEARCH }
private enum class ReadingMode { RECEIVE, STUDY }
private enum class Language { ENGLISH, URDU, BOTH }
private enum class LibraryLevel { COLLECTIONS, BOOKS, HADITHS }

private sealed interface CatalogLoad<out T> {
    data object Idle : CatalogLoad<Nothing>
    data object Loading : CatalogLoad<Nothing>
    data class Ready<T>(val value: T) : CatalogLoad<T>
    data class Failed(val message: String) : CatalogLoad<Nothing>
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithToApp() {
    var destinationName by rememberSaveable { mutableStateOf(Destination.LISTEN.name) }
    var modeName by rememberSaveable { mutableStateOf(ReadingMode.RECEIVE.name) }
    var languageName by rememberSaveable { mutableStateOf(Language.ENGLISH.name) }
    var quietMode by rememberSaveable { mutableStateOf(false) }
    var selectedHadithKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRemoteCollectionSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRemoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailRetry by remember { mutableStateOf(0) }
    var libraryLevelName by rememberSaveable { mutableStateOf(LibraryLevel.COLLECTIONS.name) }
    var selectedCollectionSlug by rememberSaveable { mutableStateOf("bukhari") }
    var selectedBookNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var booksRetry by remember { mutableStateOf(0) }
    var bookRetry by remember { mutableStateOf(0) }
    var booksState by remember { mutableStateOf<CatalogLoad<List<CatalogBook>>>(CatalogLoad.Idle) }
    var bookState by remember { mutableStateOf<CatalogLoad<List<CatalogHadith>>>(CatalogLoad.Idle) }
    var remoteDetailState by remember {
        mutableStateOf<CatalogLoad<CatalogHadithDetail>>(CatalogLoad.Idle)
    }
    val localRepository = remember { LocalHadithRepository() }
    val entries = remember(localRepository) { localRepository.all() }
    val catalogRepository = remember { HadithCatalogRepository() }
    val context = LocalContext.current
    val audioController = remember(context) { HadithAudioController(context.applicationContext) }
    val audioState by audioController.state.collectAsStateWithLifecycle()

    val destination = Destination.valueOf(destinationName)
    val mode = ReadingMode.valueOf(modeName)
    val language = Language.valueOf(languageName)
    val libraryLevel = LibraryLevel.valueOf(libraryLevelName)
    val selectedCollection = CatalogCollections.first { it.slug == selectedCollectionSlug }
    val selectedRemoteRef = selectedRemoteCollectionSlug?.let { slug ->
        selectedRemoteId?.let { id -> CatalogRef(slug, id) }
    }
    val remoteDetail = when (val state = remoteDetailState) {
        is CatalogLoad.Ready -> state.value
        else -> null
    }
    val remoteSelectionPending = selectedRemoteRef != null
    val hadith = if (remoteSelectionPending) {
        remoteDetail?.entry
    } else {
        entries.firstOrNull { it.stableKey == selectedHadithKey } ?: entries.firstOrNull()
    }
    val audioSource = if (remoteSelectionPending) remoteDetail?.audioSource else FIRST_HADITH_AUDIO_SOURCE

    LaunchedEffect(selectedRemoteRef, detailRetry) {
        val ref = selectedRemoteRef ?: run {
            remoteDetailState = CatalogLoad.Idle
            return@LaunchedEffect
        }
        remoteDetailState = CatalogLoad.Loading
        try {
            val selected = catalogRepository.hadith(ref)
            val translations = coroutineScope {
                val english = async {
                    translationOrEmpty(catalogRepository, ref, "eng")
                }
                val urdu = async {
                    translationOrEmpty(catalogRepository, ref, "urd")
                }
                english.await() to urdu.await()
            }
            if (selectedRemoteRef == ref) {
                remoteDetailState = CatalogLoad.Ready(
                    selected.asCatalogDetail(
                        english = translations.first,
                        urdu = translations.second,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (selectedRemoteRef == ref) {
                remoteDetailState = CatalogLoad.Failed(friendlyCatalogError(failure))
            }
        }
    }

    LaunchedEffect(destination, libraryLevel, selectedCollectionSlug, booksRetry) {
        if (destination != Destination.LIBRARY || libraryLevel == LibraryLevel.COLLECTIONS) {
            return@LaunchedEffect
        }
        if (booksState is CatalogLoad.Ready) return@LaunchedEffect
        booksState = CatalogLoad.Loading
        try {
            booksState = CatalogLoad.Ready(catalogRepository.books(selectedCollectionSlug))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            booksState = CatalogLoad.Failed(friendlyCatalogError(failure))
        }
    }

    LaunchedEffect(destination, libraryLevel, selectedCollectionSlug, selectedBookNumber, bookRetry) {
        val book = selectedBookNumber
        if (
            destination != Destination.LIBRARY ||
            libraryLevel != LibraryLevel.HADITHS ||
            book == null
        ) {
            return@LaunchedEffect
        }
        bookState = CatalogLoad.Loading
        try {
            bookState = CatalogLoad.Ready(catalogRepository.hadiths(selectedCollectionSlug, book))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            bookState = CatalogLoad.Failed(friendlyCatalogError(failure))
        }
    }

    DisposableEffect(audioController) {
        onDispose { audioController.release() }
    }
    LaunchedEffect(hadith?.stableKey, audioSource) {
        if (hadith == null || audioSource == null) {
            audioController.stop()
        } else {
            audioController.prepare(hadith, audioSource)
        }
    }

    val displayWords = when {
        audioState.timedWords.isNotEmpty() -> audioState.timedWords.map {
            HadithWord(it, "", "", null)
        }
        hadith != null -> hadith.words
        else -> emptyList()
    }
    val selectedWord = if (hadith == null) {
        -1
    } else if (audioState.wordTimings.isNotEmpty()) {
        wordIndexAtTiming(audioState.positionSeconds, audioState.wordTimings)
    } else if (audioState.usingTimingPreview) {
        wordIndexAtTime(
            positionSeconds = audioState.positionSeconds,
            durationSeconds = audioState.durationSeconds.toInt(),
            wordCount = displayWords.size,
        )
    } else {
        -1
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!quietMode) {
                HadithBottomBar(
                    destination = destination,
                    onDestinationSelected = { destinationName = it.name },
                )
            }
        },
    ) { innerPadding ->
        when (destination) {
            Destination.LISTEN -> when {
                remoteSelectionPending && remoteDetailState is CatalogLoad.Failed -> {
                    CatalogErrorScreen(
                        title = "This hadith could not open",
                        message = (remoteDetailState as CatalogLoad.Failed).message,
                        modifier = Modifier.padding(innerPadding),
                        onRetry = { detailRetry++ },
                        onBack = {
                            selectedRemoteCollectionSlug = null
                            selectedRemoteId = null
                            destinationName = Destination.LIBRARY.name
                        },
                    )
                }

                remoteSelectionPending && hadith == null -> CatalogLoadingScreen(
                    label = "Opening hadith…",
                    modifier = Modifier.padding(innerPadding),
                )

                hadith == null -> EmptyRepositoryScreen(Modifier.padding(innerPadding))

                else -> ListenScreen(
                    hadith = hadith,
                    displayWords = displayWords,
                    mode = mode,
                    language = language,
                    quietMode = quietMode,
                    audioState = audioState,
                    selectedWord = selectedWord,
                    modifier = Modifier.padding(innerPadding),
                    onModeChange = { modeName = it.name },
                    onLanguageChange = { languageName = it.name },
                    onQuietModeChange = { quietMode = it },
                    onPlayPause = audioController::togglePlayPause,
                    onProgressChange = audioController::seekTo,
                    onWordSelected = { index ->
                        val preciseTime = audioState.wordTimings
                            .firstOrNull { it.wordIndex == index }
                            ?.startSeconds
                        val seekTime = preciseTime ?: if (audioState.usingTimingPreview) {
                            seekTimeForWord(
                                wordIndex = index,
                                durationSeconds = audioState.durationSeconds.toInt(),
                                wordCount = displayWords.size,
                            )
                        } else {
                            null
                        }
                        seekTime?.let(audioController::seekTo)
                    },
                )
            }

            Destination.LIBRARY -> when (libraryLevel) {
                LibraryLevel.COLLECTIONS -> LibraryScreen(
                    modifier = Modifier.padding(innerPadding),
                    onCollectionSelected = { collection ->
                        selectedCollectionSlug = collection.slug
                        selectedBookNumber = null
                        booksState = CatalogLoad.Idle
                        bookState = CatalogLoad.Idle
                        libraryLevelName = LibraryLevel.BOOKS.name
                    },
                )

                LibraryLevel.BOOKS -> when (val state = booksState) {
                    CatalogLoad.Idle, CatalogLoad.Loading -> CatalogLoadingScreen(
                        label = "Opening ${selectedCollection.title}…",
                        modifier = Modifier.padding(innerPadding),
                    )
                    is CatalogLoad.Failed -> CatalogErrorScreen(
                        title = "Library unavailable",
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                        onRetry = { booksRetry++ },
                        onBack = { libraryLevelName = LibraryLevel.COLLECTIONS.name },
                    )
                    is CatalogLoad.Ready -> CollectionBooksScreen(
                        collection = selectedCollection,
                        books = state.value,
                        modifier = Modifier.padding(innerPadding),
                        onBack = { libraryLevelName = LibraryLevel.COLLECTIONS.name },
                        onBookSelected = { selected ->
                            selectedBookNumber = selected.number
                            bookState = CatalogLoad.Idle
                            libraryLevelName = LibraryLevel.HADITHS.name
                        },
                    )
                }

                LibraryLevel.HADITHS -> when (val state = bookState) {
                    CatalogLoad.Idle, CatalogLoad.Loading -> CatalogLoadingScreen(
                        label = "Opening book…",
                        modifier = Modifier.padding(innerPadding),
                    )
                    is CatalogLoad.Failed -> CatalogErrorScreen(
                        title = "Book unavailable",
                        message = state.message,
                        modifier = Modifier.padding(innerPadding),
                        onRetry = { bookRetry++ },
                        onBack = { libraryLevelName = LibraryLevel.BOOKS.name },
                    )
                    is CatalogLoad.Ready -> CollectionHadithsScreen(
                        collection = selectedCollection,
                        bookNumber = selectedBookNumber ?: 1,
                        hadiths = state.value,
                        modifier = Modifier.padding(innerPadding),
                        onBack = { libraryLevelName = LibraryLevel.BOOKS.name },
                        onHadithSelected = { selected ->
                            selectedRemoteCollectionSlug = selected.book.collection.slug
                            selectedRemoteId = selected.number
                            remoteDetailState = CatalogLoad.Idle
                            detailRetry++
                            destinationName = Destination.LISTEN.name
                        },
                    )
                }
            }

            Destination.SEARCH -> SearchScreen(
                repository = localRepository,
                catalogRepository = catalogRepository,
                modifier = Modifier.padding(innerPadding),
                onResultSelected = { selected ->
                    selectedRemoteCollectionSlug = null
                    selectedRemoteId = null
                    selectedHadithKey = selected.stableKey
                    destinationName = Destination.LISTEN.name
                },
                onCatalogResultSelected = { selected ->
                    selectedRemoteCollectionSlug = selected.book.collection.slug
                    selectedRemoteId = selected.number
                    remoteDetailState = CatalogLoad.Idle
                    detailRetry++
                    destinationName = Destination.LISTEN.name
                },
            )
        }
    }
}

@Composable
private fun ListenScreen(
    hadith: HadithEntry,
    displayWords: List<HadithWord>,
    mode: ReadingMode,
    language: Language,
    quietMode: Boolean,
    audioState: AudioUiState,
    selectedWord: Int,
    modifier: Modifier,
    onModeChange: (ReadingMode) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onQuietModeChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onWordSelected: (Int) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "hadith.to",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (quietMode) "Quiet mode" else "A moment to listen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onQuietModeChange(!quietMode) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (quietMode) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (quietMode) "Exit quiet mode" else "Enter quiet mode",
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${hadith.collection}  ·  ${hadith.number}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${hadith.book}  ·  ${hadith.passageLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (hadith.isnad.isNotBlank()) {
            item {
                NarrationChain(hadith)
            }
        }

        if (!quietMode) {
            item {
                ModeChooser(mode = mode, onModeChange = onModeChange)
            }
        }

        item {
            ArabicHero(
                hadith = hadith,
                words = displayWords,
                selectedWord = selectedWord,
                onWordSelected = onWordSelected,
            )
        }

        if (!quietMode && mode == ReadingMode.STUDY && selectedWord in displayWords.indices) {
            item {
                WordInsight(displayWords[selectedWord])
            }
        }

        if (!quietMode) {
            item {
                LanguageChooser(language = language, onLanguageChange = onLanguageChange)
            }
        }

        if (!quietMode && (language == Language.ENGLISH || language == Language.BOTH)) {
            item {
                Text(
                    text = hadith.english.ifBlank { "Official English translation unavailable." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hadith.english.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (!quietMode && (language == Language.URDU || language == Language.BOTH)) {
            item {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    Text(
                        text = hadith.urdu.ifBlank { "سرکاری اردو ترجمہ دستیاب نہیں ہے۔" },
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Right,
                        color = if (hadith.urdu.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item {
            AudioController(
                state = audioState,
                onPlayPause = onPlayPause,
                onProgressChange = onProgressChange,
            )
        }
    }
}

@Composable
private fun ModeChooser(
    mode: ReadingMode,
    onModeChange: (ReadingMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadingMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChange(option) },
                label = { Text(if (option == ReadingMode.RECEIVE) "Receive" else "Study") },
                modifier = Modifier.semantics {
                    contentDescription = "${option.name.lowercase()} mode"
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArabicHero(
    hadith: HadithEntry,
    words: List<HadithWord>,
    selectedWord: Int,
    onWordSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                words.forEachIndexed { index, word ->
                    val active = index == selectedWord
                    Text(
                        text = word.arabic,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                            )
                            .clickable(role = Role.Button) { onWordSelected(index) }
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                            .semantics {
                                contentDescription = if (word.gloss.isBlank()) {
                                    word.arabic
                                } else {
                                    "${word.arabic}, ${word.gloss}"
                                }
                                selected = active
                            },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(
            text = "${hadith.collection} ${hadith.number.removePrefix("Hadith ")}  ·  ${hadith.passageLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NarrationChain(hadith: HadithEntry) {
    var expanded by rememberSaveable(hadith.stableKey) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Narration chain", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (hadith.narrator.isBlank()) "Full transmitted chain preserved"
                        else "Narrated by ${hadith.narrator}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics {
                        contentDescription = if (expanded) "Hide narration chain" else "Show narration chain"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    },
                ) {
                    Text(if (expanded) "Hide" else "View")
                }
            }
            if (expanded) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    Text(
                        text = hadith.isnad,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Right,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordInsight(word: HadithWord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(text = word.arabic, style = MaterialTheme.typography.titleLarge)
            if (word.transliteration.isBlank() && word.gloss.isBlank()) {
                Text(
                    text = "Lexical insight is not available for this word yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = listOf(word.transliteration, word.gloss)
                        .filter { it.isNotBlank() }
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val grammar = word.root?.let { "Root  $it" }
                    ?: word.grammaticalCategory.orEmpty()
                if (grammar.isNotBlank()) {
                    Text(
                        text = grammar,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageChooser(
    language: Language,
    onLanguageChange: (Language) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Language.entries.forEach { option ->
            FilterChip(
                selected = language == option,
                onClick = { onLanguageChange(option) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                label = {
                    Text(
                        text = when (option) {
                            Language.ENGLISH -> "English"
                            Language.URDU -> "اردو"
                            Language.BOTH -> "Both"
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun AudioController(
    state: AudioUiState,
    onPlayPause: () -> Unit,
    onProgressChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.statusText,
            modifier = Modifier.semantics {
                contentDescription = "Audio status: ${state.statusText}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (state.usingTimingPreview) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = state.positionSeconds,
            onValueChange = onProgressChange,
            enabled = !state.isLoading && state.durationSeconds > 0f,
            valueRange = 0f..state.durationSeconds.coerceAtLeast(0.1f),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Hadith audio progress" },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(state.positionSeconds.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPlayPause,
                enabled = !state.isLoading && state.durationSeconds > 0f,
                modifier = Modifier
                    .height(52.dp)
                    .semantics {
                        contentDescription = when {
                            state.usingTimingPreview && state.previewPlaying -> "Pause timing preview"
                            state.usingTimingPreview -> "Preview timing without audio"
                            state.isPlaying -> "Pause hadith"
                            else -> "Play hadith"
                        }
                    },
            ) {
                Icon(
                    imageVector = if (state.isPlaying || state.previewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        state.usingTimingPreview && state.previewPlaying -> "Pause preview"
                        state.usingTimingPreview -> "Preview timing"
                        state.isPlaying -> "Pause"
                        else -> "Listen"
                    },
                )
            }
            Text(
                text = formatTime(state.durationSeconds.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formatCount(count: Int?): String = count?.let {
    java.text.NumberFormat.getIntegerInstance().format(it)
} ?: "Available"

private fun collectionShelfLabel(collection: CatalogCollection): String = when (collection.kind) {
    CatalogCollection.Kind.FORTY -> "complete series · read & listen"
    CatalogCollection.Kind.MUSNAD -> "28 parts · read & listen"
    CatalogCollection.Kind.BOOKS -> "read & listen"
}

private fun shortCollectionTitle(collection: CatalogCollection): String = when (collection.slug) {
    "bukhari" -> "Bukhari"
    "muslim" -> "Muslim"
    "abudawud" -> "Abu Dawud"
    "tirmidhi" -> "Tirmidhi"
    "nasai" -> "Nasa'i"
    "ibnmajah" -> "Ibn Majah"
    "malik" -> "Muwatta"
    "riyad" -> "Riyad"
    "musnad-ahmad" -> "Musnad Ahmad"
    "nawawi40" -> "Nawawi 40"
    "qudsi40" -> "Qudsi 40"
    "shahwaliullah40" -> "Shah Waliullah 40"
    else -> collection.title
}

private fun catalogBookLabel(collection: CatalogCollection, book: CatalogBook): String =
    when (collection.kind) {
        CatalogCollection.Kind.FORTY -> "All narrations"
        CatalogCollection.Kind.MUSNAD -> "Part ${book.number}"
        CatalogCollection.Kind.BOOKS -> if (book.title == "Book ${book.number}") {
            "Book ${book.number}"
        } else {
            "Book ${book.number} · ${book.title}"
        }
    }

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    onCollectionSelected: (CatalogCollection) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(text = "Library", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Collections, quietly arranged.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
        items(CatalogCollections, key = { it.slug }) { collection ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                    ) { onCollectionSelected(collection) }
                    .padding(vertical = 16.dp)
                    .semantics {
                        contentDescription = "Open ${collection.title}, ${formatCount(collection.totalCount)} hadith"
                        stateDescription = "Available"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        collection.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${formatCount(collection.totalCount)} hadith · ${collectionShelfLabel(collection)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Open",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CollectionBooksScreen(
    collection: CatalogCollection,
    books: List<CatalogBook>,
    modifier: Modifier,
    onBack: () -> Unit,
    onBookSelected: (CatalogBook) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            CatalogHeader(
                eyebrow = collection.title,
                title = if (collection.kind == CatalogCollection.Kind.MUSNAD) {
                    "${books.size} parts"
                } else if (collection.kind == CatalogCollection.Kind.FORTY) {
                    "Complete collection"
                } else {
                    "${books.size} books"
                },
                onBack = onBack,
            )
            Text(
                text = "${formatCount(collection.totalCount)} hadith · Arabic opens first",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }
        items(books, key = { it.number }) { book ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onBookSelected(book) }
                    .padding(vertical = 15.dp)
                    .semantics {
                        contentDescription = "Open ${catalogBookLabel(collection, book)}, ${book.count} hadith"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = book.number.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.sizeIn(minWidth = 42.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = catalogBookLabel(collection, book),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${book.count} hadith",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CollectionHadithsScreen(
    collection: CatalogCollection,
    bookNumber: Int,
    hadiths: List<CatalogHadith>,
    modifier: Modifier,
    onBack: () -> Unit,
    onHadithSelected: (CatalogHadith) -> Unit,
) {
    val title = when (collection.kind) {
        CatalogCollection.Kind.MUSNAD -> "Part $bookNumber"
        CatalogCollection.Kind.FORTY -> collection.title
        CatalogCollection.Kind.BOOKS -> hadiths.firstOrNull()?.book?.title
            ?: catalogBookTitle(collection.slug, bookNumber)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            CatalogHeader(
                eyebrow = collection.title,
                title = title,
                onBack = onBack,
            )
            Text(
                text = "${hadiths.size} narrations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }
        items(hadiths, key = { it.number }) { hadith ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onHadithSelected(hadith) }
                    .semantics {
                        contentDescription = "Open ${collection.title} hadith ${hadith.number}"
                    },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Hadith ${hadith.number}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (hadith.words.isNotEmpty()) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                        ) {
                            Text(
                                text = hadith.words.take(20).joinToString(" ") { it.arabic }
                                    .let { if (hadith.words.size > 20) "$it…" else it },
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Right,
                            )
                        }
                    } else {
                        Text(
                            text = "Open narration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
        }
        TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
            Text("Back")
        }
    }
}

@Composable
private fun CatalogLoadingScreen(label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogErrorScreen(
    title: String,
    message: String,
    modifier: Modifier,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text("Back")
            }
            Button(onClick = onRetry, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun SearchScreen(
    repository: HadithRepository,
    catalogRepository: HadithCatalogRepository,
    modifier: Modifier,
    onResultSelected: (HadithEntry) -> Unit,
    onCatalogResultSelected: (CatalogHadith) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCollectionSlug by rememberSaveable { mutableStateOf("bukhari") }
    var catalogResult by remember { mutableStateOf<CatalogLoad<CatalogHadith>>(CatalogLoad.Idle) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val normalizedQuery = query.trim()
    val selectedCollection = CatalogCollections.first { it.slug == selectedCollectionSlug }
    val isCatalogNumber = CatalogParser.isValidHadithId(normalizedQuery)
    val results = if (isCatalogNumber || normalizedQuery.isBlank()) emptyList()
    else repository.search(normalizedQuery)

    fun openNumber() {
        val id = normalizedQuery
        if (!CatalogParser.isValidHadithId(id)) return
        val ref = CatalogRef(selectedCollectionSlug, id)
        searchJob?.cancel()
        searchJob = scope.launch {
            catalogResult = CatalogLoad.Loading
            try {
                val result = catalogRepository.hadith(ref)
                if (query.trim() == id && selectedCollectionSlug == ref.collectionSlug) {
                    catalogResult = CatalogLoad.Ready(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (query.trim() == id && selectedCollectionSlug == ref.collectionSlug) {
                    catalogResult = CatalogLoad.Failed(friendlyCatalogError(failure))
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = "Search", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose a collection, then open any exact hadith number. Words search the offline opening preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CatalogCollections, key = { it.slug }) { collection ->
                    FilterChip(
                        selected = selectedCollectionSlug == collection.slug,
                        onClick = {
                            searchJob?.cancel()
                            selectedCollectionSlug = collection.slug
                            catalogResult = CatalogLoad.Idle
                        },
                        label = { Text(shortCollectionTitle(collection)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    searchJob?.cancel()
                    query = it
                    catalogResult = CatalogLoad.Idle
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                singleLine = true,
                placeholder = { Text("Try “intentions” or “402b”") },
                label = { Text("Words or ${shortCollectionTitle(selectedCollection)} number") },
            )
        }

        if (isCatalogNumber) {
            item {
                Button(
                    onClick = ::openNumber,
                    enabled = catalogResult !is CatalogLoad.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        if (catalogResult is CatalogLoad.Loading) "Opening…"
                        else "Open ${shortCollectionTitle(selectedCollection)} $normalizedQuery",
                    )
                }
            }
        }

        when (val state = catalogResult) {
            CatalogLoad.Idle, CatalogLoad.Loading -> Unit
            is CatalogLoad.Failed -> item {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is CatalogLoad.Ready -> item {
                CatalogHadithSearchResult(state.value, onCatalogResultSelected)
            }
        }

        items(results, key = { it.stableKey }) { result ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onResultSelected(result) }
                    .semantics { contentDescription = "Open ${result.collection} ${result.number}" },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${result.collection}  ·  ${result.number}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(result.english, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (normalizedQuery.isNotBlank() && !isCatalogNumber && results.isEmpty()) {
            item {
                Text(
                    text = "No match in the downloaded preview. Full-catalog text search is not indexed offline yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CatalogHadithSearchResult(
    hadith: CatalogHadith,
    onSelected: (CatalogHadith) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onSelected(hadith) }
            .semantics {
                contentDescription = "Open ${hadith.book.collection.title} hadith ${hadith.number}"
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${shortCollectionTitle(hadith.book.collection)} · Hadith ${hadith.number}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                Text(
                    text = hadith.words.take(20).joinToString(" ") { it.arabic }
                        .let { if (hadith.words.size > 20) "$it…" else it },
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Right,
                )
            }
        }
    }
}

private fun friendlyCatalogError(failure: Exception): String = when (failure) {
    is NoSuchElementException -> failure.message ?: "That hadith number was not found."
    is java.io.IOException -> "Connect to the internet and try again."
    else -> "The catalog response could not be read. Please try again."
}

private suspend fun translationOrEmpty(
    repository: HadithCatalogRepository,
    ref: CatalogRef,
    language: String,
): String = try {
    repository.translation(ref, language)?.text.orEmpty()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ""
}

@Composable
private fun EmptyRepositoryScreen(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No hadith available", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "The local preview could not be loaded.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HadithBottomBar(
    destination: Destination,
    onDestinationSelected: (Destination) -> Unit,
) {
    NavigationBar(
        tonalElevation = 0.dp,
    ) {
        listOf(
            Destination.LISTEN to "Listen",
            Destination.LIBRARY to "Library",
            Destination.SEARCH to "Search",
        ).forEach { (item, label) ->
            NavigationBarItem(
                selected = destination == item,
                onClick = { onDestinationSelected(item) },
                icon = {
                    Icon(
                        imageVector = when (item) {
                            Destination.LISTEN -> Icons.Filled.Headphones
                            Destination.LIBRARY -> Icons.AutoMirrored.Filled.MenuBook
                            Destination.SEARCH -> Icons.Filled.Search
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}
