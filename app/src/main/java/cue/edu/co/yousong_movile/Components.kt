package cue.edu.co.yousong_movile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.LocalReduceMotion
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import cue.edu.co.yousong_movile.ui.theme.tabular
import kotlinx.coroutines.delay

/** Centers content and caps its width so tablets and landscape keep a readable column. */
@Composable
internal fun ContentColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Spacing.contentMax).fillMaxWidth(), content = content)
    }
}

internal fun Modifier.readableWidth(): Modifier = this.widthIn(max = Spacing.contentMax).fillMaxWidth()

@Composable
internal fun ScreenTitle(title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let { Box(Modifier.padding(start = Spacing.sm, top = Spacing.xs)) { it() } }
    }
}

/** Title and an optional trailing element; at large font sizes the trailing element wraps below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    FlowRow(modifier.fillMaxWidth().heightIn(min = Spacing.touch), horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        trailing?.invoke()
    }
}

/** True when the user's font scale is large enough that side-by-side layouts must stack. */
@Composable
internal fun largeFont(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f

/** A quiet pill used for counts ("4 canciones") and service state. */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.clip(CircleShape).background(container).padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon?.let { Icon(it, null, Modifier.size(16.dp), tint = content) }
        Text(text, style = MaterialTheme.typography.labelMedium.tabular(), color = content, maxLines = 1)
    }
}

@Composable
internal fun ServiceStatePill(chip: ServiceChip, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extras = YouSongTheme.extras
    when (chip) {
        ServiceChip.Live -> Row(modifier.clip(CircleShape).background(colors.surfaceContainerHighest)
            .padding(horizontal = Spacing.sm, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(extras.live))
            Text(stringResource(R.string.chip_live), style = MaterialTheme.typography.labelMedium)
        }
        ServiceChip.Paused -> Pill(stringResource(R.string.chip_paused), modifier, YouSongIcons.Pause)
        ServiceChip.Closed -> Pill(stringResource(R.string.chip_closed), modifier, YouSongIcons.Night)
        ServiceChip.Maintenance -> Pill(stringResource(R.string.chip_maintenance), modifier, YouSongIcons.Maintenance)
    }
}

@Composable
internal fun votesLabel(votes: Long): String =
    pluralStringResource(R.plurals.votes_count, votes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), votes)

@Composable
internal fun songMeta(artist: String, seconds: Int?): String =
    clockLabel(seconds)?.let { stringResource(R.string.meta_artist_duration, artist, it) } ?: artist

/** Icon, one sentence and one action: used for every blocking or empty state. */
@Composable
internal fun StatePanel(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier,
    action: String? = null, onAction: (() -> Unit)? = null, tone: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        }
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = Spacing.xs).heightIn(min = Spacing.touch)) { Text(action) }
        }
    }
}

/** Full-screen variant: vertically centered inside the available area. */
@Composable
internal fun FullScreenState(icon: ImageVector, title: String, body: String?, action: String?, onAction: (() -> Unit)?,
    modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = Spacing.lg), contentAlignment = Alignment.Center) {
        StatePanel(icon, title, body, Modifier.readableWidth(), action, onAction)
    }
}

/** A calm inline card: "Sin reproducción activa" and the paused notice. */
@Composable
internal fun InfoCard(icon: ImageVector, kicker: String?, title: String, body: String?, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, YouSongTheme.extras.hairline)) {
        Row(Modifier.padding(Spacing.md).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                kicker?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(title, style = MaterialTheme.typography.titleLarge)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/**
 * The round vote control (50dp). Votable: outlined arrow. Voted: tonal square-ish check.
 * Pending: spinner in place. Unavailable: dimmed outline, with the reason in its label.
 */
@Composable
internal fun VoteButton(state: VoteState, title: String, votes: Long, onVote: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val count = votesLabel(votes)
    val label = when (state) {
        VoteState.Votable -> stringResource(R.string.vote_action_description, title, count)
        VoteState.Voted -> stringResource(R.string.voted_description, title, count)
        VoteState.Pending -> stringResource(R.string.vote_pending_description, title)
        is VoteState.Unavailable -> when (state.reason) {
            UnavailableReason.Paused -> stringResource(R.string.vote_unavailable_paused, title)
            UnavailableReason.Unknown -> stringResource(R.string.vote_unavailable_unknown, title)
            UnavailableReason.NotReady -> stringResource(R.string.vote_unavailable_wait, title)
        }
    }
    val size = 50.dp
    val base = modifier.size(size).semantics(mergeDescendants = true) {
        contentDescription = label
        if (state is VoteState.Voted) stateDescription = count
        if (state == VoteState.Pending) liveRegion = LiveRegionMode.Polite
    }
    when (state) {
        VoteState.Votable -> OutlinedIconButton(onClick = onVote, modifier = base,
            border = BorderStroke(1.dp, colors.outline)) { Icon(YouSongIcons.ArrowUp, null) }
        VoteState.Voted -> FilledTonalIconButton(onClick = {}, enabled = false, modifier = base,
            shape = MaterialTheme.shapes.small, colors = IconButtonDefaults.filledTonalIconButtonColors(
                disabledContainerColor = colors.inverseSurface, disabledContentColor = colors.inverseOnSurface)) {
            Icon(YouSongIcons.Check, null)
        }
        VoteState.Pending -> Box(base.border(1.dp, colors.outline, CircleShape), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        is VoteState.Unavailable -> OutlinedIconButton(onClick = {}, enabled = false, modifier = base,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.38f))) { Icon(YouSongIcons.ArrowUp, null) }
    }
}

/** Vote count with an up arrow; pulses once when the number changes (never under reduce motion). */
@Composable
internal fun VoteCount(votes: Long, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    var last by remember { mutableLongStateOf(votes) }
    LaunchedEffect(votes) {
        if (votes != last && !reduce) {
            scale.animateTo(1.25f, tween(140))
            scale.animateTo(1f, tween(220))
        }
        last = votes
    }
    Row(modifier.scale(scale.value).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(YouSongIcons.ArrowUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(votes.toString(), style = MaterialTheme.typography.labelLarge.tabular())
    }
}

/** Three bars. Animates only while something is playing and motion is allowed. */
@Composable
internal fun Equalizer(playing: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val animate = playing && !LocalReduceMotion.current
    val heights = if (animate) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        listOf(520, 380, 640).mapIndexed { index, period ->
            transition.animateFloat(0.3f + index * 0.1f, 1f, infiniteRepeatable(tween(period), RepeatMode.Reverse),
                label = "bar$index").value
        }
    } else listOf(0.55f, 0.9f, 0.4f)
    Canvas(modifier.size(20.dp).clearAndSetSemantics {}) {
        val bar = size.width / 5f
        heights.forEachIndexed { index, fraction ->
            val h = size.height * fraction
            drawRoundRect(color, Offset(bar * (index * 2), size.height - h), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

/**
 * Settles the "can I act" signal: polling flips readiness off for a moment every few seconds,
 * so turning off waits [hold] before the UI reacts, while turning on is immediate.
 */
@Composable
internal fun rememberInteractive(actions: SongActions, hold: Long = 2_500): Boolean {
    val raw = actions.pending || actions.canSupport(false)
    var settled by remember { mutableStateOf(raw) }
    LaunchedEffect(raw) {
        if (raw) settled = true else { delay(hold); settled = false }
    }
    return settled
}

@Composable
internal fun CenteredProgress(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(size))
    }
}

@Composable
internal fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier,
    color: Color = Color.Unspecified, lines: Int = 1) {
    Text(text, modifier, color = color, style = style, maxLines = lines, overflow = TextOverflow.Ellipsis)
}
