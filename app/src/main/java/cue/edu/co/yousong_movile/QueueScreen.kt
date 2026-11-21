package cue.edu.co.yousong_movile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.PositionNumeral
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import cue.edu.co.yousong_movile.ui.theme.tabular

/** Everything a queue view needs, decoupled from the network coordinator. */
internal data class QueueUi(
    val snapshot: QueueSnapshot?,
    val interactive: Boolean,
    val pendingId: String?,
    val loading: Boolean,
    val error: String?,
)

internal fun QueueUi.voteStateFor(voted: Boolean?, id: String) =
    voteState(voted, snapshot?.state, interactive, pendingId == id)

/** Display server order, positions and estimates verbatim; never simulate a player. */
@Composable
internal fun QueueScreen(ui: QueueUi, onVote: (QueueItem) -> Unit, onRetry: () -> Unit, onSearch: () -> Unit,
    modifier: Modifier = Modifier) {
    val queue = ui.snapshot
    if (queue == null) {
        if (ui.error != null) FullScreenState(YouSongIcons.Offline, stringResource(R.string.state_offline_title),
            ui.error, stringResource(R.string.action_retry), onRetry, modifier)
        else CenteredProgress(modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        item {
            Column(Modifier.readableWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PlayingCard(queue.playing)
                ScreenTitle(stringResource(R.string.queue_title), stringResource(R.string.queue_subtitle),
                    Modifier.padding(top = Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    serviceChip(queue.state)?.let { ServiceStatePill(it) }
                    Pill(pluralStringResource(R.plurals.songs_count, queue.items.size, queue.items.size))
                }
                if (queue.state == ServiceState.PAUSADO) Text(stringResource(R.string.paused_inline),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (queue.items.isEmpty()) item {
            StatePanel(YouSongIcons.QueueMusic, stringResource(R.string.empty_queue_title),
                stringResource(R.string.empty_queue_body), Modifier.readableWidth(),
                stringResource(R.string.empty_queue_action), onSearch)
        }
        items(queue.items, key = { it.requestId }) { item ->
            QueueRow(item, ui.voteStateFor(item.voted, item.requestId), head = item.position == queue.items.first().position,
                onVote = { onVote(item) }, modifier = Modifier.readableWidth())
        }
        if (queue.items.isNotEmpty()) item {
            Row(Modifier.readableWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(YouSongIcons.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.queue_wait_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The memorable element: a big tabular position numeral, cover, "Artista · 3:34", the wait,
 * and the round vote. The head of the queue carries the live-blue bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QueueRow(item: QueueItem, state: VoteState, head: Boolean, onVote: () -> Unit, modifier: Modifier = Modifier,
    compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val live = YouSongTheme.extras.live
    val position = stringResource(R.string.position_description, item.position)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.medium)
        .background(if (head && !compact) colors.surfaceContainerHighest else colors.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (head && !compact) live else colors.surfaceContainer))
        Row(Modifier.weight(1f).padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm)
            .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.position.toString().padStart(2, '0'), style = PositionNumeral,
                color = if (head) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 34.dp).semantics { contentDescription = position })
            Cover(item.song.thumbnailUrl, item.song.title, Modifier.size(if (compact) 48.dp else 56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OneLine(item.song.title, MaterialTheme.typography.titleMedium, lines = 2)
                OneLine(songMeta(item.song.artist, item.song.durationSeconds), MaterialTheme.typography.bodyMedium.tabular(),
                    color = colors.onSurfaceVariant, lines = 2)
                val wait = if (compact) null else waitMinutes(item.waitSeconds)
                if (wait != null || item.song.genre != null) FlowRow(Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    item.song.genre?.let { GenreChip(it) }
                    wait?.let {
                        Text(if (it == 0) stringResource(R.string.wait_now) else stringResource(R.string.wait_minutes, it),
                            style = MaterialTheme.typography.bodySmall.tabular(), color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        VoteCount(item.votes, Modifier.padding(start = Spacing.xxs, end = Spacing.xs))
        VoteButton(state, item.song.title, item.votes, onVote, Modifier.padding(end = Spacing.sm))
    }
}

@Composable
internal fun PlayingCard(playing: PlayingSong?, modifier: Modifier = Modifier) {
    if (playing == null) {
        InfoCard(YouSongIcons.Disc, stringResource(R.string.no_playback_kicker), stringResource(R.string.no_playback_title),
            stringResource(R.string.no_playback_body), modifier)
        return
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(Spacing.md).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Cover(playing.song.thumbnailUrl, playing.song.title, Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Equalizer(true, Modifier.size(14.dp), YouSongTheme.extras.live)
                    Text(stringResource(R.string.now_playing_label), style = MaterialTheme.typography.labelMedium,
                        color = YouSongTheme.extras.live)
                }
                OneLine(playing.song.title, MaterialTheme.typography.titleLarge, lines = 2)
                OneLine(songMeta(playing.song.artist, playing.song.durationSeconds, playing.song.genre),
                    MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Persistent mini-player above the navigation while the local reports a song playing. There is no
 * play button: playback belongs to the local. The thin accent line is how much of the song has
 * played, as of the last queue read; it moves only when the queue refreshes, never on its own.
 */
@Composable
internal fun NowPlayingBar(playing: PlayingSong, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.now_playing_description, playing.song.title, playing.song.artist)
    val colors = MaterialTheme.colorScheme
    val remaining = if (largeFont()) null else clockLabel(playing.remainingSeconds)
        ?.let { stringResource(R.string.now_playing_remaining, it) }
    val played = playedFraction(playing.song.durationSeconds, playing.remainingSeconds)
    Surface(modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHighest, contentColor = colors.onSurface) {
        Column(Modifier.clickable(onClick = onOpen).clearAndSetSemantics { contentDescription = description; role = Role.Button }) {
            Row(Modifier.heightIn(min = 60.dp).padding(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Cover(playing.song.thumbnailUrl, playing.song.title, Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    OneLine(playing.song.title, MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    OneLine(listOfNotNull(playing.song.artist, remaining).joinToString(META_SEPARATOR),
                        MaterialTheme.typography.bodySmall.tabular(), color = colors.onSurfaceVariant)
                }
                Equalizer(true, color = YouSongTheme.extras.live)
            }
            if (played != null) Box(Modifier.padding(horizontal = Spacing.sm).fillMaxWidth().height(2.dp)
                .clip(CircleShape).background(YouSongTheme.extras.hairline)) {
                Box(Modifier.fillMaxWidth(played).fillMaxHeight().background(colors.primary))
            }
            Spacer(Modifier.height(Spacing.xxs))
        }
    }
}
