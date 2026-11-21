package cue.edu.co.yousong_movile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The song the participant just acted on; only this item shows progress. */
internal data class ActionTarget(val id: String, val title: String, val artist: String, val cover: String?)

internal data class Confirmation(val target: ActionTarget, val position: Int?)

/** Snackbar content that knows whether it confirms something (check icon) or reports a problem. */
internal class Notice(override val message: String, val success: Boolean) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction = true
    override val duration = SnackbarDuration.Long
}

@Composable
internal fun ConnectedScreen(api: ParticipantApi, session: ParticipantSession, initialNotice: String?, onEnded: (String?) -> Unit) {
    key(api, session) { ConnectedSessionScreen(api, session, initialNotice, onEnded) }
}

@Composable
private fun ConnectedSessionScreen(api: ParticipantApi, session: ParticipantSession, initialNotice: String?,
    onEnded: (String?) -> Unit) {
    val actions = rememberSongActions(api, session, onEnded)
    val feed = rememberHomeFeed(api, session)
    // Only the tab is saved; credentials never enter saved instance state.
    var tab by rememberSaveable { mutableStateOf(Tab.Inicio) }
    val interactive = rememberInteractive(actions)
    var target by remember { mutableStateOf<ActionTarget?>(null) }
    var selected by remember { mutableStateOf<Song?>(null) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val leftNotice = stringResource(R.string.left_notice)

    LaunchedEffect(Unit) { initialNotice?.let { snackbar.showSnackbar(Notice(it, success = false)) } }
    LaunchedEffect(actions.outcome) {
        val outcome = actions.outcome ?: return@LaunchedEffect
        val result = outcome.result
        val acted = target
        selected = null
        if (result != null && result.created && acted != null) {
            confirmation = Confirmation(acted, result.position)
            return@LaunchedEffect
        }
        val message = when {
            result != null && acted != null -> resources.getString(R.string.snack_voted, acted.title)
            result != null -> resources.getString(R.string.snack_voted_generic)
            else -> outcome.message
        } ?: return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(Notice(message, success = result != null))
    }
    LaunchedEffect(actions.pending) { if (!actions.pending) target = null }

    // The UI only offers these taps when the song looks actionable; if the coordinator's gate
    // still refuses (a poll is reconciling), say so instead of dropping the tap silently.
    fun refused() {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(Notice(resources.getString(R.string.tap_refused_busy), success = false))
        }
    }
    fun request(song: Song) {
        if (actions.request(song)) target = ActionTarget(song.videoId, song.title, song.artist, song.thumbnailUrl)
        else refused()
    }
    fun vote(item: QueueItem) {
        if (actions.vote(item.requestId, item.voted)) target = ActionTarget(item.requestId, item.song.title, item.song.artist, item.song.thumbnailUrl)
        else refused()
    }

    BackHandler(enabled = confirmation != null || backTarget(tab) != null) {
        if (confirmation != null) confirmation = null else backTarget(tab)?.let { tab = it }
    }

    val ui = QueueUi(actions.snapshot, interactive, target?.id, actions.loading, actions.error)
    // Above the tab branches so results survive tabs and the confirmation; only the query is saved.
    val search = rememberSaveable(saver = SearchState.Saver) { SearchState() }
    val busy = actions.loading || actions.pending
    val snapshot = actions.snapshot
    // "Últimas que sonaron" changes when the song changes; reload the local's rankings then, not on a timer.
    val playingId = snapshot?.playing?.song?.videoId
    var seenPlaying by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playingId) {
        if (playingId != null && seenPlaying != null && playingId != seenPlaying) feed.loadHighlights()
        if (playingId != null) seenPlaying = playingId
    }
    val hour = remember(tab) { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val trendSongs = (feed.trends as? Feed.Ready)?.value?.songs.orEmpty()
    val tiles = remember(trendSongs) { genreTiles(trendSongs) }
    fun open(song: Song) { selected = song }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ShellTopBar(if (tab == Tab.Inicio) null else actions.status.name, busy, leaving,
                onRefresh = { actions.refresh(); if (tab == Tab.Inicio) feed.refresh() }, onLeave = { askLeave = true })
        },
        snackbarHost = { YouSongSnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                snapshot?.playing?.let { NowPlayingBar(it, onOpen = { tab = Tab.Cola }, Modifier.readableWidthCentered()) }
                ShellNavigation(tab, snapshot?.items?.size ?: 0) { tab = it; confirmation = null }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ShellBanner(actions, interactive, contentShowsError = tab != Tab.Buscar)
            Box(Modifier.weight(1f)) {
                val current = confirmation
                // The confirmation is drawn over the tab, which stays composed (and keeps its state)
                // but is hidden from accessibility while covered.
                Box(if (current != null) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.Inicio -> HomeScreen(actions.status.name, ui, greeting(hour), feed.trends, feed.highlights,
                        HomeActions(onOpen = ::open, onVote = ::vote, onQueue = { tab = Tab.Cola }, onSearch = { tab = Tab.Buscar },
                            onRetryQueue = actions::refresh, onRetryTrends = feed::loadTrends, onRetryHighlights = feed::loadHighlights))
                    Tab.Buscar -> SearchScreen(api, session.token, search, ui, onExpired = { onEnded(it) },
                        onOpen = ::open, onQuick = ::request, tiles = tiles)
                    Tab.Cola -> QueueScreen(ui, onVote = ::vote, onRetry = actions::refresh, onSearch = { tab = Tab.Buscar })
                }
                }
                if (current != null) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfirmationScreen(current, onQueue = { confirmation = null; tab = Tab.Cola },
                        onSearch = { confirmation = null; tab = Tab.Buscar })
                }
            }
        }
    }
    selected?.let { song ->
        SongDetail(song, songCta(song, snapshot, interactive), target?.id == song.videoId,
            onAction = { request(song) }, onDismiss = { selected = null })
    }
    if (askLeave) LeaveDialog(actions.status.pseudonym, confirmEnabled = !leaving && !actions.pending, onConfirm = {
        askLeave = false
        if (leaving || actions.pending) return@LeaveDialog
        leaving = true
        scope.launch {
            try { api.leave(session.token); onEnded(leftNotice) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is ApiFailure && failure.status == 401) onEnded(errorLabel(failure))
                else snackbar.showSnackbar(Notice(errorLabel(failure), success = false))
            } finally { leaving = false }
        }
    }, onDismiss = { askLeave = false })
}

internal fun Modifier.readableWidthCentered(): Modifier = this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = Spacing.contentMax)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShellTopBar(title: String?, loading: Boolean, leaving: Boolean, onRefresh: () -> Unit, onLeave: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(YouSongIcons.BrandMark, stringResource(R.string.brand_mark_description), Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
                title?.let { OneLine(it, MaterialTheme.typography.titleMedium) }
            }
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(YouSongIcons.Refresh, stringResource(R.string.refresh_description))
            }
            IconButton(onClick = onLeave, enabled = !leaving) {
                Icon(YouSongIcons.Leave, stringResource(if (leaving) R.string.leaving else R.string.leave_action))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Spotify-like tabs: no pill indicator; the selected tab gets the filled icon, full ink and bold label. */
@Composable
internal fun ShellNavigation(tab: Tab, queued: Int, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(containerColor = colors.surfaceContainerLow, tonalElevation = 0.dp) {
        Tab.entries.forEach { item ->
            val selected = tab == item
            val label = stringResource(when (item) { Tab.Inicio -> R.string.tab_home; Tab.Buscar -> R.string.tab_search
                Tab.Cola -> R.string.tab_queue })
            val icon = when (item) {
                Tab.Inicio -> if (selected) YouSongIcons.HomeFilled else YouSongIcons.Home
                Tab.Buscar -> if (selected) YouSongIcons.SearchFilled else YouSongIcons.Search
                Tab.Cola -> if (selected) YouSongIcons.QueueMusic else YouSongIcons.QueueMusicOutlined
            }
            val badge = if (item == Tab.Cola && queued > 0) stringResource(R.string.queue_badge_description, queued) else null
            NavigationBarItem(selected = selected, onClick = { onSelect(item) },
                label = { Text(label, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium) },
                modifier = Modifier.semantics { badge?.let { stateDescription = it } },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent,
                    selectedIconColor = colors.onSurface, selectedTextColor = colors.onSurface,
                    unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant),
                icon = {
                    if (badge != null) BadgedBox(badge = {
                        Badge(containerColor = YouSongTheme.extras.cta, contentColor = YouSongTheme.extras.onCta) {
                            Text(if (queued > 99) "99+" else queued.toString(), Modifier.clearAndSetSemantics {})
                        }
                    }) { Icon(icon, null) } else Icon(icon, null)
                })
        }
    }
}

/** One quiet line under the top bar when actions are not possible right now. */
@Composable
private fun ShellBanner(actions: SongActions, interactive: Boolean, contentShowsError: Boolean) {
    val snapshot = actions.snapshot
    // Before the first queue load, Inicio and Cola show their own full offline state; Buscar does not.
    val problem = if (snapshot == null && contentShowsError) actions.statusError?.takeIf { actions.error == null }
        else actions.error ?: actions.statusError
    val viewOnly = snapshot != null && !interactive && snapshot.state == ServiceState.ABIERTO && !actions.loading && !actions.pending
    if (problem == null && !viewOnly) return
    Surface(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xxs),
        shape = MaterialTheme.shapes.medium,
        color = if (problem != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (problem != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (problem == null) Text(stringResource(R.string.view_only_title), style = MaterialTheme.typography.titleSmall)
                Text(problem ?: actions.availabilityMessage, style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = actions::refresh, enabled = !actions.loading && !actions.pending) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
internal fun YouSongSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(Modifier.padding(horizontal = Spacing.md).widthIn(max = Spacing.contentMax)
            .semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            dismissAction = {
                IconButton(onClick = { data.dismiss() }) { Icon(YouSongIcons.Close, stringResource(R.string.action_close)) }
            }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val success = (data.visuals as? Notice)?.success == true
                Icon(if (success) YouSongIcons.Check else YouSongIcons.Error, null, Modifier.size(20.dp),
                    tint = if (success) YouSongTheme.extras.live else MaterialTheme.colorScheme.error)
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun ConfirmationScreen(confirmation: Confirmation, onQueue: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val song = confirmation.target
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(56.dp).background(YouSongTheme.extras.success, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(YouSongIcons.Check, null, Modifier.size(32.dp), tint = YouSongTheme.extras.onSuccess)
        }
        Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Text(confirmation.position?.let { stringResource(R.string.confirm_position, it) } ?: stringResource(R.string.confirm_no_position),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Cover(song.cover, song.title, Modifier.padding(top = Spacing.xs).size(160.dp), decorative = false,
            shape = MaterialTheme.shapes.large)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(song.title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CtaButton(onClick = onQueue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.confirm_view_queue)) }
            TextButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.confirm_keep_searching))
            }
        }
    }
}

@Composable
private fun LeaveDialog(pseudonym: String, confirmEnabled: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        title = { Text(stringResource(R.string.leave_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.leave_body))
                // Only shown here, on screen: never logged, saved or sent to a snackbar.
                if (pseudonym.isNotBlank()) Text(stringResource(R.string.leave_as, pseudonym),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(stringResource(R.string.leave_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.leave_cancel)) } })
}
