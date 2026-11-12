package cue.edu.co.yousong_movile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ConnectedScreen(api: ParticipantApi, session: ParticipantSession, onEnded: (String?) -> Unit) {
    key(api, session) { ConnectedSessionScreen(api, session, onEnded) }
}

@Composable
private fun ConnectedSessionScreen(api: ParticipantApi, session: ParticipantSession, onEnded: (String?) -> Unit) {
    val actions = rememberSongActions(api, session, onEnded)
    var tab by remember(session) { mutableStateOf("Buscar") }
    val status = actions.status
    var leaveError by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Inicio" to "⌂", "Buscar" to "⌕", "Cola" to "≡").forEach { (label, symbol) ->
                NavigationBarItem(selected = tab == label, onClick = { tab = label },
                    icon = { Text(symbol) }, label = { Text(label) })
            }
        }
    }) { padding ->
        val scroll = rememberScrollState()
        val contentModifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)
        Column(if (tab == "Buscar") contentModifier else contentModifier.verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("YouSong", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(status.name, style = MaterialTheme.typography.headlineMedium)
            Text("${status.pseudonym} · ${status.service}")
            (leaveError ?: actions.statusError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { leaveError = null; actions.refresh() }) { Text("Reintentar estado") }
            }
            actions.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            actions.notice?.let { Text(it) }
            if (!actions.ready && tab != "Cola") {
                TextButton(onClick = actions::refresh, enabled = !actions.loading && !actions.pending) { Text("Actualizar cola para habilitar acciones") }
            }
            when (tab) {
                "Inicio" -> {
                    Text("Elige lo que viene.", style = MaterialTheme.typography.headlineSmall)
                    Text("Busca canciones y consulta sus detalles en el catálogo conectado.")
                    Button(onClick = { tab = "Buscar" }) { Text("Explorar canciones") }
                    Text("La música comienza en el local", style = MaterialTheme.typography.titleMedium)
                    Text("El operador controla la reproducción. Solicita canciones desde el catálogo y apoya una vez cada canción de la cola. La app no reproduce audio.")
                    Text("Credencial válida hasta: ${session.expiresAt}", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !leaving && !actions.pending, onClick = {
                        leaving = true
                        scope.launch {
                            try { api.leave(session.token); onEnded("Saliste del local.") }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                if (failure is ApiFailure && failure.status == 401) onEnded(errorLabel(failure))
                                else leaveError = errorLabel(failure)
                            } finally { leaving = false }
                        }
                    }) { Text(if (leaving) "Saliendo…" else "Salir del local") }
                }
                "Buscar" -> CatalogScreen(api, session.token, actions) { onEnded(it) }
                else -> QueueScreen(actions)
            }
        }
    }
}

@Composable
private fun CatalogScreen(api: ParticipantApi, token: String, actions: SongActions, onExpired: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var nextPage by remember { mutableStateOf<String?>(null) }
    var requestedPage by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Song?>(null) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var suggestionsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api, token, query) {
        suggestions = emptyList(); suggestionsError = null
        val normalized = ClientContract.query(query) ?: return@LaunchedEffect
        delay(400)
        try { suggestions = api.suggestions(token, normalized) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            suggestionsError = errorLabel(failure)
            if (failure is ApiFailure && failure.status == 401) onExpired(errorLabel(failure))
        }
    }
    LaunchedEffect(api, token, query, requestedPage, retry) {
        val normalized = ClientContract.query(query)
        error = null
        if (requestedPage == null) { songs = emptyList(); nextPage = null; searched = false }
        if (normalized == null) { loading = false; return@LaunchedEffect }
        loading = true
        delay(400) // Keep provider search debounced and independently paginated.
        try {
            val result = api.search(token, normalized, requestedPage)
            songs = if (requestedPage == null) result.songs else (songs + result.songs).distinctBy { it.videoId }
            nextPage = result.nextPage; searched = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = errorLabel(failure)
            if (failure is ApiFailure && failure.status == 401) onExpired(errorLabel(failure))
        } finally { loading = false }
    }
    OutlinedTextField(query, { query = it; requestedPage = null; selected = null },
        modifier = Modifier.fillMaxWidth(), label = { Text("¿Qué quieres escuchar?") }, singleLine = true,
        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = ""; requestedPage = null }) { Text("×") } })
    suggestionsError?.let { Text("Sugerencias: $it", color = MaterialTheme.colorScheme.error) }
    Text("Explorar canciones", style = MaterialTheme.typography.headlineSmall)
    if (ClientContract.query(query) == null) Text("Escribe entre 2 y 100 caracteres. El catálogo se consulta por búsqueda.")
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { retry++ }) { Text("Reintentar búsqueda") }
    }
    if (searched && songs.isEmpty() && !loading && error == null) Text("No encontramos canciones. Prueba con otro título o artista.")
    LazyVerticalGrid(columns = GridCells.Adaptive(240.dp), modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        if (suggestions.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sugerencias", style = MaterialTheme.typography.titleMedium)
                suggestions.forEach { suggestion ->
                    TextButton(onClick = {
                        val song = suggestion.song
                        if (song != null) selected = song
                        else { query = suggestion.text; requestedPage = null; selected = null }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(suggestion.text) }
                }
            }
        }
        items(songs, key = { it.videoId }) { song ->
            Card(Modifier.fillMaxWidth().clickable { selected = song }) {
                SongThumbnail(song.thumbnailUrl, song.title, Modifier.fillMaxWidth().height(180.dp))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist)
                    Text(durationLabel(song.durationSeconds), style = MaterialTheme.typography.bodySmall)
                    Text("Ver detalle", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { actions.request(song) }, enabled = actions.canRequest(song),
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Solicitar o apoyar") }
                }
            }
        }
        if (nextPage != null) item {
            TextButton(enabled = !loading, onClick = { requestedPage = nextPage }) { Text("Cargar más canciones") }
        }
    }
    selected?.let { SongDetail(it, actions) { selected = null } }
}
