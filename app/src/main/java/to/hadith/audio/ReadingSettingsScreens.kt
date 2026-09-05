@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package to.hadith.audio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(state: ReadingState, action: ReadingDispatch) {
    Column {
        PageToolbar("Settings", { action(ReadingAction.Back) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            PageHeading("Your reading space")
            Eyebrow("Reading", Modifier.padding(bottom = 8.dp))
            SettingRow("Appearance", state.settings.appearance.label, onClick = { action(ReadingAction.Go(ReadingPage.APPEARANCE)) })
            SettingRow("Translation language", state.settings.language.label, onClick = { action(ReadingAction.Sheet(ReaderSheet.LANGUAGE)) })
            ToggleRow("Word meanings", state.settings.wordMeanings, "Show available meanings in Study mode.") { action(ReadingAction.Settings(state.settings.copy(wordMeanings = it))) }
            Eyebrow("Listening", Modifier.padding(top = 28.dp, bottom = 8.dp))
            SettingRow("Playback", "${state.settings.speed.clean()}×", onClick = { action(ReadingAction.Sheet(ReaderSheet.PLAYBACK)) })
            SettingRow("Downloads", "${state.offlineRecords.size} offline", onClick = { action(ReadingAction.Go(ReadingPage.DOWNLOADS)) })
            Eyebrow("About", Modifier.padding(top = 28.dp, bottom = 8.dp))
            SettingRow("Sources & about", onClick = { action(ReadingAction.Go(ReadingPage.SOURCES)) })
            SettingRow("Report an error", caption = if (state.current == null) "Open a passage to include its reference." else "${state.current.ref.title} · ${state.current.ref.normalizedNumber}", onClick = { action(ReadingAction.Go(ReadingPage.REPORT)) })
            SettingRow("Open-source licences", onClick = { action(ReadingAction.Go(ReadingPage.LICENCES)) })
            Text("hadith.to", Modifier.padding(top = 36.dp), fontFamily = ReadingSerif, fontSize = 26.sp)
            Text("Version ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AppearanceScreen(state: ReadingState, action: ReadingDispatch) {
    val settings = state.settings
    var fontMenu by remember { mutableStateOf(false) }
    Column {
        PageToolbar("Appearance", { action(ReadingAction.Back) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Spacer(Modifier.height(24.dp))
            Eyebrow("Page tone", Modifier.padding(bottom = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadingAppearance.entries.forEach { appearance ->
                    val selected = settings.appearance == appearance
                    val background = when (appearance) { ReadingAppearance.LIGHT -> Color(0xFFEEF1F3); ReadingAppearance.SEPIA -> Color(0xFFF3EDDF); ReadingAppearance.DARK -> Color(0xFF171C20) }
                    val ink = if (appearance == ReadingAppearance.DARK) Color(0xFFE8EBEC) else Color(0xFF182026)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(Modifier.fillMaxWidth().height(88.dp).semantics { this.selected = selected; contentDescription = "${appearance.label} appearance" }
                            .clickable { action(ReadingAction.Settings(settings.copy(appearance = appearance))) }, color = background, shape = RoundedCornerShape(8.dp), border = BorderStroke(if (selected) 2.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Box(contentAlignment = Alignment.Center) { Text("Aa", fontFamily = ReadingSerif, fontSize = 27.sp, color = ink) }
                        }
                        Text(appearance.label, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Surface(Modifier.fillMaxWidth().padding(vertical = 28.dp), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("Reading preview")
                    Text(FirstHadith.words.take(3).joinToString(" ") { it.arabic }, Modifier.fillMaxWidth().padding(top = 14.dp), fontFamily = settings.typeface.family(), fontSize = settings.arabicSize.sp,
                        lineHeight = (settings.arabicSize * settings.lineSpacing).sp, textAlign = TextAlign.Center)
                    Text(FirstHadith.english.substringBefore(",") + ".", Modifier.padding(top = 14.dp), fontFamily = ReadingSerif, fontSize = settings.translationSize.sp, lineHeight = (settings.translationSize * 1.65f).sp, textAlign = TextAlign.Center)
                }
            }
            Box {
                SettingRow("Arabic typeface", settings.typeface.label, onClick = { fontMenu = true })
                DropdownMenu(fontMenu, { fontMenu = false }) { ArabicTypeface.entries.forEach { font -> DropdownMenuItem({ Text(font.label) }, { fontMenu = false; action(ReadingAction.Settings(settings.copy(typeface = font))) }) } }
            }
            ReadingSlider("Arabic size", "${settings.arabicSize.toInt()}", settings.arabicSize, 24f..44f) { action(ReadingAction.Settings(settings.copy(arabicSize = it))) }
            ReadingSlider("Translation size", "${settings.translationSize.toInt()}", settings.translationSize, 14f..26f) { action(ReadingAction.Settings(settings.copy(translationSize = it))) }
            ReadingSlider("Line spacing", "${settings.lineSpacing.clean()}×", settings.lineSpacing, 1.4f..2.3f) { action(ReadingAction.Settings(settings.copy(lineSpacing = it))) }
            ReadingSlider("Space between words", settings.wordSpacing.clean(), settings.wordSpacing, 0f..12f) { action(ReadingAction.Settings(settings.copy(wordSpacing = it))) }
            TextButton({ action(ReadingAction.Settings(settings.copy(appearance = ReadingAppearance.LIGHT, typeface = ArabicTypeface.SCHEHERAZADE, arabicSize = 32f, translationSize = 18f, lineSpacing = 1.8f, wordSpacing = 2f))) }, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) { Text("Restore reading defaults") }
        }
    }
}

@Composable
private fun ReadingSlider(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        ReadingSliderControl(value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = title }, valueRange = range)
        Rule()
    }
}

@Composable
internal fun SourcesScreen(state: ReadingState, action: ReadingDispatch) {
    val context = LocalContext.current
    fun visit(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { /* No browser is installed on this device. */ }
    }
    Column {
        PageToolbar("Sources & about", { action(ReadingAction.Back) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeading("hadith.to", "Read. Listen. Reflect.")
            Text("Hadith text, translations, and audio in one reading space.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp)); Rule()
            SourceDetail("Arabic text & numbering", "Every passage keeps its source reference.", "Arabic passages come from Hadith.to’s collection data. Exact hadith numbers, including letter suffixes, are preserved.")
            SourceDetail("Translation sources", "English and Urdu, where available.", "Translations come from Hadith.to’s collection sidecars and its versioned Hadith API source. Word meanings are shown where published with the passage or audio timing. Missing translations and meanings are labelled unavailable.")
            SourceDetail("Audio & word timings", "Hadith recordings with synchronized Arabic.", "Recordings and word timings come from Hadith.to’s audio sources. Generated narration is labelled Synthetic narration in the player.")
            SourceDetail("Your reading library", "Saved privately on this device.", "Saved passages, words, recent reading, downloads, and preferences stay on this device. Optional error reports send the passage reference and details you choose to submit.")
            SettingRow("Visit Hadith.to", onClick = { visit("https://www.hadith.to/") })
            state.current?.let { record -> SettingRow("Open this passage on the website", onClick = { visit(record.ref.url) }) }
            SettingRow("View source code", onClick = { visit("https://github.com/dfordev1/hadithaudioapp") })
            SettingRow("Privacy policy", onClick = { visit("https://github.com/dfordev1/hadithaudioapp/blob/main/docs/PRIVACY.md") })
            SettingRow("Fonts & open-source licences", onClick = { action(ReadingAction.Go(ReadingPage.LICENCES)) })
        }
    }
}


@Composable
private fun SourceDetail(title: String, caption: String, detail: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingRow(title, caption = caption, onClick = { expanded = !expanded }, trailing = { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) })
    if (expanded) Text(detail, Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal val reportCategories = linkedMapOf("arabic_text" to "Arabic text", "translation" to "Translation or meaning", "audio_unavailable" to "Audio playback", "word_timing" to "Word timing", "metadata" to "Hadith reference", "other" to "Something else")

@Composable
internal fun ReportScreen(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val record = state.current
    var category by rememberSaveable(record?.key) { mutableStateOf("arabic_text") }
    var note by rememberSaveable(record?.key) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val word = displayedReadingWords(state, audio).getOrNull(state.wordIndex ?: -1)
    var includeWord by rememberSaveable(record?.key, state.wordIndex) { mutableStateOf(word != null) }
    Column {
        PageToolbar("Report an error", { action(ReadingAction.Back) })
        if (record == null) EmptyReading("Help keep the text accurate", "Open the passage you want to report, then choose Report an error from the reader tools.", Icons.Outlined.Flag, "Open library") { action(ReadingAction.Go(ReadingPage.LIBRARY)) }
        else Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Spacer(Modifier.height(24.dp))
            Eyebrow("Passage reference")
            Text("${record.ref.title} · ${record.ref.normalizedNumber}", Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.titleMedium)
            Rule()
            Spacer(Modifier.height(24.dp))
            Box {
                OutlinedButton({ menu = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(8.dp)) {
                    Text(reportCategories[category].orEmpty(), Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(menu, { menu = false }) { reportCategories.forEach { (value, title) -> DropdownMenuItem({ Text(title) }, { menu = false; category = value }) } }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(note, { note = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("What needs attention?") }, placeholder = { Text("Tell us what you noticed and where.") }, minLines = 5, maxLines = 10, shape = RoundedCornerShape(8.dp), enabled = !state.sendingReport)
            Text("${note.length}/2000", Modifier.fillMaxWidth().padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (word != null) ToggleRow("Include selected word", includeWord, word.arabic) { includeWord = it }
            Text("The passage reference, error category, your note, and any selected word will be sent to Hadith.to. No account is needed.", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.reportResult != null) Text(state.reportResult, Modifier.padding(bottom = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            if (state.reportResult?.startsWith("Thank you") == true) PrimaryAction("Done", Modifier.fillMaxWidth()) { action(ReadingAction.Back) }
            else PrimaryAction(if (state.sendingReport) "Sending…" else "Send report", Modifier.fillMaxWidth(), enabled = !state.sendingReport && note.isNotBlank()) { action(ReadingAction.Report(category, note.trim(), includeWord)) }
        }
    }
}

@Composable
internal fun LicencesScreen(action: ReadingDispatch) {
    val context = LocalContext.current
    val licences by produceState<List<Pair<String, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            listOf("Source Serif 4" to "source-serif-OFL.txt", "Scheherazade New" to "scheherazade-OFL.txt", "Amiri" to "amiri-OFL.txt", "Noto Naskh Arabic" to "OFL.txt")
                .map { (title, file) -> title to context.assets.open("font-licenses/$file").bufferedReader().use { it.readText() } }
        }
    }
    Column {
        PageToolbar("Open-source licences", { action(ReadingAction.Back) })
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Text("Typography & software", style = MaterialTheme.typography.headlineMedium) }
            item { Text("Built with Android Jetpack Compose, Material Components, Media3, and Kotlin coroutines. These libraries use the Apache License 2.0. The bundled typefaces use the SIL Open Font License below.", style = MaterialTheme.typography.bodyMedium) }
            licences.forEach { (title, text) -> item { Text(title, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp)); Text(text, style = MaterialTheme.typography.bodySmall); Rule(Modifier.padding(top = 16.dp)) } }
        }
    }
}
