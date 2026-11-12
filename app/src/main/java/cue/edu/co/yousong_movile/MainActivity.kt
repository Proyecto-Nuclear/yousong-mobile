package cue.edu.co.yousong_movile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cue.edu.co.yousong_movile.ui.theme.YousongmovileTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YousongmovileTheme { CustomerApp(this) } }
    }
}

@Composable
private fun CustomerApp(activity: ComponentActivity) {
    var active by remember { mutableStateOf(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, _ ->
            active = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    val lifecycle = remember { SessionLifecycle(KeystoreSessionStore(context.applicationContext)) }
    // Credentials never enter saved instance state or rememberSaveable.
    var record by remember { mutableStateOf(lifecycle.restore()) }
    val session = record?.second
    val defaultBase = context.getString(R.string.api_base_url)
    var base by remember { mutableStateOf(record?.first ?: defaultBase) }
    var notice by remember { mutableStateOf<String?>(null) }
    val endSession: (String?) -> Unit = { message ->
        lifecycle.ended()
        record = null; base = defaultBase; notice = message
    }
    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        val expiry = expiresAtMillis(current.expiresAt) ?: 0
        delay((expiry - System.currentTimeMillis()).coerceAtLeast(0))
        endSession("La credencial caducó. Vuelve a unirte.")
    }
    Surface(Modifier.fillMaxSize()) {
        val current = session
        if (current == null) EntryScreen(base, active, notice) {
            val origin = requireNotNull(ClientContract.apiBase(base, BuildConfig.DEBUG))
            notice = try { lifecycle.joined(origin, it); null } catch (_: Exception) {
                lifecycle.ended()
                "No se pudo guardar la sesión de forma segura. Al cerrar la app tendrás que unirte de nuevo."
            }
            base = origin; record = origin to it
        } else if (active) {
            val api = remember(base) { ParticipantApi(requireNotNull(ClientContract.apiBase(base, BuildConfig.DEBUG))) }
            Column {
                notice?.let { Text(it, Modifier.safeDrawingPadding()) }
                ConnectedScreen(api, current, endSession)
            }
        }
    }
}
