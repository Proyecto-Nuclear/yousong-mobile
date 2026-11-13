package cue.edu.co.yousong_movile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme

@Composable
internal fun HomeScreen(venue: String, ui: QueueUi, onSearch: () -> Unit, onQueue: () -> Unit, onVote: (QueueItem) -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val queue = ui.snapshot
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.readableWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ScreenTitle(venue, stringResource(R.string.home_tagline))
            SearchEntry(onSearch)
            when {
                queue == null && ui.error != null -> StatePanel(YouSongIcons.Offline, stringResource(R.string.state_offline_title),
                    ui.error, action = stringResource(R.string.action_retry), onAction = onRetry)
                queue == null -> CenteredProgress()
                else -> HomeQueue(queue, ui, onSearch, onQueue, onVote)
            }
        }
    }
}

@Composable
private fun HomeQueue(queue: QueueSnapshot, ui: QueueUi, onSearch: () -> Unit, onQueue: () -> Unit, onVote: (QueueItem) -> Unit) {
    when (queue.state) {
        ServiceState.PAUSADO -> InfoCard(YouSongIcons.Pause, null, stringResource(R.string.state_paused_title),
            stringResource(R.string.state_paused_body))
        ServiceState.MANTENIMIENTO -> InfoCard(YouSongIcons.Maintenance, null, stringResource(R.string.state_maintenance_title),
            stringResource(R.string.state_maintenance_body))
        else -> Unit
    }
    val top = favourite(queue)
    if (top != null) FavouriteCard(top, ui.voteStateFor(top.voted, top.requestId)) { onVote(top) }
    else if (queue.playing == null) PlayingCard(null)
    if (queue.items.isEmpty()) {
        StatePanel(YouSongIcons.QueueMusic, stringResource(R.string.empty_queue_title), stringResource(R.string.empty_queue_body),
            action = stringResource(R.string.empty_queue_action), onAction = onSearch)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.up_next)) {
            IconButton(onClick = onQueue) { Icon(YouSongIcons.ArrowForward, stringResource(R.string.see_full_queue)) }
        }
        queue.items.take(3).forEach { item ->
            QueueRow(item, ui.voteStateFor(item.voted, item.requestId), head = false, onVote = { onVote(item) }, compact = true)
        }
    }
}

@Composable
private fun SearchEntry(onSearch: () -> Unit) {
    val label = stringResource(R.string.home_search_entry)
    Surface(Modifier.fillMaxWidth(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.clickable(onClick = onSearch).heightIn(min = 52.dp).padding(horizontal = Spacing.md)
            .clearAndSetSemantics { contentDescription = label; role = Role.Button },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(YouSongIcons.Search, null)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(YouSongIcons.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FavouriteCard(item: QueueItem, state: VoteState, onVote: () -> Unit) {
    val stacked = largeFont()
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        val content: @Composable (Modifier) -> Unit = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    OneLine(item.song.title, MaterialTheme.typography.headlineMedium, lines = 2)
                    OneLine(item.song.artist, MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(YouSongIcons.MusicNote, null, Modifier.size(14.dp), tint = YouSongTheme.extras.live)
                        Text(stringResource(R.string.favourite_label), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                FavouriteVote(item, state, onVote)
                Text(votesLabel(item.votes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (stacked) Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Cover(item.song.thumbnailUrl, item.song.title, Modifier.size(112.dp))
            content(Modifier.fillMaxWidth())
        } else Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Cover(item.song.thumbnailUrl, item.song.title, Modifier.size(112.dp))
            content(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FavouriteVote(item: QueueItem, state: VoteState, onVote: () -> Unit) {
    val modifier = Modifier.heightIn(min = Spacing.touch)
    when (state) {
        VoteState.Votable -> OutlinedButton(onClick = onVote, modifier = modifier,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Icon(YouSongIcons.ArrowUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.vote_for_this_song))
        }
        else -> VoteButton(state, item.song.title, item.votes, onVote)
    }
}
