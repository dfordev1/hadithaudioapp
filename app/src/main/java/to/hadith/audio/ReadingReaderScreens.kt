@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package to.hadith.audio

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun ReaderScreen(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val current = state.current
    val scroll = rememberLazyListState()
    LaunchedEffect(current?.key) { scroll.scrollToItem(0) }
    Column {
        if (state.focused) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ReadingIcon(Icons.Outlined.CloseFullscreen, "Exit focus", onClick = { action(ReadingAction.ToggleFocus) })
                Spacer(Modifier.weight(1f))
                TextButton({ action(ReadingAction.Go(ReadingPage.APPEARANCE)) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Aa", fontFamily = ReadingSerif, fontSize = 21.sp) }
            }
        } else PageToolbar(current?.ref?.title ?: state.requestedRef?.title ?: "Reader", { action(ReadingAction.Back) }) {
            TextButton({ action(ReadingAction.Go(ReadingPage.APPEARANCE)) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Aa", fontFamily = ReadingSerif, fontSize = 21.sp, modifier = Modifier.semantics { contentDescription = "Appearance" }) }
            if (current != null) ReadingIcon(if (state.isSaved(current.ref)) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (state.isSaved(current.ref)) "Unsave this hadith" else "Save this hadith", tint = MaterialTheme.colorScheme.primary, onClick = { action(ReadingAction.Save(current.ref)) })
            ReadingIcon(Icons.Outlined.MoreHoriz, "Reader tools", onClick = { action(ReadingAction.Sheet(ReaderSheet.TOOLS)) })
        }
        if (state.loading) LoadingReading()
        else if (state.error != null && current == null) CatalogFailure(state, action)
        else if (current != null) LazyColumn(state = scroll, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
            item {
                if (!state.focused) {
                    Eyebrow(current.entry.book, Modifier.padding(top = 14.dp))
                    Text("Hadith ${current.ref.normalizedNumber}", Modifier.padding(top = 8.dp, bottom = 14.dp), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth()) {
                        ReadingTab("Reading", !state.study, Modifier.weight(1f)) { if (state.study) action(ReadingAction.ToggleStudy) }
                        ReadingTab("Study", state.study, Modifier.weight(1f)) { if (!state.study) action(ReadingAction.ToggleStudy) }
                    }
                    Spacer(Modifier.height(28.dp))
                    if (state.study) Text("Tap a word to hear it or explore its meaning.", Modifier.padding(bottom = 18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Eyebrow("${current.ref.title} · ${current.ref.normalizedNumber}", Modifier.padding(top = 28.dp, bottom = 32.dp))
            }
            item { ArabicPassage(state, audio, action) }
            item {
                if (current.entry.passageLabel == "Opening passage") Text("Opening passage", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                TextButton({ action(ReadingAction.ToggleMeaning) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
                    Text(if (state.meaning) "Hide meaning" else "Show meaning")
                    Icon(if (state.meaning) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
                }
                if (state.meaning) TranslationBlock(current.entry, state.settings)
                if (!state.focused) {
                    Rule(Modifier.padding(top = 28.dp, bottom = 12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ action(ReadingAction.Neighbor(false)) }, enabled = state.previous != null) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null); Text("Previous") }
                        Spacer(Modifier.weight(1f))
                        TextButton({ action(ReadingAction.Neighbor(true)) }, enabled = state.next != null) { Text("Next"); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArabicPassage(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val words = remember(state.current, audio.timedWords, audio.timedMeanings) { displayedReadingWords(state, audio) }
    val active = if (audio.isPlaying) wordIndexAtTiming(audio.positionSeconds, audio.wordTimings) else -1
    val settings = state.settings
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(settings.wordSpacing.dp), verticalArrangement = Arrangement.spacedBy(if (state.study) 16.dp else 2.dp)) {
            words.forEachIndexed { index, word ->
                val highlighted = index == active
                Column(Modifier.clip(RoundedCornerShape(5.dp)).background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable(role = Role.Button, onClickLabel = "Explore ${word.arabic}") { action(ReadingAction.Word(index)) }
                    .padding(horizontal = 3.dp, vertical = if (state.study) 4.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(word.arabic, fontFamily = settings.typeface.family(), fontSize = settings.arabicSize.sp,
                        lineHeight = (settings.arabicSize * settings.lineSpacing).sp, color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                    if (state.study && settings.wordMeanings) {
                        if (settings.language != ReadingLanguage.URDU && word.gloss.isNotBlank()) Text(word.gloss, Modifier.widthIn(max = 135.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (settings.language != ReadingLanguage.ENGLISH && word.urduGloss.isNotBlank()) Text(word.urduGloss, fontFamily = ReadingNaskh, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TranslationBlock(entry: HadithEntry, settings: ReadingSettings) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (settings.language != ReadingLanguage.URDU) {
            Eyebrow("English translation")
            Text(entry.english.ifBlank { "English translation is not available for this passage." }, fontFamily = ReadingSerif, fontSize = settings.translationSize.sp, lineHeight = (settings.translationSize * 1.65f).sp)
        }
        if (settings.language != ReadingLanguage.ENGLISH) {
            Eyebrow("Urdu translation")
            Text(entry.urdu.ifBlank { "Urdu translation is not available for this passage." }, Modifier.fillMaxWidth(), fontFamily = if (entry.urdu.isBlank()) ReadingSerif else ReadingNaskh,
                fontSize = (settings.translationSize + if (entry.urdu.isBlank()) 0 else 3).sp, lineHeight = (settings.translationSize * 2f).sp, textAlign = if (entry.urdu.isBlank()) TextAlign.Start else TextAlign.End)
        }
    }
}

@Composable
internal fun AudioProgress(audio: AudioUiState, action: ReadingDispatch) {
    Column {
        ReadingSliderControl(audio.positionSeconds.coerceIn(0f, audio.durationSeconds.coerceAtLeast(1f)), { action(ReadingAction.Seek(it)) }, Modifier.fillMaxWidth().semantics { contentDescription = "Audio position" },
            enabled = audio.canPlay(), valueRange = 0f..audio.durationSeconds.coerceAtLeast(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clockTime(audio.positionSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(clockTime(audio.durationSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReaderDock(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    Column(Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(audio, { action(ReadingAction.PlayPause) })
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(if (audio.usingTimingPreview) "Audio unavailable" else if (audio.isLoading) "Preparing audio…" else "${clockTime(audio.positionSeconds)} / ${clockTime(audio.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
                if (audio.usingTimingPreview) TextButton({ action(ReadingAction.Retry) }, contentPadding = PaddingValues(0.dp)) { Text("Retry audio") }
                else ReadingSliderControl(audio.positionSeconds.coerceIn(0f, audio.durationSeconds.coerceAtLeast(1f)), { action(ReadingAction.Seek(it)) }, Modifier.fillMaxWidth().semantics { contentDescription = "Audio position" }, enabled = audio.canPlay(), valueRange = 0f..audio.durationSeconds.coerceAtLeast(1f))
            }
            TextButton({ action(ReadingAction.Sheet(ReaderSheet.PLAYBACK)) }, Modifier.heightIn(min = 48.dp)) { Text("${state.settings.speed.clean()}×") }
        }
    }
}

@Composable
internal fun PlayerScreen(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val current = state.current
    Column {
        PageToolbar("Now playing", { action(ReadingAction.Go(ReadingPage.LIBRARY)) }) {
            if (current != null) ReadingIcon(if (state.isSaved(current.ref)) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (state.isSaved(current.ref)) "Unsave this hadith" else "Save this hadith", tint = MaterialTheme.colorScheme.primary, onClick = { action(ReadingAction.Save(current.ref)) })
        }
        if (state.loading) LoadingReading()
        else if (state.error != null && current == null) CatalogFailure(state, action)
        else if (current != null) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow(current.ref.title, Modifier.padding(top = 10.dp))
            Text("Hadith ${current.ref.normalizedNumber}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineLarge)
            Text(current.entry.book, Modifier.padding(top = 10.dp, bottom = 32.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Rule()
            val words = displayedReadingWords(state, audio)
            val index = wordIndexAtTiming(audio.positionSeconds, audio.wordTimings).coerceAtLeast(0)
            val excerpt = words.drop((index / 9) * 9).take(9).joinToString(" ") { it.arabic }
            Text(excerpt, Modifier.fillMaxWidth().padding(vertical = 32.dp), fontFamily = state.settings.typeface.family(), fontSize = state.settings.arabicSize.sp,
                lineHeight = (state.settings.arabicSize * 1.8f).sp, textAlign = TextAlign.Center)
            Rule()
            Text(if (audio.usingTimingPreview) "Audio unavailable" else if (audio.isLoading) "Preparing audio…" else if (audio.isSynthetic) "Synthetic narration" else "Hadith audio", Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (audio.usingTimingPreview) TextButton({ action(ReadingAction.Retry) }) { Text("Retry audio") }
            AudioProgress(audio, action)
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ReadingIcon(Icons.Outlined.SkipPrevious, "Previous hadith", state.previous != null, onClick = { action(ReadingAction.Neighbor(false)) })
                ReadingIcon(Icons.Outlined.Replay10, "Back 10 seconds", audio.canPlay(), onClick = { action(ReadingAction.Skip(-10)) })
                PlayButton(audio, { action(ReadingAction.PlayPause) }, large = true)
                ReadingIcon(Icons.Outlined.Forward10, "Forward 10 seconds", audio.canPlay(), onClick = { action(ReadingAction.Skip(10)) })
                ReadingIcon(Icons.Outlined.SkipNext, "Next hadith", state.next != null, onClick = { action(ReadingAction.Neighbor(true)) })
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton({ action(ReadingAction.Sheet(ReaderSheet.PLAYBACK)) }) { Text("${state.settings.speed.clean()}× speed") }
                TextButton({ action(ReadingAction.Settings(state.settings.copy(repeat = !state.settings.repeat))) }) { Text(if (state.settings.repeat) "Repeat on" else "Repeat off") }
                TextButton({ action(ReadingAction.Sheet(ReaderSheet.PLAYBACK)) }) { Text(if (state.sleepAtEnd) "Until end" else if (state.sleepMinutes > 0) "${state.sleepMinutes} min" else "Sleep timer") }
            }
            OutlinedButton({ action(ReadingAction.Go(ReadingPage.READER)) }, Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.MenuBook, null, Modifier.padding(end = 10.dp).size(18.dp)); Text("Open reader") }
        }
    }
}

internal fun Float.clean(): String = if (this == toInt().toFloat()) toInt().toString() else "%.2f".format(Locale.ROOT, this).trimEnd('0')

@Composable
internal fun ReadingSheets(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    if (state.sheet == ReaderSheet.NONE) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { action(ReadingAction.Sheet(ReaderSheet.NONE)) }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            when (state.sheet) {
                ReaderSheet.WORD -> WordSheet(state, audio, action)
                ReaderSheet.PLAYBACK -> PlaybackSheet(state, action)
                ReaderSheet.TOOLS -> ToolsSheet(state, action)
                ReaderSheet.LANGUAGE -> {
                    Text("Translation language", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    ReadingLanguage.entries.forEach { language -> SettingRow(language.label, onClick = { action(ReadingAction.Settings(state.settings.copy(language = language))); action(ReadingAction.Sheet(ReaderSheet.NONE)) }, trailing = {
                        RadioButton(state.settings.language == language, { action(ReadingAction.Settings(state.settings.copy(language = language))); action(ReadingAction.Sheet(ReaderSheet.NONE)) }, Modifier.semantics { contentDescription = language.label })
                    }) }
                }
                ReaderSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun WordSheet(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val word = displayedReadingWords(state, audio).getOrNull(state.wordIndex ?: -1) ?: return
    val ref = state.current?.ref ?: return
    val saved = state.library.words.any { it.key == SavedWord(ref, word).key }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("Word meaning", Modifier.weight(1f))
        ReadingIcon(Icons.Outlined.Close, "Close word meaning", onClick = { action(ReadingAction.Sheet(ReaderSheet.NONE)) })
    }
    Text(word.arabic, Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), fontFamily = state.settings.typeface.family(), fontSize = 44.sp, lineHeight = 72.sp, textAlign = TextAlign.Center)
    if (word.transliteration.isNotBlank()) Text(word.transliteration, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(word.gloss.ifBlank { "Meaning unavailable for this word" }, Modifier.fillMaxWidth().padding(top = 18.dp), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    if (word.urduGloss.isNotBlank()) Text(word.urduGloss, Modifier.fillMaxWidth().padding(top = 12.dp), fontFamily = ReadingNaskh, fontSize = 22.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton({ action(ReadingAction.HearWord) }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = audio.canPlay() && audio.wordTimings.any { it.wordIndex == state.wordIndex }, shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Hear word") }
        OutlinedButton({ action(ReadingAction.SaveWord(word)) }, Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp)) { Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (saved) "Saved" else "Save word") }
    }
    Spacer(Modifier.height(20.dp)); Rule()
    word.root?.takeIf { it.isNotBlank() }?.let { SettingRow("Root", it) }
    SettingRow("In ${ref.title} · ${ref.normalizedNumber}", onClick = { action(ReadingAction.Sheet(ReaderSheet.NONE)) })
    TextButton({ action(ReadingAction.Go(ReadingPage.REPORT)) }, Modifier.fillMaxWidth()) { Text("Report an issue") }
}

@Composable
private fun PlaybackSheet(state: ReadingState, action: ReadingDispatch) {
    Text("Playback", style = MaterialTheme.typography.headlineSmall)
    Eyebrow("Playback speed", Modifier.padding(top = 24.dp, bottom = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed -> Choice("${speed.clean()}×", state.settings.speed == speed) { action(ReadingAction.Settings(state.settings.copy(speed = speed))) } }
    }
    Spacer(Modifier.height(16.dp))
    ToggleRow("Repeat this hadith", state.settings.repeat) { action(ReadingAction.Settings(state.settings.copy(repeat = it))) }
    ToggleRow("Continue to the next hadith", state.settings.autoplay) { action(ReadingAction.Settings(state.settings.copy(autoplay = it))) }
    Eyebrow("Sleep timer", Modifier.padding(top = 24.dp, bottom = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 15, 30).forEach { minutes -> Choice(if (minutes == 0) "Off" else "$minutes min", state.sleepMinutes == minutes && !state.sleepAtEnd) { action(ReadingAction.Sleep(minutes)) } }
        Choice("End of hadith", state.sleepAtEnd) { action(ReadingAction.Sleep(atEnd = true)) }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryAction("Done", Modifier.fillMaxWidth()) { action(ReadingAction.Sheet(ReaderSheet.NONE)) }
}

@Suppress("DEPRECATION")
@Composable
private fun ToolsSheet(state: ReadingState, action: ReadingDispatch) {
    val record = state.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Text("Reading tools", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    SettingRow("Focus on the passage", onClick = { action(ReadingAction.ToggleFocus) })
    SettingRow("Appearance", onClick = { action(ReadingAction.Go(ReadingPage.APPEARANCE)) })
    SettingRow("Translation language", state.settings.language.label, onClick = { action(ReadingAction.Sheet(ReaderSheet.LANGUAGE)) })
    SettingRow("Playback", onClick = { action(ReadingAction.Sheet(ReaderSheet.PLAYBACK)) })
    if (record != null) {
        val downloaded = state.offlineRecords.any { it.key == record.key }
        SettingRow(if (downloaded) "View downloaded passage" else "Download audio & text", onClick = {
            if (!downloaded) action(ReadingAction.Download(record.ref)); action(ReadingAction.Go(ReadingPage.DOWNLOADS))
        })
        SettingRow("Copy passage", onClick = { clipboard.setText(AnnotatedString("${record.entry.arabic}\n\n${record.entry.english}\n\n${record.ref.title} · ${record.ref.normalizedNumber}\n${record.ref.url}")); action(ReadingAction.Sheet(ReaderSheet.NONE)) })
        SettingRow("Share passage", onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${record.ref.title} · ${record.ref.normalizedNumber}\n${record.ref.url}") }
            context.startActivity(Intent.createChooser(intent, "Share passage")); action(ReadingAction.Sheet(ReaderSheet.NONE))
        })
        SettingRow("Report an error", onClick = { action(ReadingAction.Go(ReadingPage.REPORT)) })
    }
    SettingRow("Sources & about", onClick = { action(ReadingAction.Go(ReadingPage.SOURCES)) })
}
