package to.hadith.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val arabicCollections = mapOf("bukhari" to "صحيح البخاري", "muslim" to "صحيح مسلم", "abudawud" to "سنن أبي داود",
    "tirmidhi" to "جامع الترمذي", "nasai" to "سنن النسائي", "ibnmajah" to "سنن ابن ماجه", "malik" to "موطأ مالك",
    "riyad" to "رياض الصالحين", "musnad-ahmad" to "مسند أحمد", "nawawi40" to "الأربعون النووية", "qudsi40" to "الأحاديث القدسية",
    "shahwaliullah40" to "أربعون حديثًا")

@Composable
internal fun LibraryScreen(state: ReadingState, action: ReadingDispatch) {
    var savedWords by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp).heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("hadith.to", Modifier.weight(1f), fontFamily = ReadingSerif, fontSize = 27.sp, letterSpacing = (-.8).sp)
            ReadingIcon(Icons.Outlined.FileDownload, "Downloads", onClick = { action(ReadingAction.Go(ReadingPage.DOWNLOADS)) })
            ReadingIcon(Icons.Outlined.Settings, "Settings", onClick = { action(ReadingAction.Go(ReadingPage.SETTINGS)) })
        }
        Rule(Modifier.padding(horizontal = 24.dp))
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            item { PageHeading("Your library", "A place to listen, read, and return.") }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryTab.entries.forEach { tab -> Choice(tab.name.lowercase().replaceFirstChar { it.uppercase() }, state.tab == tab) { action(ReadingAction.Tab(tab)) } }
                }
                Rule(Modifier.padding(top = 10.dp, bottom = 20.dp))
            }
            if (!state.online) item { OfflineNotice { action(ReadingAction.Go(ReadingPage.DOWNLOADS)) }; Spacer(Modifier.height(20.dp)) }
            when (state.tab) {
                LibraryTab.COLLECTIONS -> {
                    val recent = state.library.recent.firstOrNull()
                    if (recent != null) item {
                        Eyebrow("Continue reading")
                        Surface(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).clickable { action(ReadingAction.Open(recent.ref)) }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(recent.ref.title, style = MaterialTheme.typography.titleMedium)
                                    Text("Hadith ${recent.ref.normalizedNumber}" + if (recent.position > 0) " · ${clockTime(recent.position)}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShelfFilter.entries.forEach { filter -> Choice(filter.label, state.filter == filter) { action(ReadingAction.Filter(filter)) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    val list = CatalogCollections.filterIndexed { index, c -> when (state.filter) { ShelfFilter.ALL -> true; ShelfFilter.SIX -> index < 6; ShelfFilter.FORTY -> c.kind == CatalogCollection.Kind.FORTY } }
                    items(list, key = { it.slug }) { collection ->
                        Row(Modifier.fillMaxWidth().clickable { action(ReadingAction.Collection(collection)) }.padding(vertical = 19.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(collection.title, style = MaterialTheme.typography.titleLarge)
                                Text(buildList { collection.bookCount?.let { add("$it books") }; collection.totalCount?.let { add("%,d hadith".format(Locale.ROOT, it)) } }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(arabicCollections[collection.slug].orEmpty(), fontFamily = ReadingArabic, fontSize = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Rule()
                    }
                    item { Eyebrow("12 collections · one quiet place", Modifier.padding(top = 28.dp)) }
                }
                LibraryTab.SAVED -> {
                    item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Choice("Hadiths (${state.library.saved.size})", !savedWords) { savedWords = false }
                        Choice("Words (${state.library.words.size})", savedWords) { savedWords = true }
                    } }
                    if (savedWords) {
                        if (state.library.words.isEmpty()) item { EmptyReading("Words to return to", "Tap a word while reading, then save it here.", Icons.Outlined.BookmarkBorder) }
                        items(state.library.words, key = { it.key }) { word ->
                            Column(Modifier.fillMaxWidth().clickable { action(ReadingAction.Open(word.ref)) }.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(word.word.arabic, Modifier.fillMaxWidth(), fontFamily = state.settings.typeface.family(), fontSize = 32.sp, textAlign = TextAlign.End)
                                Text(word.word.gloss.ifBlank { "Meaning unavailable for this word" }, style = MaterialTheme.typography.bodyLarge)
                                Text("${word.ref.title} · ${word.ref.normalizedNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }; Rule()
                        }
                    } else {
                        if (state.library.saved.isEmpty()) item { EmptyReading("Keep a passage close", "Save a hadith while reading. You’ll find it here whenever you return.", Icons.Outlined.BookmarkBorder, "Explore collections") { action(ReadingAction.Tab(LibraryTab.COLLECTIONS)) } }
                        items(state.savedRecords, key = { it.key }) { record -> PassageRow(record, true, { action(ReadingAction.Open(record.ref)) }, { action(ReadingAction.Save(record.ref)) }) }
                    }
                }
                LibraryTab.RECENT -> {
                    if (state.library.recent.isEmpty()) item { EmptyReading("Begin anywhere", "The passages you open will be waiting here when you return.", Icons.Outlined.History, "Explore collections") { action(ReadingAction.Tab(LibraryTab.COLLECTIONS)) } }
                    items(state.library.recent, key = { it.ref.key }) { recent ->
                        val record = state.opened.firstOrNull { it.key == recent.ref.key }
                        if (record != null) PassageRow(record, state.isSaved(record.ref), { action(ReadingAction.Open(record.ref)) }, subtitle = if (recent.position > 0) "Resume at ${clockTime(recent.position)}" else record.entry.book)
                        else SettingRow("${recent.ref.title} · ${recent.ref.normalizedNumber}", onClick = { action(ReadingAction.Open(recent.ref)) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CollectionScreen(state: ReadingState, action: ReadingDispatch) {
    Column {
        PageToolbar("Collections", { action(ReadingAction.Back) }) { ReadingIcon(Icons.Outlined.FileDownload, "Downloads", onClick = { action(ReadingAction.Go(ReadingPage.DOWNLOADS)) }) }
        if (state.loading) LoadingReading()
        else if (state.error != null) CatalogFailure(state, action)
        else LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            item {
                Text(arabicCollections[state.collection.slug].orEmpty(), Modifier.fillMaxWidth().padding(top = 16.dp), fontFamily = ReadingArabic, fontSize = 30.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                PageHeading(state.collection.title, "${state.books.size} ${if (state.collection.kind == CatalogCollection.Kind.MUSNAD) "parts" else "books"} · ${state.books.sumOf { it.count }} hadith")
                Eyebrow("Contents", Modifier.padding(bottom = 16.dp)); Rule()
            }
            items(state.books, key = { it.number }) { book ->
                Row(Modifier.fillMaxWidth().clickable { action(ReadingAction.Book(book)) }.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(book.number.toString().padStart(2, '0'), Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                        Text("${book.count} hadith · ${book.from}–${book.to}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }; Rule()
            }
        }
    }
}

@Composable
internal fun HadithListScreen(state: ReadingState, action: ReadingDispatch) {
    Column {
        PageToolbar(state.collection.title, { action(ReadingAction.Back) }) {
            state.book?.let { book -> ReadingIcon(Icons.Outlined.FileDownload, "Download this book", onClick = { action(ReadingAction.DownloadBook(book)) }) }
        }
        if (state.loading) LoadingReading()
        else if (state.error != null) CatalogFailure(state, action)
        else LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            item {
                Eyebrow("Book ${state.book?.number ?: ""}", Modifier.padding(top = 20.dp))
                PageHeading(state.book?.title.orEmpty(), "${state.hadiths.size} hadith")
                Rule()
            }
            items(state.hadiths, key = { it.number }) { hadith ->
                val ref = CatalogRef(hadith.book.collection.slug, hadith.number)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { action(ReadingAction.Open(ref)) }.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow("Hadith ${hadith.number}")
                        Text(hadith.arabic, Modifier.fillMaxWidth(), fontFamily = ReadingArabic, fontSize = 26.sp, lineHeight = 42.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    ReadingIcon(Icons.Outlined.PlayArrow, "Play hadith ${hadith.number}", tint = MaterialTheme.colorScheme.primary, onClick = { action(ReadingAction.Open(ref, true)) })
                }; Rule()
            }
        }
    }
}

@Composable
internal fun CatalogFailure(state: ReadingState, action: ReadingDispatch) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        EmptyReading(if (state.online) "A moment of interruption" else "Read wherever you are", state.error ?: "You’re offline. Downloaded passages remain available.", if (state.online) Icons.Outlined.CloudOff else Icons.Outlined.WifiOff, "Try again") { action(ReadingAction.Retry) }
        if (!state.online) OutlinedButton({ action(ReadingAction.Go(ReadingPage.DOWNLOADS)) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp)) { Text("Open downloads") }
    }
}

@Composable
internal fun SearchScreen(state: ReadingState, action: ReadingDispatch) {
    var menu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val query = state.query.trim()
    val exact = query.isNotEmpty() && CatalogParser.isValidHadithId(query)
    val local = remember(query, state.searchCollection, state.opened) {
        if (query.isBlank()) emptyList() else state.opened.filter { record ->
            (state.searchCollection == "all" || record.ref.collectionSlug == state.searchCollection) &&
                (record.entry.english.contains(query, true) || record.entry.urdu.contains(query) ||
                    normalizeArabicForTiming(query).takeIf { it.isNotEmpty() }?.let { normalizeArabicForTiming(record.entry.arabic).contains(it) } == true || record.ref.normalizedNumber == query)
        }.take(50)
    }
    LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(24.dp)) {
        item { PageHeading("Search", "Find a passage. Return to a meaning.") }
        item {
            OutlinedTextField(state.query, { action(ReadingAction.Query(it.take(300))) }, Modifier.fillMaxWidth(), label = { Text("Number, Arabic, or English") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) ReadingIcon(Icons.Outlined.Close, "Clear search", onClick = { action(ReadingAction.Query("")) }) },
                singleLine = true, shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); action(ReadingAction.Find) }))
            Spacer(Modifier.height(12.dp))
            Box {
                OutlinedButton({ menu = true }, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(CatalogCollections.firstOrNull { it.slug == state.searchCollection }?.title ?: "All collections")
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 8.dp).size(18.dp))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("All collections") }, { menu = false; action(ReadingAction.SearchCollection("all")) })
                    CatalogCollections.forEach { c -> DropdownMenuItem({ Text(c.title) }, { menu = false; action(ReadingAction.SearchCollection(c.slug)) }) }
                }
            }
            if (exact) PrimaryAction("Find hadith $query", Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !state.searching) { keyboard?.hide(); action(ReadingAction.Find) }
            Text("Look up any hadith number within a collection. Arabic and English text search covers passages opened on this device.", Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Rule()
        }
        if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 20.dp)) }
        if (state.searchMessage != null) item { Text(state.searchMessage, Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodyMedium) }
        val results = (listOfNotNull(state.exactResult) + local).distinctBy { it.key }
        if (query.isBlank()) item { EmptyReading("An invitation to explore", "Try a hadith number, an Arabic word, or a phrase from a passage you’ve opened.", Icons.Outlined.Search) }
        else if (results.isEmpty() && !state.searching && !exact) item { EmptyReading("No matching passages", "Try another phrase, or choose a collection and look up an exact hadith number.", Icons.Outlined.Search) }
        items(results, key = { it.key }) { record -> PassageRow(record, state.isSaved(record.ref), { action(ReadingAction.Open(record.ref)) }, { action(ReadingAction.Save(record.ref)) }) }
    }
}

@Composable
internal fun DownloadsScreen(state: ReadingState, action: ReadingDispatch) {
    var removing by remember { mutableStateOf<ReadingRecord?>(null) }
    Column {
        PageToolbar("Downloads", { action(ReadingAction.Back) })
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            item {
                PageHeading("Always with you", "${state.offlineRecords.size} downloaded · ${readableBytes(state.offlineBytes)}")
                ToggleRow("Download on Wi-Fi only", state.settings.wifiOnly) { action(ReadingAction.Settings(state.settings.copy(wifiOnly = it))) }
                Spacer(Modifier.height(24.dp))
            }
            if (state.downloads.isNotEmpty()) item { Eyebrow("In progress", Modifier.padding(bottom = 12.dp)); Text("Keep the app open while downloading. Paused transfers restart when retried.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.downloads, key = { it.ref.key }) { download ->
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${download.ref.title} · ${download.ref.normalizedNumber}", style = MaterialTheme.typography.titleMedium)
                    if (download.total > 0) LinearProgressIndicator(progress = { (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else if (download.status == DownloadStatus.RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(when (download.status) {
                        DownloadStatus.QUEUED -> "Queued"; DownloadStatus.RUNNING -> "${readableBytes(download.bytes)}${if (download.total > 0) " of ${readableBytes(download.total)}" else " downloaded"}"
                        DownloadStatus.PAUSED -> "Paused"; DownloadStatus.WAITING_WIFI -> "Waiting for Wi-Fi"; DownloadStatus.FAILED -> download.message ?: "Download interrupted"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton({ action(ReadingAction.PauseDownload(download.ref)) }) { Text(if (download.status in setOf(DownloadStatus.FAILED, DownloadStatus.PAUSED)) "Retry" else "Pause") }
                        TextButton({ action(ReadingAction.CancelDownload(download.ref)) }) { Text("Cancel") }
                    }
                }; Rule()
            }
            if (state.offlineRecords.isNotEmpty()) item { Eyebrow("Available offline", Modifier.padding(top = 24.dp, bottom = 12.dp)) }
            items(state.offlineRecords, key = { it.key }) { record ->
                SettingRow("${record.ref.title} · ${record.ref.normalizedNumber}", caption = "Audio and text · ready offline", onClick = { action(ReadingAction.Open(record.ref)) }, trailing = {
                    ReadingIcon(Icons.Outlined.DeleteOutline, "Remove download ${record.ref.normalizedNumber}", onClick = { removing = record })
                })
            }
            if (state.downloads.isEmpty() && state.offlineRecords.isEmpty()) item { EmptyReading("Take a passage with you", "Download a hadith from the reader, or a whole book from its contents page.", Icons.Outlined.FileDownload, "Explore collections") { action(ReadingAction.Go(ReadingPage.LIBRARY)) } }
        }
    }
    removing?.let { record -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove this download?") }, text = { Text("${record.ref.title} · ${record.ref.normalizedNumber}. You can download the audio again. Your saved passage stays in your library.") },
        confirmButton = { TextButton({ action(ReadingAction.RemoveDownload(record.ref)); removing = null }) { Text("Remove") } }, dismissButton = { TextButton({ removing = null }) { Text("Keep") } }) }
}
