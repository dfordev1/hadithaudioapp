package to.hadith.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal typealias ReadingDispatch = (ReadingAction) -> Unit

@Composable
internal fun Rule(modifier: Modifier = Modifier) = HorizontalDivider(modifier, .5.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(Locale.ROOT), modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ReadingIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp), enabled = enabled) { Icon(icon, label, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = .3f), modifier = Modifier.size(22.dp)) }
}

@Composable
internal fun PageToolbar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ReadingIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = onBack)
        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick, modifier.heightIn(min = 48.dp).semantics { this.selected = selected }, shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else androidx.compose.ui.graphics.Color.Transparent)) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ReadingTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, fontFamily = ReadingSerif, fontSize = 16.sp, textAlign = TextAlign.Center, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(if (selected) 2.dp else .5.dp).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
internal fun PrimaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) { Text(text) }
}

@Composable
internal fun SettingRow(title: String, value: String? = null, caption: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else {
            if (value != null) Text(value, Modifier.widthIn(max = 145.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Rule()
}

@Composable
internal fun ToggleRow(title: String, checked: Boolean, caption: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, caption = caption, trailing = { Switch(checked, onChange, Modifier.semantics { contentDescription = title }) })
}

@Composable
internal fun EmptyReading(title: String, description: String, icon: ImageVector = Icons.Outlined.MenuBook, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) PrimaryAction(action, onClick = onClick)
    }
}

@Composable
internal fun OfflineNotice(onDownloads: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WifiOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("You’re offline", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onDownloads) { Text("Downloads") }
        }
    }
}

@Composable
internal fun LoadingReading() {
    Column(Modifier.fillMaxWidth().padding(24.dp).semantics { contentDescription = "Loading passages" }, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
        Text("Loading passages…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        repeat(5) { index ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(if (index % 2 == 0) .7f else .5f).height(16.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Box(Modifier.fillMaxWidth(.9f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Rule(Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
internal fun PassageRow(record: ReadingRecord, saved: Boolean, onOpen: () -> Unit, onSave: (() -> Unit)? = null, subtitle: String? = null, showArabic: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${record.ref.title} · ${record.ref.normalizedNumber}", style = MaterialTheme.typography.titleMedium)
            Text(subtitle ?: record.entry.book, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showArabic) Text(record.entry.arabic, Modifier.fillMaxWidth(), fontFamily = ReadingArabic, fontSize = 24.sp, lineHeight = 38.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (record.entry.english.isNotBlank()) Text(record.entry.english, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onSave != null) ReadingIcon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (saved) "Unsave hadith ${record.ref.normalizedNumber}" else "Save hadith ${record.ref.normalizedNumber}", tint = MaterialTheme.colorScheme.primary, onClick = onSave)
        else ReadingIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Open hadith ${record.ref.normalizedNumber}", onClick = onOpen)
    }
    Rule()
}

internal fun clockTime(seconds: Float): String {
    val n = if (seconds.isFinite()) seconds.toInt().coerceAtLeast(0) else 0
    return "%d:%02d".format(Locale.ROOT, n / 60, n % 60)
}
internal fun readableBytes(bytes: Long): String = if (bytes < 1_048_576) "${(bytes / 1024).coerceAtLeast(0)} KB" else "%.1f MB".format(Locale.ROOT, bytes / 1_048_576.0)
internal fun AudioUiState.canPlay() = status == AudioStatus.READY && !usingTimingPreview

@Composable
internal fun PlayButton(audio: AudioUiState, onClick: () -> Unit, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    OutlinedIconButton(onClick, Modifier.size(if (large) 76.dp else 48.dp), enabled = audio.canPlay() || audio.isPlaying, shape = CircleShape,
        border = if (large) null else androidx.compose.foundation.BorderStroke(.75.dp, colors.outline),
        colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = if (large) colors.primary else Color.Transparent, contentColor = if (large) colors.onPrimary else colors.onSurface)) {
        if (audio.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Icon(if (audio.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (audio.isPlaying) "Pause audio" else "Play audio", Modifier.size(if (large) 34.dp else 26.dp))
    }
}

@Composable
internal fun MiniPlayer(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val current = state.current ?: return
    Column {
        Rule()
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { action(ReadingAction.Go(ReadingPage.PLAYER)) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${current.ref.title} · ${current.ref.normalizedNumber}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (audio.usingTimingPreview) "Audio unavailable" else if (audio.isLoading) "Preparing audio…" else "${clockTime(audio.positionSeconds)} / ${clockTime(audio.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlayButton(audio, { action(ReadingAction.PlayPause) })
        }
        if (audio.durationSeconds > 0) LinearProgressIndicator(progress = { (audio.positionSeconds / audio.durationSeconds).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
    }
}
