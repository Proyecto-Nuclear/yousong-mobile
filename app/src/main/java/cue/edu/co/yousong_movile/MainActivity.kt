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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
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
    val resources = LocalResources.current
    val lifecycle = remember { SessionLifecycle(KeystoreSessionStore(context.applicationContext)) }
    // Credentials never enter saved instance state or rememberSaveable.
    var record by remember { mutableStateOf(lifecycle.restore()) }
    val session = record?.second
    val defaultBase = stringResource(R.string.api_base_url)
    var base by remember { mutableStateOf(record?.first ?: defaultBase) }
    var notice by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf<String?>(null) }
    val endSession: (String?) -> Unit = { message ->
        lifecycle.ended()
        record = null; base = defaultBase; notice = null; ended = message ?: ""
    }
    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        val expiry = expiresAtMillis(current.expiresAt) ?: 0
        delay((expiry - System.currentTimeMillis()).coerceAtLeast(0))
        endSession(resources.getString(R.string.session_expired_notice))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val current = session
        val reason = ended
        if (current == null && reason != null) SessionEndedScreen(reason) { ended = null }
        else if (current == null) EntryScreen(base, active, notice) {
            val origin = requireNotNull(ClientContract.apiBase(base, BuildConfig.DEBUG))
            notice = try { lifecycle.joined(origin, it); null } catch (_: Exception) {
                lifecycle.ended()
                resources.getString(R.string.joined_notice_unsafe)
            }
            base = origin; record = origin to it
        } else if (active) {
            val api = remember(base) { ParticipantApi(requireNotNull(ClientContract.apiBase(base, BuildConfig.DEBUG))) }
            ConnectedScreen(api, current, notice, endSession)
        }
    }
}

/** "La noche terminó": one sentence and one way back in. */
@Composable
internal fun SessionEndedScreen(reason: String, onScanAgain: () -> Unit) {
    val left = stringResource(R.string.left_notice)
    val title = stringResource(when (reason) {
        SESSION_CLOSED_NOTICE -> R.string.state_ended_title
        left -> R.string.state_left_title
        else -> R.string.state_session_over_title
    })
    FullScreenState(if (reason == left) YouSongIcons.Leave else YouSongIcons.Night, title,
        reason.takeIf { it.isNotBlank() && it != left }, stringResource(R.string.state_scan_again), onScanAgain,
        Modifier.safeDrawingPadding())
}
