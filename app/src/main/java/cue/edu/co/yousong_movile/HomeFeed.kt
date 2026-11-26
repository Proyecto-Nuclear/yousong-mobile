package cue.edu.co.yousong_movile

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The home carousels' data. Both endpoints are anonymous: no bearer, no participant credential.
 * They load once per connected screen and again only on an explicit refresh (or, for the local's
 * rankings, when the playing song changes). They never share the queue's transport lock, so a slow
 * catalog cannot delay the queue poll, and a failure here never touches the session.
 */
@Stable
internal class HomeFeed(private val api: ParticipantApi, slug: String?, private val scope: CoroutineScope) {
    private val slug = validatedQrSlug(slug)
    var trends by mutableStateOf<Feed<Trends>>(Feed.Loading)
        private set
    var highlights by mutableStateOf<Feed<Highlights>>(if (this.slug == null) Feed.Unavailable else Feed.Loading)
        private set
    private var trendsJob: Job? = null
    private var highlightsJob: Job? = null

    fun loadTrends() {
        if (trendsJob?.isActive == true) return
        trends = trends.reloading()
        trendsJob = scope.launch {
            trends = try { trends.loaded(api.trends()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { trends.failed(errorLabel(failure)) }
        }
    }

    fun loadHighlights() {
        val slug = slug ?: return
        if (highlightsJob?.isActive == true) return
        highlights = highlights.reloading()
        highlightsJob = scope.launch {
            highlights = try { highlights.loaded(api.highlights(slug)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                // An unknown slug means this local has no public rankings: hide them instead of nagging.
                if (failure is ApiFailure && failure.status == 404) Feed.Unavailable
                else highlights.failed(errorLabel(failure))
            }
        }
    }

    fun refresh() { loadTrends(); loadHighlights() }
}

@Composable
internal fun rememberHomeFeed(api: ParticipantApi, session: ParticipantSession): HomeFeed {
    val scope = rememberCoroutineScope()
    val feed = remember(api, session) { HomeFeed(api, session.qrSlug, scope) }
    LaunchedEffect(feed) { feed.refresh() }
    return feed
}
