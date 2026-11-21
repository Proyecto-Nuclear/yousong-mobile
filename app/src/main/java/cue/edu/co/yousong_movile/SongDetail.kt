package cue.edu.co.yousong_movile

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.LocalReduceMotion
import cue.edu.co.yousong_movile.ui.theme.SheetShape
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YouSongTheme
import cue.edu.co.yousong_movile.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Song detail as a bottom sheet: hero cover, title, meta and one primary action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongDetail(song: Song, cta: SongCta, pending: Boolean, onAction: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SongDetailContent(song, cta, pending, onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongDetailContent(song: Song, cta: SongCta, pending: Boolean, onAction: () -> Unit) {
    // The action stays pinned under the scrolling details so it is always reachable.
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.readableWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Cover(song.thumbnailUrl, song.title, Modifier.padding(top = Spacing.xs).fillMaxWidth(0.72f).widthIn(max = 280.dp)
                .aspectRatio(1f), decorative = false, shape = MaterialTheme.shapes.large)
            Spacer(Modifier.height(Spacing.sm))
            Text(song.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                song.genre?.let { GenreChip(it) }
                clockLabel(song.durationSeconds)?.let { clock ->
                    val description = stringResource(R.string.duration_description, clock)
                    Pill(clock, Modifier.semantics { contentDescription = description }, YouSongIcons.Schedule)
                }
            }
            if (!song.verified) Text(stringResource(R.string.unverified_artist_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.detail_how_it_works), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs))
        }
        SongCtaButton(cta, pending, onAction, Modifier.readableWidth().padding(top = Spacing.md))
    }
}

@Composable
internal fun SongCtaButton(cta: SongCta, pending: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val height = Modifier.heightIn(min = 52.dp)
    when {
        pending -> CtaButton(onClick = {}, enabled = false, modifier = modifier) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.xs)); Text(stringResource(R.string.cta_pending))
        }
        cta == SongCta.Request -> CtaButton(onClick = onAction, modifier = modifier) {
            Icon(YouSongIcons.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.cta_request))
        }
        cta == SongCta.Vote -> CtaButton(onClick = onAction, modifier = modifier) {
            Icon(YouSongIcons.ArrowUp, null, Modifier.size(20.dp)); Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.cta_vote))
        }
        cta == SongCta.Voted -> FilledTonalButton(onClick = {}, enabled = false, modifier = modifier.then(height),
            colors = ButtonDefaults.filledTonalButtonColors(disabledContainerColor = YouSongTheme.extras.success,
                disabledContentColor = YouSongTheme.extras.onSuccess)) {
            Icon(YouSongIcons.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.cta_voted))
        }
        else -> OutlinedButton(onClick = {}, enabled = false, modifier = modifier.then(height)) {
            Text(stringResource(R.string.cta_unavailable))
        }
    }
}

@Composable
internal fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(MaterialTheme.shapes.extraSmall).background(YouSongTheme.extras.coverScrim)
        .padding(horizontal = Spacing.xs, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium.tabular(), color = androidx.compose.ui.graphics.Color.White)
}

internal const val COVER_IMAGE_TAG = "cover-image"
internal const val COVER_PLACEHOLDER_TAG = "cover-placeholder"

/** Loads a cover through [LocalCoverLoader]; production always uses the anonymous HTTPS loader. */
internal val LocalCoverLoader = staticCompositionLocalOf<suspend (String) -> ImageBitmap?> { { secureCover(it) } }

@Composable
internal fun Cover(url: String?, title: String, modifier: Modifier = Modifier, decorative: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraSmall) {
    // A new url is a new image: never carry the previous song's bitmap (or its state) over.
    key(url) { CoverFor(url, title, modifier, decorative, shape) }
}

@Composable
private fun CoverFor(url: String?, title: String, modifier: Modifier, decorative: Boolean, shape: Shape) {
    val loader = LocalCoverLoader.current
    val cached = url?.let(CoverCache::get)
    val image by produceState(cached, url) {
        if (url == null || value != null) return@produceState
        value = loader(url)
    }
    val description = if (decorative) null else stringResource(R.string.cover_description, title)
    val reduce = LocalReduceMotion.current
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .semantics { if (description != null) { contentDescription = description; role = Role.Image } }) {
        Crossfade(image, label = "cover", animationSpec = if (reduce || cached != null) snap() else tween(220)) { bitmap ->
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().testTag(COVER_IMAGE_TAG), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().testTag(COVER_PLACEHOLDER_TAG), contentAlignment = Alignment.Center) {
                Icon(YouSongIcons.MusicNote, null, Modifier.fillMaxSize(0.38f).widthIn(max = 48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

/** Process-wide, memory-only cover cache (~8 MB). Never written to disk. */
internal object CoverCache {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    fun get(url: String): ImageBitmap? = cache.get(url)
    fun put(url: String, image: ImageBitmap) { cache.put(url, image) }
}

private suspend fun secureCover(url: String): ImageBitmap? =
    CoverCache.get(url) ?: withContext(Dispatchers.IO) { loadThumbnail(url) }?.also { CoverCache.put(url, it) }

private fun loadThumbnail(raw: String?): ImageBitmap? {
    return runCatching {
        val uri = raw?.let { URI(it) } ?: return null
        // Thumbnails are anonymous, HTTPS-only, bounded and never inherit the participant bearer.
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null) return null
        val connection = URL(raw).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.useCaches = false
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { it.readBytesBounded(2 * 1024 * 1024) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } finally { connection.disconnect() }
    }.getOrNull()
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

