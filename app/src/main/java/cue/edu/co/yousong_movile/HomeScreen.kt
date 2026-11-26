package cue.edu.co.yousong_movile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.RankNumeral
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import cue.edu.co.yousong_movile.ui.theme.tabular
import java.util.Locale
import java.util.TimeZone

/** Callbacks for Inicio, grouped so the screen stays readable and the gallery can pass no-ops. */
internal class HomeActions(
    val onOpen: (Song) -> Unit = {},
    val onVote: (QueueItem) -> Unit = {},
    val onQueue: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onRetryQueue: () -> Unit = {},
    val onRetryTrends: () -> Unit = {},
    val onRetryHighlights: () -> Unit = {},
)

/**
 * Inicio: the greeting, the now-playing hero (the live core) and the carousels. Each carousel loads,
 * fails and empties on its own; a failing one shows a quiet retry card and never blocks the rest.
 */
@Composable
internal fun HomeScreen(venue: String, ui: QueueUi, greeting: Greeting, trends: Feed<Trends>, highlights: Feed<Highlights>,
    actions: HomeActions, modifier: Modifier = Modifier, zone: TimeZone = TimeZone.getDefault(),
    listState: LazyListState = rememberLazyListState()) {
    val queue = ui.snapshot
    val upNext = queue?.items.orEmpty().drop(1)
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item("header") { HomeWidth { HomeHeader(greeting, venue, queue?.state) } }
        item("hero") {
            HomeWidth(Modifier.padding(horizontal = Spacing.md)) {
                when {
                    queue == null && ui.error != null -> StatePanel(YouSongIcons.Offline, stringResource(R.string.state_offline_title),
                        ui.error, action = stringResource(R.string.action_retry), onAction = actions.onRetryQueue)
                    queue == null -> CenteredProgress()
                    else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        when (queue.state) {
                            ServiceState.PAUSADO -> InfoCard(YouSongIcons.Pause, null, stringResource(R.string.state_paused_title),
                                stringResource(R.string.state_paused_body))
                            ServiceState.MANTENIMIENTO -> InfoCard(YouSongIcons.Maintenance, null,
                                stringResource(R.string.state_maintenance_title), stringResource(R.string.state_maintenance_body))
                            else -> Unit
                        }
                        NowPlayingHero(queue, ui, actions)
                    }
                }
            }
        }
        item("trends") {
            HomeWidth {
                ShelfSection(stringResource(R.string.section_trends), stringResource(R.string.section_trends_note),
                    trends.shelf { it.songs }, stringResource(R.string.shelf_trends_empty), actions.onRetryTrends,
                    key = { it.videoId }) { index, song ->
                    ShelfCard(song.title, song.artist, songMetaLine("", song.genre, song.durationSeconds).ifEmpty { null }, null,
                        song.thumbnailUrl, { actions.onOpen(song) }, rank = index + 1)
                }
            }
        }
        if (upNext.isNotEmpty()) item("up-next") {
            HomeWidth {
                ShelfSection(stringResource(R.string.up_next), null, Shelf.Items(upNext), "", {}, key = { it.requestId },
                    trailing = { TextButton(onClick = actions.onQueue) { Text(stringResource(R.string.see_queue)) } }) { _, item ->
                    ShelfCard(item.song.title, item.song.artist,
                        songMetaLine("", item.song.genre, item.song.durationSeconds).ifEmpty { null }, votesLabel(item.votes),
                        item.song.thumbnailUrl, { actions.onOpen(item.song.asSong()) })
                }
            }
        }
        if (highlights != Feed.Unavailable) {
            item("most-voted") {
                HomeWidth {
                    ShelfSection(stringResource(R.string.section_most_voted, venue), null, highlights.shelf { it.mostVoted },
                        stringResource(R.string.shelf_most_voted_empty), actions.onRetryHighlights, key = { it.song.videoId }) { _, ranked ->
                        ShelfCard(ranked.song.title, ranked.song.artist,
                            songMetaLine("", ranked.song.genre, ranked.song.durationSeconds).ifEmpty { null },
                            votesLabel(ranked.count), ranked.song.thumbnailUrl, { actions.onOpen(ranked.song) })
                    }
                }
            }
            item("recent") {
                HomeWidth {
                    // The same list can only fail once: its retry card shows under "Lo más votado".
                    val shelf = highlights.shelf { it.recent }.let { if (it is Shelf.Failed) Shelf.Hidden else it }
                    ShelfSection(stringResource(R.string.section_recent), null, shelf, stringResource(R.string.shelf_recent_empty), {},
                        key = { "${it.song.videoId}-${it.playedAt}" }) { _, play ->
                        val time = localTime(play.playedAt, zone, Locale.forLanguageTag("es-CO"))
                        ShelfCard(play.song.title, play.song.artist,
                            songMetaLine("", play.song.genre, play.song.durationSeconds).ifEmpty { null },
                            if (play.playing) stringResource(R.string.now_playing_label) else time?.let { stringResource(R.string.played_at, it) },
                            play.song.thumbnailUrl, { actions.onOpen(play.song) }, live = play.playing)
                    }
                }
            }
        }
    }
}

/** Home content is wider than the reading column so carousels use a tablet's width, but still capped. */
@Composable
private fun HomeWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Spacing.wideMax).fillMaxWidth().then(modifier)) { content() }
    }
}

@Composable
private fun HomeHeader(greeting: Greeting, venue: String, state: ServiceState?) {
    Column(Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(when (greeting) {
            Greeting.Morning -> R.string.greeting_morning
            Greeting.Afternoon -> R.string.greeting_afternoon
            Greeting.Night -> R.string.greeting_night
        }), style = MaterialTheme.typography.displayLarge, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(venue, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false))
            serviceChip(state)?.let { ServiceStatePill(it) }
        }
    }
}

/**
 * What is playing now and what comes next. With nothing playing, the head of the queue takes the
 * hero; with an empty queue, the hero invites the first request.
 */
@Composable
private fun NowPlayingHero(queue: QueueSnapshot, ui: QueueUi, actions: HomeActions) {
    val playing = queue.playing
    val head = queue.items.firstOrNull()
    if (playing == null && head == null) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
            StatePanel(YouSongIcons.Disc, stringResource(R.string.home_quiet_title), stringResource(R.string.home_quiet_body),
                Modifier.padding(horizontal = Spacing.md), stringResource(R.string.empty_queue_action), actions.onSearch,
                tone = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
        return
    }
    val subject = playing?.song ?: head!!.song
    val live = YouSongTheme.extras.live
    val stacked = largeFont()
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val details = stringResource(R.string.action_details)
            val open = Modifier.clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = details) { actions.onOpen(subject.asSong()) }.semantics(mergeDescendants = true) {}
            val text: @Composable (Modifier) -> Unit = { textModifier ->
                Column(textModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (playing != null) Equalizer(true, Modifier.size(16.dp), live)
                        Text(stringResource(if (playing != null) R.string.now_playing_label else R.string.home_up_next_hero),
                            style = MaterialTheme.typography.labelLarge, color = if (playing != null) live else MaterialTheme.colorScheme.primary)
                    }
                    OneLine(subject.title, MaterialTheme.typography.headlineMedium, lines = 3)
                    OneLine(songMeta(subject.artist, subject.durationSeconds, subject.genre), MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, lines = 2)
                }
            }
            if (stacked) Column(open, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Cover(subject.thumbnailUrl, subject.title, Modifier.size(136.dp), shape = MaterialTheme.shapes.medium)
                text(Modifier.fillMaxWidth())
            } else Row(open, horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Cover(subject.thumbnailUrl, subject.title, Modifier.size(136.dp), shape = MaterialTheme.shapes.medium)
                text(Modifier.weight(1f))
            }
            if (head != null) {
                if (playing != null) NextRow(head, ui, actions)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(votesLabel(head.votes), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VoteButton(ui.voteStateFor(head.voted, head.requestId), head.song.title, head.votes, { actions.onVote(head) })
                }
            }
        }
    }
}

@Composable
private fun NextRow(item: QueueItem, ui: QueueUi, actions: HomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.weight(1f).clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.action_details)) { actions.onOpen(item.song.asSong()) }
            .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Cover(item.song.thumbnailUrl, item.song.title, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_next_label), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OneLine(item.song.title, MaterialTheme.typography.titleMedium, lines = 2)
                OneLine(songMeta(item.song.artist, null, item.song.genre), MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VoteCount(item.votes)
        VoteButton(ui.voteStateFor(item.voted, item.requestId), item.song.title, item.votes, { actions.onVote(item) })
    }
}

private val CardWidth = 148.dp
private val RankNudge = (-4).dp
private val RankGap = 4.dp

/** A carousel with its own loading, empty and error states. */
@Composable
private fun <T> ShelfSection(title: String, note: String?, shelf: Shelf<T>, empty: String, onRetry: () -> Unit, key: (T) -> Any,
    trailing: (@Composable () -> Unit)? = null, card: @Composable (Int, T) -> Unit) {
    if (shelf == Shelf.Hidden) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.padding(horizontal = Spacing.md)) {
            SectionHeader(title, trailing = trailing)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when (shelf) {
            Shelf.Hidden -> Unit
            Shelf.Loading -> ShelfSkeleton(stringResource(R.string.shelf_loading, title))
            Shelf.Empty -> Text(empty, Modifier.padding(horizontal = Spacing.md), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Shelf.Failed -> RetryCard(shelf.message, onRetry, Modifier.padding(horizontal = Spacing.md))
            is Shelf.Items -> {
                val state = rememberLazyListState()
                LazyRow(Modifier.fillMaxWidth(), state = state, flingBehavior = rememberSnapFlingBehavior(state),
                    contentPadding = PaddingValues(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    itemsIndexed(shelf.items, key = { _, it -> key(it) }) { index, item -> card(index, item) }
                }
            }
        }
    }
}

/**
 * One song in a carousel: a big rounded cover, title, artist and a meta line, one tap target that
 * opens the detail sheet. Ranked cards set an outlined mango numeral behind the cover's left edge.
 */
@Composable
private fun ShelfCard(title: String, artist: String, meta: String?, extra: String?, cover: String?, onClick: () -> Unit,
    rank: Int? = null, live: Boolean = false) {
    // The gutter follows the numeral's measured width, so "10" and above are never hidden by the cover.
    val gutter = rank?.let { rankGutter(it) } ?: 0.dp
    val width = CardWidth + gutter
    Column(Modifier.width(width).clip(MaterialTheme.shapes.medium)
        .clickable(onClickLabel = stringResource(R.string.action_details), onClick = onClick)
        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (rank != null) Box(Modifier.fillMaxWidth().height(CardWidth)) {
            RankMark(rank, Modifier.align(Alignment.BottomStart).testTag("rank-mark-$rank"))
            Cover(cover, title, Modifier.align(Alignment.BottomEnd).size(CardWidth).testTag("rank-cover-$rank"),
                shape = MaterialTheme.shapes.medium)
        } else Cover(cover, title, Modifier.size(CardWidth), shape = MaterialTheme.shapes.medium)
        Column(Modifier.padding(start = gutter, end = Spacing.xxs, bottom = Spacing.xs)) {
            OneLine(title, MaterialTheme.typography.titleMedium, lines = 2)
            OneLine(artist, MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            meta?.let { OneLine(it.removePrefix(META_SEPARATOR), MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            extra?.let {
                Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium.tabular(),
                    color = if (live) YouSongTheme.extras.live else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** The numeral is sized in dp: it ignores font scale because it is decorative (the rank is read aloud). */
@Composable
private fun rankStyle(): TextStyle = with(LocalDensity.current) {
    RankNumeral.copy(fontSize = 88.dp.toSp(), lineHeight = 88.dp.toSp(), drawStyle = Stroke(width = 2.dp.toPx()))
}

@Composable
private fun rankGutter(rank: Int): Dp {
    val measured = rememberTextMeasurer().measure(rank.toString(), rankStyle(), softWrap = false, maxLines = 1)
    return with(LocalDensity.current) { measured.size.width.toDp() } + RankNudge + RankGap
}

/** The outlined rank numeral. */
@Composable
private fun RankMark(rank: Int, modifier: Modifier) {
    val description = stringResource(R.string.rank_description, rank)
    Text(rank.toString(), modifier.offset(x = RankNudge, y = 14.dp).clearAndSetSemantics { contentDescription = description },
        style = rankStyle(), color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
}

@Composable
private fun ShelfSkeleton(label: String) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(Modifier.fillMaxWidth().padding(start = Spacing.md).wrapContentWidth(Alignment.Start, unbounded = true)
        .clearAndSetSemantics { contentDescription = label; liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.size(CardWidth).clip(MaterialTheme.shapes.medium).background(block))
                Bar(block, CardWidth * 0.8f, 14.dp)
                Bar(block, CardWidth * 0.5f, 12.dp)
            }
        }
    }
}

@Composable
private fun Bar(color: androidx.compose.ui.graphics.Color, width: Dp, height: Dp) =
    Box(Modifier.size(width, height).clip(MaterialTheme.shapes.extraSmall).background(color))

/** The quiet failure for one carousel: what happened and a retry; the rest of Inicio keeps working. */
@Composable
private fun RetryCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(YouSongIcons.Offline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.shelf_error_title), style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.touch)) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
