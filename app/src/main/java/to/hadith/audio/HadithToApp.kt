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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
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

private enum class Destination { LISTEN, LIBRARY, SEARCH }
private enum class ReadingMode { RECEIVE, STUDY }
private enum class Language { ENGLISH, URDU, BOTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithToApp() {
    var destinationName by rememberSaveable { mutableStateOf(Destination.LISTEN.name) }
    var modeName by rememberSaveable { mutableStateOf(ReadingMode.RECEIVE.name) }
    var languageName by rememberSaveable { mutableStateOf(Language.ENGLISH.name) }
    var quietMode by rememberSaveable { mutableStateOf(false) }
    var selectedHadithKey by rememberSaveable { mutableStateOf<String?>(null) }
    val repository = remember { LocalHadithRepository() }
    val entries = remember(repository) { repository.all() }
    val context = LocalContext.current
    val audioController = remember(context) { HadithAudioController(context.applicationContext) }
    val audioState by audioController.state.collectAsStateWithLifecycle()

    val destination = Destination.valueOf(destinationName)
    val mode = ReadingMode.valueOf(modeName)
    val language = Language.valueOf(languageName)
    val hadith = entries.firstOrNull { it.stableKey == selectedHadithKey } ?: entries.firstOrNull()

    DisposableEffect(audioController) {
        onDispose { audioController.release() }
    }
    LaunchedEffect(hadith?.stableKey) {
        hadith?.let(audioController::prepare)
    }

    val selectedWord = if (hadith == null) {
        -1
    } else if (audioState.usingTimingPreview) {
        wordIndexAtTime(
            positionSeconds = audioState.positionSeconds,
            durationSeconds = hadith.durationSeconds,
            wordCount = hadith.words.size,
        )
    } else {
        wordIndexAtTiming(audioState.positionSeconds, audioState.wordTimings)
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
            Destination.LISTEN -> if (hadith == null) {
                EmptyRepositoryScreen(Modifier.padding(innerPadding))
            } else {
                ListenScreen(
                    hadith = hadith,
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
                                durationSeconds = hadith.durationSeconds,
                                wordCount = hadith.words.size,
                            )
                        } else {
                            null
                        }
                        seekTime?.let(audioController::seekTo)
                    },
                )
            }

            Destination.LIBRARY -> LibraryScreen(
                modifier = Modifier.padding(innerPadding),
                onCollectionSelected = { collection ->
                    entries.firstOrNull { it.collection == collection.title }?.let { selected ->
                        selectedHadithKey = selected.stableKey
                        destinationName = Destination.LISTEN.name
                    }
                },
            )

            Destination.SEARCH -> SearchScreen(
                repository = repository,
                modifier = Modifier.padding(innerPadding),
                onResultSelected = { selected ->
                    selectedHadithKey = selected.stableKey
                    destinationName = Destination.LISTEN.name
                },
            )
        }
    }
}

@Composable
private fun ListenScreen(
    hadith: HadithEntry,
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

        item {
            NarrationChain(hadith)
        }

        if (!quietMode) {
            item {
                ModeChooser(mode = mode, onModeChange = onModeChange)
            }
        }

        item {
            ArabicHero(
                hadith = hadith,
                selectedWord = selectedWord,
                onWordSelected = onWordSelected,
            )
        }

        if (!quietMode && mode == ReadingMode.STUDY && selectedWord in hadith.words.indices) {
            item {
                WordInsight(hadith.words[selectedWord])
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
                    text = hadith.english,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (!quietMode && (language == Language.URDU || language == Language.BOTH)) {
            item {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    Text(
                        text = hadith.urdu,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Right,
                        color = MaterialTheme.colorScheme.onSurface,
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
                hadith.words.forEachIndexed { index, word ->
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
                                contentDescription = "${word.arabic}, ${word.gloss}"
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
                        "Narrated by ${hadith.narrator}",
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
            Text(
                text = "${word.transliteration}  ·  ${word.gloss}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = word.root?.let { "Root  $it" }
                    ?: word.grammaticalCategory.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    onCollectionSelected: (LibraryCollection) -> Unit,
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
        items(HadithCollections) { collection ->
            val contentColor = if (collection.isAvailable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = collection.isAvailable,
                        role = Role.Button,
                    ) { onCollectionSelected(collection) }
                    .padding(vertical = 16.dp)
                    .semantics {
                        contentDescription = if (collection.isAvailable) {
                            "Open ${collection.title}"
                        } else {
                            "${collection.title}, coming next"
                        }
                        stateDescription = if (collection.isAvailable) "Available" else "Unavailable"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        collection.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                    )
                    Text(
                        collection.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    collection.status,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (collection.isAvailable) MaterialTheme.colorScheme.primary else contentColor,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SearchScreen(
    repository: HadithRepository,
    modifier: Modifier,
    onResultSelected: (HadithEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = repository.search(query)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = "Search", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                singleLine = true,
                placeholder = { Text("Search hadith") },
                label = { Text("Find a teaching") },
            )
        }
        item {
            Text(
                text = if (query.isBlank()) "Try “intentions”" else "Results",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(results) { result ->
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
                    Text(
                        result.english,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (results.isEmpty()) {
            item {
                Text(
                    text = "No hadith found in this preview.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
                            Destination.LIBRARY -> Icons.Filled.MenuBook
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
