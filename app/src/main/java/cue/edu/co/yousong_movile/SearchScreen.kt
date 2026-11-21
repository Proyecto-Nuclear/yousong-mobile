package cue.edu.co.yousong_movile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.ui.draw.rotate
import cue.edu.co.yousong_movile.ui.theme.GenreTileColors
import cue.edu.co.yousong_movile.ui.theme.tabular
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal sealed interface SearchPhase {
    data object Idle : SearchPhase
    data object Loading : SearchPhase
    data class Results(val songs: List<Song>, val hasMore: Boolean, val loadingMore: Boolean) : SearchPhase
    data object Empty : SearchPhase
    data class Failed(val message: String) : SearchPhase
}

/** One explicit search: the first page (`page == null`) or a "ver más" page. [seq] makes retries distinct. */
internal data class Submission(val seq: Long, val query: String, val page: String?)

/**
 * Catalog search state. It lives above the tab and confirmation branches so results survive
 * switching tabs; only the typed text is saved across rotation (never results or credentials).
 * Searching spends the venue's daily budget, so results load only on an explicit submit or page
 * request, never on keystrokes; typing only feeds the (debounced) suggestions.
 */
@Stable
internal class SearchState(initialQuery: String = "") {
    var query by mutableStateOf(initialQuery)
        private set
    var submission by mutableStateOf<Submission?>(null)
        private set
    var songs by mutableStateOf<List<Song>>(emptyList())
    var nextPage by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
    var suggestedFor: String? = null
    var dismissed by mutableStateOf(false)
    /** The submission whose results are on screen; a recomposed effect never refetches it. */
    var loadedFor: Submission? = null
    private var seq = 0L

    fun edit(text: String) {
        query = text
        dismissed = false
        if (text.isEmpty()) clear()
    }

    /** Explicit search (keyboard action or a phrase suggestion). Re-submitting the shown query is free. */
    fun submit(text: String = query) {
        query = text
        dismissed = true
        val normalized = ClientContract.query(text) ?: return
        val current = submission
        if (current != null && current.query == normalized && error == null) return
        submission = Submission(++seq, normalized, null)
    }

    fun more() {
        val current = submission ?: return
        val page = nextPage ?: return
        if (!loading) submission = Submission(++seq, current.query, page)
    }

    fun retry() { submission = submission?.let { it.copy(seq = ++seq) } }

    private fun clear() {
        submission = null; loadedFor = null; songs = emptyList(); nextPage = null; searched = false; error = null
        suggestions = emptyList(); suggestedFor = null
    }

    val phase: SearchPhase get() = when {
        error != null && songs.isEmpty() -> SearchPhase.Failed(error!!)
        submission == null && songs.isEmpty() -> SearchPhase.Idle
        loading && songs.isEmpty() -> SearchPhase.Loading
        searched && songs.isEmpty() -> SearchPhase.Empty
        songs.isNotEmpty() -> SearchPhase.Results(songs, nextPage != null, loading)
        else -> SearchPhase.Loading
    }

    companion object {
        val Saver: Saver<SearchState, String> = Saver(save = { it.query }, restore = { SearchState(it) })
    }
}

@Composable
internal fun SearchScreen(api: ParticipantApi, token: String, state: SearchState, ui: QueueUi, onExpired: (String) -> Unit,
    onOpen: (Song) -> Unit, onQuick: (Song) -> Unit, tiles: List<GenreTile>, modifier: Modifier = Modifier) {
    // Suggestions: the one debounce, on typing only, skipped once a search was submitted.
    LaunchedEffect(api, token, state.query, state.dismissed) {
        val normalized = ClientContract.query(state.query)
        if (normalized == null || state.dismissed) { state.suggestions = emptyList(); return@LaunchedEffect }
        if (state.suggestedFor == normalized) return@LaunchedEffect
        delay(350)
        try { state.suggestions = api.suggestions(token, normalized); state.suggestedFor = normalized }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // Suggestions are optional help; the search itself reports failures.
            if (failure is ApiFailure && failure.status == 401) onExpired(errorLabel(failure))
        }
    }
    // Results: only explicit submissions reach the network.
    LaunchedEffect(api, token, state.submission) {
        val submission = state.submission ?: return@LaunchedEffect
        if (submission == state.loadedFor) return@LaunchedEffect
        state.error = null
        if (submission.page == null) { state.songs = emptyList(); state.nextPage = null; state.searched = false }
        state.loading = true
        try {
            val result = api.search(token, submission.query, submission.page)
            state.songs = if (submission.page == null) result.songs else (state.songs + result.songs).distinctBy { it.videoId }
            state.nextPage = result.nextPage; state.searched = true
            state.loadedFor = submission
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            state.error = errorLabel(failure)
            if (failure is ApiFailure && failure.status == 401) onExpired(errorLabel(failure))
        } finally { state.loading = false }
    }
    SearchContent(
        query = state.query,
        onQuery = state::edit,
        onSubmit = { state.submit() },
        suggestions = if (state.dismissed || ClientContract.query(state.query) == null) emptyList() else state.suggestions,
        onSuggestion = { suggestion ->
            val song = suggestion.song
            if (song != null) { state.dismissed = true; onOpen(song) } else state.submit(suggestion.text)
        },
        phase = state.phase,
        ui = ui,
        onOpen = onOpen,
        onQuick = onQuick,
        onMore = state::more,
        onRetry = state::retry,
        modifier = modifier,
        tiles = tiles,
        onGenre = { state.submit(it) },
    )
}

@Composable
internal fun SearchContent(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, suggestions: List<Suggestion>,
    onSuggestion: (Suggestion) -> Unit, phase: SearchPhase, ui: QueueUi, onOpen: (Song) -> Unit, onQuick: (Song) -> Unit,
    onMore: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier, tiles: List<GenreTile> = genreTiles(emptyList()),
    onGenre: (String) -> Unit = {}) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val wide = phase == SearchPhase.Idle
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = if (wide) Spacing.wideMax else Spacing.contentMax).fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.search_title), style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.semantics { heading() })
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().heightIn(min = 60.dp).onFocusChanged { focused = it.isFocused },
                placeholder = { Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                textStyle = MaterialTheme.typography.titleMedium,
                leadingIcon = { Icon(YouSongIcons.SearchFilled, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(YouSongIcons.Close, stringResource(R.string.search_clear))
                    }
                },
                singleLine = true, shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(); focus.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = YouSongTheme.extras.live,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    cursorColor = YouSongTheme.extras.live))
        }
        Box(Modifier.widthIn(max = if (wide) Spacing.wideMax else Spacing.contentMax).fillMaxWidth().weight(1f)) {
            SearchBody(phase, ui, onOpen, onQuick, onMore, onRetry, tiles, { focus.clearFocus(); onGenre(it) })
            if (suggestions.isNotEmpty() && (focused || query.isNotEmpty())) {
                SuggestionList(suggestions, { focus.clearFocus(); onSuggestion(it) }, Modifier.zIndex(1f))
            }
        }
    }
}

@Composable
private fun SuggestionList(items: List<Suggestion>, onPick: (Suggestion) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, YouSongTheme.extras.hairline)) {
        Column(Modifier.padding(vertical = Spacing.xs)) {
            items.forEach { suggestion ->
                val song = suggestion.song
                val description = if (song != null) stringResource(R.string.suggestion_song_description, suggestion.text)
                    else stringResource(R.string.suggestion_query_description, suggestion.text)
                Row(Modifier.fillMaxWidth().clickable { onPick(suggestion) }.heightIn(min = 52.dp)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .clearAndSetSemantics { contentDescription = description; role = Role.Button },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (song != null) {
                        // A catalog song: cover and artist, opens the detail directly.
                        Cover(song.thumbnailUrl, song.title, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            OneLine(song.title, MaterialTheme.typography.titleSmall)
                            OneLine(song.artist, MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // A search phrase: magnifier, plain text, runs that search.
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(YouSongIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OneLine(suggestion.text, MaterialTheme.typography.bodyLarge, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBody(phase: SearchPhase, ui: QueueUi, onOpen: (Song) -> Unit, onQuick: (Song) -> Unit, onMore: () -> Unit,
    onRetry: () -> Unit, tiles: List<GenreTile>, onGenre: (String) -> Unit) {
    when (phase) {
        SearchPhase.Idle -> ExploreGrid(tiles, onGenre)
        SearchPhase.Empty -> Column {
            ResultsHeader(0)
            StatePanel(YouSongIcons.Search, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body),
                Modifier.padding(top = Spacing.lg))
        }
        is SearchPhase.Failed -> StatePanel(YouSongIcons.Error, stringResource(R.string.search_error_title), phase.message,
            action = stringResource(R.string.action_retry), onAction = onRetry, tone = MaterialTheme.colorScheme.errorContainer)
        SearchPhase.Loading -> SkeletonList()
        is SearchPhase.Results -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            item { ResultsHeader(phase.songs.size) }
            items(phase.songs, key = { it.videoId }) { song ->
                SongRow(song, songCta(song, ui.snapshot, ui.interactive), ui.pendingId == song.videoId,
                    { onOpen(song) }, { onQuick(song) })
            }
            if (phase.hasMore) item {
                Box(Modifier.fillMaxWidth().padding(top = Spacing.sm), contentAlignment = Alignment.Center) {
                    if (phase.loadingMore) CircularProgressIndicator(Modifier.size(28.dp))
                    else OutlinedButton(onClick = onMore, modifier = Modifier.heightIn(min = Spacing.touch),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Text(stringResource(R.string.search_more))
                    }
                }
            }
        }
    }
}

/** "Explorar": festival-colored genre tiles; one tap runs a search for that genre. */
@Composable
private fun ExploreGrid(tiles: List<GenreTile>, onGenre: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = Spacing.xs, bottom = Spacing.xxs)) {
                SectionHeader(stringResource(R.string.explore_title))
                Text(stringResource(R.string.explore_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(tiles, key = { _, tile -> genreKey(tile.label) }) { index, tile ->
            GenreTileCard(tile, GenreTileColors[index % GenreTileColors.size]) { onGenre(tile.label) }
        }
    }
}

@Composable
private fun GenreTileCard(tile: GenreTile, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val action = stringResource(R.string.explore_tile_action, tile.label)
    Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(MaterialTheme.shapes.small).background(color)
        .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}) {
        // A trend cover tucked into the corner, tilted like a record sleeve on a stall; decorative.
        tile.cover?.let {
            Cover(it, tile.label, Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 10.dp).size(64.dp)
                .rotate(22f), shape = MaterialTheme.shapes.extraSmall)
        }
        Text(tile.label, Modifier.padding(Spacing.sm), style = MaterialTheme.typography.titleLarge,
            color = YouSongTheme.extras.onTile)
    }
}

@Composable
private fun ResultsHeader(count: Int) {
    SectionHeader(stringResource(R.string.search_results)) {
        Text(pluralStringResource(R.plurals.songs_count, count, count), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A result row: cover, title, "Artista · Género · 3:34" and a quick add/vote; the row opens the detail. */
@Composable
private fun SongRow(song: Song, cta: SongCta, pending: Boolean, onOpen: () -> Unit, onQuick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.action_details), onClick = onOpen)
            .padding(vertical = Spacing.xs).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Cover(song.thumbnailUrl, song.title, Modifier.size(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OneLine(song.title, MaterialTheme.typography.titleMedium, lines = 2)
                OneLine(songMeta(song.artist, song.durationSeconds, song.genre), MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lines = 2)
            }
        }
        QuickAction(song.title, cta, pending, onQuick, Modifier.padding(start = Spacing.xxs))
    }
}

@Composable
private fun QuickAction(title: String, cta: SongCta, pending: Boolean, onQuick: () -> Unit, modifier: Modifier) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val tint = MaterialTheme.colorScheme.primary
    when {
        pending -> Box(modifier.size(Spacing.touch).padding(4.dp).border(border, CircleShape)
            .semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        cta == SongCta.Request -> OutlinedIconButton(onClick = onQuick, modifier = modifier.size(Spacing.touch).padding(4.dp),
            border = border) {
            Icon(YouSongIcons.Add, stringResource(R.string.quick_request_description, title), tint = tint)
        }
        cta == SongCta.Vote -> OutlinedIconButton(onClick = onQuick, modifier = modifier.size(Spacing.touch).padding(4.dp),
            border = border) {
            Icon(YouSongIcons.ArrowUp, stringResource(R.string.quick_vote_description, title), tint = tint)
        }
        cta == SongCta.Voted -> Box(modifier.size(Spacing.touch).padding(4.dp).clip(CircleShape)
            .background(YouSongTheme.extras.success), contentAlignment = Alignment.Center) {
            Icon(YouSongIcons.Check, stringResource(R.string.cta_voted), Modifier.size(20.dp), tint = YouSongTheme.extras.onSuccess)
        }
        else -> Unit
    }
}

@Composable
private fun SkeletonList() {
    val label = stringResource(R.string.search_loading)
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label; liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.padding(vertical = Spacing.sm).size(140.dp, 24.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
        repeat(5) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                    Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                }
            }
        }
    }
}
