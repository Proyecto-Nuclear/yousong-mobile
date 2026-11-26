package cue.edu.co.yousong_movile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun EntryScreen(base: String, active: Boolean, notice: String?, onJoined: (ParticipantSession) -> Unit) {
    var establishment by remember { mutableStateOf<Establishment?>(null) }
    var policy by remember { mutableStateOf<PrivacyPolicy?>(null) }
    var fresh by remember { mutableStateOf(false) }
    var f1 by remember { mutableStateOf(false) }
    var f2 by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraDenied by remember { mutableStateOf(false) }
    val origin = ClientContract.apiBase(base, BuildConfig.DEBUG, BuildConfig.LAN_API_HOST)
    val flow = remember { EntryFlow() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val context = LocalContext.current
    var request by remember { mutableStateOf<Job?>(null) }
    fun clearConsent() {
        establishment = null; policy = null; f1 = false; f2 = false; fresh = false
    }
    fun entryErrorLabel(failure: Exception): String = when {
        failure is ApiFailure && failure.status == 404 -> resources.getString(R.string.entry_not_found)
        failure !is ApiFailure -> resources.getString(R.string.entry_offline)
        else -> errorLabel(failure)
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
            contents == null -> resources.getString(R.string.entry_scan_cancelled)
            slug == null -> resources.getString(R.string.entry_scan_invalid)
            else -> null
        }
        scanned = slug
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) scanner.launch(qrScannerOptions(resources.getString(R.string.scanner_prompt)))
        else scanning = false
    }
    fun lookup(slug: String, newScan: Boolean) {
        if (!active || origin == null) return
        val ticket = flow.begin() ?: return
        if (newScan) clearConsent()
        fresh = false
        busy = true; error = null
        request = scope.launch {
            try {
                val api = ParticipantApi(origin)
                val place = api.establishment(slug)
                require(place.slug == slug)
                val privacy = api.policy()
                if (flow.owns(ticket)) {
                    if (!retainsConsent(policy, privacy)) { f1 = false; f2 = false }
                    establishment = place; policy = privacy; fresh = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (flow.owns(ticket)) error = entryErrorLabel(failure) }
            finally { if (flow.finish(ticket)) busy = false }
        }
    }
    LaunchedEffect(scanned, active) {
        val slug = scanned ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        scanned = null
        lookup(slug, newScan = true)
    }
    fun startScan() {
        if (!busy && !scanning) {
            flow.reset(); request?.cancel(); clearConsent(); scanned = null; error = null; cameraDenied = false
            scanning = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    fun join(place: Establishment, privacy: PrivacyPolicy) {
        if (f1 && fresh && canParticipate(place) && !busy && origin != null) {
            val ticket = flow.begin() ?: return
            busy = true; joining = true; error = null
            request = scope.launch {
                try {
                    val session = ParticipantApi(origin).join(place.slug, privacy, f1, f2)
                    if (flow.owns(ticket)) onJoined(session)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (flow.owns(ticket)) {
                        error = entryErrorLabel(failure)
                        fresh = false
                        // A changed policy always requires a new lookup and explicit consent.
                        if (failure is ApiFailure && failure.code == "POLICY_VERSION_MISMATCH") clearConsent()
                    }
                } finally {
                    if (flow.finish(ticket)) { busy = false; joining = false }
                }
            }
        }
    }

    val place = establishment
    val privacy = policy
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.readableWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Icon(YouSongIcons.BrandMark, stringResource(R.string.brand_mark_description), Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.entry_title), style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.entry_body), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CtaButton(onClick = ::startScan, enabled = active && origin != null && !busy && !scanning,
                modifier = Modifier.fillMaxWidth()) {
                Icon(YouSongIcons.QrScan, null, Modifier.size(20.dp)); Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(if (place == null) R.string.scan_qr else R.string.scan_other_qr))
            }
            if (cameraDenied) StatePanel(YouSongIcons.NoCamera, stringResource(R.string.state_camera_title),
                stringResource(R.string.state_camera_body), action = stringResource(R.string.state_camera_action),
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                })
            notice?.let { Message(it, error = false) }
            if (origin == null) Message(stringResource(R.string.entry_not_configured), error = true)
            error?.let { Message(it, error = true) }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(if (joining) R.string.entry_joining else R.string.entry_connecting),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (place != null && privacy != null) {
                EstablishmentCard(place, refreshEnabled = active && !busy) { lookup(place.slug, newScan = false) }
                PrivacySection(privacy, f1, f2, enabled = !busy, onF1 = { f1 = it }, onF2 = { f2 = it })
                if (!fresh) Text(stringResource(R.string.entry_refresh_first), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CtaButton(onClick = { join(place, privacy) }, enabled = active && !busy && fresh && canParticipate(place) && f1,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.join_action)) }
            } else if (!busy && !cameraDenied) Text(stringResource(R.string.entry_no_account), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(Spacing.md).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(if (error) YouSongIcons.Error else YouSongIcons.MusicNote, null, Modifier.size(20.dp),
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun EstablishmentCard(place: Establishment, refreshEnabled: Boolean, onRefresh: () -> Unit) {
    val opening = remember(place.nextOpening) {
        opening(place.nextOpening, System.currentTimeMillis(), TimeZone.getDefault(), Locale.forLanguageTag("es"))
    }
    val chip = serviceChip(place.service)
    val stateLabel = stringResource(when {
        chip == ServiceChip.Live && place.canJoin -> R.string.service_live
        chip == ServiceChip.Live -> R.string.service_full
        chip == ServiceChip.Paused -> R.string.service_paused
        chip == ServiceChip.Closed -> R.string.service_closed
        chip == ServiceChip.Maintenance -> R.string.service_maintenance
        else -> R.string.service_unknown
    })
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(place.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(
                    if (canParticipate(place)) YouSongTheme.extras.live else MaterialTheme.colorScheme.outline))
                Text(stateLabel, style = MaterialTheme.typography.bodyMedium)
            }
            opening?.let {
                Text(when (it.dayOffset) {
                    0 -> stringResource(R.string.opening_today, it.time)
                    1 -> stringResource(R.string.opening_tomorrow, it.time)
                    else -> stringResource(R.string.opening_other, it.date, it.time)
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRefresh, enabled = refreshEnabled, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(YouSongIcons.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.entry_refresh_state))
            }
        }
    }
}

@Composable
internal fun PrivacySection(privacy: PrivacyPolicy, f1: Boolean, f2: Boolean, enabled: Boolean,
    onF1: (Boolean) -> Unit, onF2: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() })
            Text(stringResource(R.string.privacy_version, privacy.version), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val described = privacy.purposes.associateBy { it.code }
        ConsentRow(f1, enabled, stringResource(R.string.consent_required),
            described["F1"]?.description ?: stringResource(R.string.consent_required_body), onF1)
        ConsentRow(f2, enabled, stringResource(R.string.consent_optional),
            described["F2"]?.description ?: stringResource(R.string.consent_optional_body), onF2)
        privacy.purposes.filter { it.code != "F1" && it.code != "F2" }.forEach { purpose ->
            Row(Modifier.padding(start = Spacing.sm, top = Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                Text(purpose.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (privacy.sections.isNotEmpty()) FullPolicy(privacy.sections)
    }
}

/** The full notice, closed by default so the two decisions stay visible. */
@Composable
private fun FullPolicy(sections: List<PolicySection>) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = Spacing.touch)) {
        Text(stringResource(if (open) R.string.privacy_hide_full else R.string.privacy_read_full))
    }
    if (open) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    Text(section.text, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The whole row is one checkbox target with merged semantics. */
@Composable
private fun ConsentRow(checked: Boolean, enabled: Boolean, title: String, body: String, onChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else YouSongTheme.extras.hairline)) {
        Row(Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(Spacing.sm).heightIn(min = Spacing.touch),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Checkbox(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(Spacing.xs))
            Column(Modifier.weight(1f).padding(top = Spacing.xxs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
