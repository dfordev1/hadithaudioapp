package to.hadith.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HadithToApp(model: ReadingViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val audio by model.audio.state.collectAsStateWithLifecycle()
    HadithToTheme(state.settings.appearance) {
        BackHandler(state.page != ReadingPage.LIBRARY || state.sheet != ReaderSheet.NONE || state.focused) { model.dispatch(ReadingAction.Back) }
        ReadingApp(state, audio, model::dispatch)
    }
}

/** Stateless surface also used by the device UI tests. Data and playback live in the ViewModel. */
@Composable
internal fun ReadingApp(state: ReadingState, audio: AudioUiState, action: ReadingDispatch) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); action(ReadingAction.DismissMessage) }
    }
    val withTabs = state.page in setOf(ReadingPage.LIBRARY, ReadingPage.COLLECTION, ReadingPage.HADITHS, ReadingPage.SEARCH, ReadingPage.PLAYER)
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    if (withTabs) {
                        if (state.page != ReadingPage.PLAYER) MiniPlayer(state, audio, action)
                        ReadingNavigation(state.page, action)
                    } else if (state.page == ReadingPage.READER && state.current != null) ReaderDock(state, audio, action)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 700.dp).fillMaxSize()) {
                when (state.page) {
                    ReadingPage.LIBRARY -> LibraryScreen(state, action)
                    ReadingPage.COLLECTION -> CollectionScreen(state, action)
                    ReadingPage.HADITHS -> HadithListScreen(state, action)
                    ReadingPage.SEARCH -> SearchScreen(state, action)
                    ReadingPage.READER -> ReaderScreen(state, audio, action)
                    ReadingPage.PLAYER -> PlayerScreen(state, audio, action)
                    ReadingPage.DOWNLOADS -> DownloadsScreen(state, action)
                    ReadingPage.SETTINGS -> SettingsScreen(state, action)
                    ReadingPage.APPEARANCE -> AppearanceScreen(state, action)
                    ReadingPage.SOURCES -> SourcesScreen(state, action)
                    ReadingPage.REPORT -> ReportScreen(state, audio, action)
                    ReadingPage.LICENCES -> LicencesScreen(action)
                }
            }
        }
    }
    ReadingSheets(state, audio, action)
}

private data class NavDestination(val label: String, val icon: ImageVector, val page: ReadingPage)
@Composable
private fun ReadingNavigation(page: ReadingPage, action: ReadingDispatch) {
    val tabs = listOf(NavDestination("Listen", Icons.Outlined.Headphones, ReadingPage.PLAYER), NavDestination("Library", Icons.Outlined.MenuBook, ReadingPage.LIBRARY), NavDestination("Search", Icons.Outlined.Search, ReadingPage.SEARCH))
    Rule()
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
        tabs.forEach { item ->
            val selected = if (item.page == ReadingPage.LIBRARY) page in setOf(ReadingPage.LIBRARY, ReadingPage.COLLECTION, ReadingPage.HADITHS) else page == item.page
            NavigationBarItem(selected, { action(ReadingAction.Go(item.page)) }, icon = { Icon(item.icon, item.label, Modifier.size(22.dp)) }, label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surface, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
