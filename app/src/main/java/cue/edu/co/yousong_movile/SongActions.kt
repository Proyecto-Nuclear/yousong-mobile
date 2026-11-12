package cue.edu.co.yousong_movile

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** One in-memory coordinator per visible participant session; no credential persistence. */
internal class SongActions(
    private val api: ParticipantApi,
    session: ParticipantSession,
    private val scope: CoroutineScope,
    private val onEnded: (String?) -> Unit
) {
    private val token = session.token
    var status by mutableStateOf(SessionStatus(session.name, session.pseudonym, session.service))
        private set
    var statusError by mutableStateOf<String?>(null)
        private set
    private var active = true
    private var mutationJob: Job? = null
    private val flow = QueueFlow()
    private val transport = Mutex()
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    var snapshot by mutableStateOf<QueueSnapshot?>(null)
        private set
    var pending by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun refresh() { if (active) refreshes.trySend(Unit) }
    fun dispose() {
        active = false
        flow.invalidate()
        mutationJob?.cancel()
        refreshes.close()
    }

    suspend fun poll() {
        while (active) {
            transport.withLock {
                readStatus()
                readQueue()
            }
            if (!active) return
            withTimeoutOrNull(SessionLifecycle.POLL_INTERVAL_MS) { refreshes.receiveCatching() }
        }
    }

    private suspend fun readStatus() {
        if (!active) return
        try {
            val latest = api.status(token)
            currentCoroutineContext().ensureActive()
            if (!active) return
            status = latest
            statusError = null
            if (latest.service == "CERRADO") {
                active = false
                ready = false
                flow.invalidate()
                onEnded("La sesión del local terminó.")
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (active) {
                statusError = errorLabel(failure)
                if (failure is ApiFailure && failure.status == 401) handleFailure(failure)
            }
        }
    }

    private suspend fun readQueue() {
        if (!active) return
        loading = true
        try {
            val latest = api.queue(token)
            currentCoroutineContext().ensureActive()
            if (flow.accept(latest)) {
                snapshot = latest
                ready = true
                error = null
                if (latest.state == ServiceState.CERRADO) {
                    active = false
                    ready = false
                    flow.invalidate()
                    onEnded("La sesión del local terminó.")
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { handleFailure(failure) }
        finally { loading = false }
    }

    fun canRequest(song: Song): Boolean {
        val value = snapshot ?: return false
        val voted = value.items.firstOrNull { it.song.videoId == song.videoId }?.voted
            ?: value.playing?.takeIf { it.song.videoId == song.videoId }?.voted
        val exists = value.items.any { it.song.videoId == song.videoId } || value.playing?.song?.videoId == song.videoId
        return active && ready && !pending && value.state == ServiceState.ABIERTO && (!exists || voted == false)
    }

    fun request(song: Song) {
        if (canRequest(song)) mutate { api.requestSong(token, song.videoId) }
    }

    fun vote(requestId: String, voted: Boolean?) {
        if (ready && canVote(snapshot?.state, voted, pending)) mutate { api.vote(token, requestId) }
    }

    private fun mutate(action: suspend () -> SupportResult) {
        val ticket = flow.beginMutation() ?: return
        pending = true
        ready = false
        notice = null
        mutationJob = scope.launch {
            try {
                transport.withLock {
                    if (!flow.owns(ticket)) return@withLock
                    try {
                        val result = action()
                        currentCoroutineContext().ensureActive()
                        if (flow.owns(ticket)) notice = if (result.created) "Solicitud aceptada con tu primer voto." else "Voto registrado."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (flow.owns(ticket)) {
                            if (failure is ApiFailure && failure.status < 500) {
                                notice = errorLabel(failure)
                                if (failure.status == 401) handleFailure(failure)
                            } else notice = "No se pudo confirmar el resultado. La acción pudo ser aceptada y no se puede deshacer aquí. Actualiza la cola antes de repetir."
                        }
                    } finally { flow.finishMutation(ticket) }
                    // No retries: reconcile even after conflicts or an uncertain result.
                    readQueue()
                }
            } finally { pending = false }
        }
    }

    private fun handleFailure(failure: Exception) {
        if (!active) return
        error = errorLabel(failure)
        if (failure is ApiFailure && failure.status == 401) {
            active = false
            ready = false
            flow.invalidate()
            onEnded(error)
        }
    }
}

@Composable
internal fun rememberSongActions(api: ParticipantApi, session: ParticipantSession, onEnded: (String?) -> Unit): SongActions {
    val scope = rememberCoroutineScope()
    val ended by rememberUpdatedState(onEnded)
    val actions = remember(api, session) { SongActions(api, session, scope) { ended(it) } }
    DisposableEffect(actions) { onDispose { actions.dispose() } }
    LaunchedEffect(actions) { actions.poll() }
    return actions
}
