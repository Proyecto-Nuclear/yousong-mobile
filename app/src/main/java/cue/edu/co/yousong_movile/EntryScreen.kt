package cue.edu.co.yousong_movile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun EntryScreen(base: String, active: Boolean, notice: String?, onJoined: (ParticipantSession) -> Unit) {
    var establishment by remember { mutableStateOf<Establishment?>(null) }
    var policy by remember { mutableStateOf<PrivacyPolicy?>(null) }
    var f1 by remember { mutableStateOf(false) }
    var f2 by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val origin = ClientContract.apiBase(base, BuildConfig.DEBUG)
    val flow = remember { EntryFlow() }
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<Job?>(null) }
    fun clearConsent() {
        establishment = null; policy = null; f1 = false; f2 = false
    }
    // Keep result launchers registered while the external camera activity covers this screen.
    // Stopped/disposed requests lose ownership even if blocking HTTP finishes later.
    DisposableEffect(active) {
        if (!active) {
            flow.reset(); request?.cancel(); busy = false; joining = false
            clearConsent()
        }
        onDispose { flow.reset(); request?.cancel() }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        scanning = false
        val contents = result.contents
        val slug = contents?.let(ClientContract::slug)
        error = when {
            contents == null -> "Escaneo cancelado. Puedes volver a escanear el QR."
            slug == null -> "QR inválido. Escanea el QR del establecimiento."
            else -> null
        }
        scanned = slug
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanner.launch(qrScannerOptions())
        else {
            scanning = false
            error = "Permiso de cámara denegado. Habilítalo en los ajustes para escanear el QR."
        }
    }
    LaunchedEffect(scanned, active) {
        val slug = scanned ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        scanned = null
        val ticket = flow.begin() ?: return@LaunchedEffect
        clearConsent()
        if (origin == null) {
            flow.finish(ticket)
            return@LaunchedEffect
        }
        busy = true; error = null
        request = scope.launch {
            try {
                val api = ParticipantApi(origin)
                val place = api.establishment(slug)
                val privacy = api.policy()
                if (flow.owns(ticket)) { establishment = place; policy = privacy }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (flow.owns(ticket)) error = entryErrorLabel(failure) }
            finally { if (flow.finish(ticket)) busy = false }
        }
    }
    Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("YouSong", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Elige lo que viene.", style = MaterialTheme.typography.headlineMedium)
        Text("Tu música, en el lugar donde estás.", style = MaterialTheme.typography.bodyLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("♫", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text("Conecta con tu local", style = MaterialTheme.typography.titleLarge)
                Text("Escanea el QR del establecimiento para descubrir canciones y elegir qué quieres escuchar.")
                Button(modifier = Modifier.fillMaxWidth(), enabled = active && origin != null && !busy && !scanning,
                    onClick = {
                        if (!busy && !scanning) {
                            flow.reset(); request?.cancel(); clearConsent(); scanned = null; error = null
                            scanning = true
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(if (establishment == null) "Escanear QR" else "Escanear otro QR") }
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (origin == null) Text("El servicio no está configurado correctamente. Contacta al establecimiento.",
            color = MaterialTheme.colorScheme.error)
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (joining) "Ingresando a la búsqueda…" else "Conectando con el local y su política de privacidad…")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val place = establishment
        val privacy = policy
        if (place != null && privacy != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(place.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Servicio: ${place.service}")
                    if (!place.canJoin) Text("El local no admite participantes en este momento. Vuelve a escanear para consultar su estado.")
                    Text("Privacidad · versión ${privacy.version}", style = MaterialTheme.typography.titleMedium)
                    privacy.purposes.forEach { purpose -> Text("${purpose.code}: ${purpose.description}") }
                    Text("F1 permite operar la sesión musical y es obligatorio. F2 permite comentarios voluntarios sobre usabilidad y es opcional. Tú decides antes de ingresar.")
                    ConsentRow(f1, !busy, "Autorizo F1: participación en la sesión (obligatorio)") { f1 = it }
                    ConsentRow(f2, !busy, "Autorizo F2: comentarios de usabilidad (opcional)") { f2 = it }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = active && !busy && place.canJoin && f1,
                        onClick = {
                            if (f1 && place.canJoin && !busy && origin != null) {
                                val ticket = flow.begin()
                                if (ticket != null) {
                                    busy = true; joining = true; error = null
                                    request = scope.launch {
                                        try {
                                            val session = ParticipantApi(origin).join(place.slug, privacy, f1, f2)
                                            if (flow.owns(ticket)) onJoined(session)
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) {
                                            if (flow.owns(ticket)) {
                                                error = entryErrorLabel(failure)
                                                // A changed policy always requires a new lookup and explicit consent.
                                                if (failure is ApiFailure && failure.code == "POLICY_VERSION_MISMATCH") clearConsent()
                                            }
                                        } finally {
                                            if (flow.finish(ticket)) { busy = false; joining = false }
                                        }
                                    }
                                }
                            }
                        }) { Text("Ingresar a buscar canciones") }
                }
            }
        } else if (!busy) Text("Sin iniciar sesión. Solo necesitas el QR del local.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun entryErrorLabel(failure: Exception): String = when {
    failure is ApiFailure && failure.status == 404 -> "No se encontró el establecimiento. Vuelve a escanear su QR."
    failure !is ApiFailure -> "No se pudo conectar con el local. Revisa tu conexión y vuelve a escanear."
    else -> errorLabel(failure)
}

@Composable
private fun ConsentRow(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label, Modifier.padding(top = 12.dp).weight(1f))
    }
}
