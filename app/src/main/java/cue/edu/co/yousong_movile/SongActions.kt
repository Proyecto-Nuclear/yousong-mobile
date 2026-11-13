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
/** The venue closed the night: the end screen titles this case differently. */
internal const val SESSION_CLOSED_NOTICE = "El local cerró la sesión de esta noche."

/** One finished mutation: a new instance per result so repeated identical notices still show. */
internal class ActionOutcome(val message: String?, val result: SupportResult?)

internal class SongActions(
    private val api: ParticipantApi,
    session: ParticipantSession,
    private val scope: CoroutineScope,
    private val onEnded: (String?) -> Unit
) {
    private val token = session.token
    private val readiness = SessionReadiness(session.qrSlug)
    var availabilityMessage by mutableStateOf(readiness.label)
        private set
    private var available by mutableStateOf(false)
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
    /** Read-only mirror of [notice] with the server result, so the UI can name the song. */
    var outcome by mutableStateOf<ActionOutcome?>(null)
        private set

    fun refresh() {
        if (active) {
            ready = false; available = false
            readiness.unavailable()
            availabilityMessage = readiness.label
            refreshes.trySend(Unit)
        }
    }
    fun dispose() {
        active = false
        ready = false; available = false
        readiness.unavailable()
        flow.invalidate()
        mutationJob?.cancel()
        refreshes.close()
    }

    suspend fun poll() {
        while (active) {
            transport.withLock {
                readStatus()
                readAvailability()
                readQueue()
            }
            if (!active) return
            withTimeoutOrNull(SessionLifecycle.POLL_INTERVAL_MS) { refreshes.receiveCatching() }
        }
    }

    private suspend fun readStatus() {
        if (!active) return
        available = false
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
                onEnded(SESSION_CLOSED_NOTICE)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (active) {
                statusError = errorLabel(failure)
                if (failure is ApiFailure && failure.status == 401) handleFailure(failure)
            }
        }
    }

    private suspend fun readAvailability() {
        if (!active) return
        readiness.unavailable()
        available = false
        val slug = readiness.slug
        if (slug != null) {
            try {
                val latest = api.establishment(slug)
                currentCoroutineContext().ensureActive()
                if (!active) return
                readiness.accept(latest)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // This is a public lookup, not participant authentication: never erase the credential.
                readiness.unavailable()
            }
        }
        availabilityMessage = readiness.label
    }

    private suspend fun readQueue() {
        if (!active) return
        ready = false
        available = false
        loading = true
        try {
            val latest = api.queue(token)
            currentCoroutineContext().ensureActive()
            if (flow.accept(latest)) {
                snapshot = latest
                ready = true
                available = statusError == null && readiness.canAct(latest.state, status.service)
                error = null
                if (latest.state == ServiceState.CERRADO) {
                    active = false
                    ready = false
                    flow.invalidate()
                    onEnded(SESSION_CLOSED_NOTICE)
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
        return active && available && ready && !pending && value.state == ServiceState.ABIERTO && (!exists || voted == false)
    }

    fun request(song: Song) {
        if (canRequest(song)) mutate { api.requestSong(token, song.videoId) }
    }

    fun canSupport(voted: Boolean?): Boolean = available && ready && canVote(snapshot?.state, voted, pending)

    fun vote(requestId: String, voted: Boolean?) {
        if (available && ready && canVote(snapshot?.state, voted, pending)) mutate { api.vote(token, requestId) }
    }

    private fun mutate(action: suspend () -> SupportResult) {
        if (!available || !active) return
        val ticket = flow.beginMutation() ?: return
        pending = true
        ready = false
        notice = null
        mutationJob = scope.launch {
            try {
                transport.withLock {
                    if (!flow.owns(ticket)) return@withLock
                    try {
                        if (!available) return@withLock
                        val result = action()
                        currentCoroutineContext().ensureActive()
                        if (flow.owns(ticket)) {
                            notice = if (result.created) "Solicitud aceptada con tu primer voto." else "Voto registrado."
                            outcome = ActionOutcome(notice, result)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (flow.owns(ticket)) {
                            if (failure is ApiFailure && failure.status < 500) {
                                notice = errorLabel(failure)
                                if (failure.status == 401) handleFailure(failure)
                            } else notice = "No pudimos confirmar tu acción. Puede que sí se haya registrado: revisa la cola antes de intentarlo de nuevo."
                            outcome = ActionOutcome(notice, null)
                        }
                    } finally { flow.finishMutation(ticket) }
                    // No retries: reconcile even after conflicts or an uncertain result.
                    readStatus()
                    readAvailability()
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
