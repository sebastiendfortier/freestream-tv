package com.freestream.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.freestream.data.db.WatchHistoryEntity
import com.freestream.data.model.MediaItem
import com.freestream.data.repository.MediaRepository
import com.freestream.player.TvPlayerActivity
import com.freestream.resolver.StreamResolver
import com.freestream.resolver.TvEpisodeInfo
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.components.TvFilterChip
import com.freestream.ui.theme.AccentRed
import com.freestream.ui.theme.SurfaceDark
import com.freestream.ui.theme.SurfaceVariantDark
import com.freestream.ui.theme.TextMuted
import com.freestream.ui.theme.TextWhite
import kotlinx.coroutines.launch

private fun episodeHistoryKey(seriesTitle: String, season: Int, episode: Int): String =
    "$seriesTitle|S${season}E$episode"

private fun formatTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val hrs = mins / 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins % 60, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaPlayDialog(
    item: MediaItem,
    streamResolver: StreamResolver,
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onSelectTag: ((String) -> Unit)? = null,
    initialSeason: Int = 1,
    initialEpisode: Int = 1,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = item.type.equals("TV", ignoreCase = true)

    var seasons by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedSeason by remember { mutableIntStateOf(initialSeason.coerceAtLeast(1)) }
    var episodes by remember { mutableStateOf<List<TvEpisodeInfo>>(emptyList()) }
    var episodesLoading by remember { mutableStateOf(false) }
    var seasonsLoading by remember { mutableStateOf(isTv) }
    var isResolving by remember { mutableStateOf(false) }
    var resolvingLabel by remember { mutableStateOf("") }
    var historyMap by remember { mutableStateOf<Map<String, WatchHistoryEntity>>(emptyMap()) }
    var highlightEpisode by remember { mutableIntStateOf(initialEpisode.coerceAtLeast(1)) }

    fun refreshHistory() {
        scope.launch {
            historyMap = repository.getSeriesHistory(item.title).associateBy { it.episodeUrl }
        }
    }

    LaunchedEffect(item.title) {
        refreshHistory()
    }

    LaunchedEffect(item.title, item.year, isTv) {
        if (!isTv) {
            seasons = emptyList()
            seasonsLoading = false
            return@LaunchedEffect
        }
        seasonsLoading = true
        seasons = runCatching { streamResolver.listAvailableSeasons(item.title, item.year) }
            .getOrElse { emptyList() }
        seasonsLoading = false
        if (seasons.isNotEmpty() && selectedSeason !in seasons) {
            selectedSeason = seasons.first()
        }
    }

    LaunchedEffect(item.title, item.year, selectedSeason, isTv) {
        if (!isTv) {
            episodes = emptyList()
            return@LaunchedEffect
        }
        episodesLoading = true
        episodes = runCatching {
            streamResolver.listEpisodes(item.title, item.year, selectedSeason)
        }.getOrElse { emptyList() }
        episodesLoading = false
        if (episodes.isNotEmpty() && episodes.none { it.episode == highlightEpisode }) {
            highlightEpisode = episodes.first().episode
        }
    }

    val playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshHistory()
    }

    fun play(ep: TvEpisodeInfo? = null, seasonOverride: Int? = null) {
        scope.launch {
            isResolving = true
            val season = seasonOverride ?: selectedSeason
            val episode = ep?.episode
            resolvingLabel = if (isTv && episode != null) "S${season}E$episode" else item.title
            try {
                val streams = streamResolver.resolve(
                    imdbId = item.imdbId.ifBlank { "tt0000000" },
                    title = item.title,
                    mediaType = if (isTv) "tv" else "movie",
                    year = item.year,
                    season = if (isTv) season else null,
                    episode = if (isTv) episode else null,
                    episodeUrl = ep?.episodeUrl?.takeIf { it.isNotBlank() },
                )
                val payload = streams.firstOrNull()
                    ?: throw IllegalStateException("No streams returned")
                val resolved = streamResolver.toResolvedStream(payload)
                val epKey = if (isTv && episode != null) {
                    episodeHistoryKey(item.title, season, episode)
                } else {
                    payload.sourceUrl.ifBlank { payload.streamUrl }
                }
                val history = historyMap[epKey]
                val startMs = if (history != null && !history.isFinished && history.positionMs > 5000) {
                    history.positionMs
                } else {
                    0L
                }
                playerLauncher.launch(
                    TvPlayerActivity.createIntent(
                        context = context,
                        resolved = resolved,
                        title = item.title,
                        seriesTitle = item.title,
                        episodeUrl = epKey,
                        episodeTitle = if (isTv && episode != null) "S${season}E$episode" else item.title,
                        seasonNumber = season.toString(),
                        episodeNumber = (episode ?: 1).toString(),
                        posterUrl = item.picture,
                        startPositionMs = startMs,
                    ),
                )
            } catch (err: Exception) {
                android.util.Log.e("MediaPlayDialog", "play failed", err)
                Toast.makeText(
                    context,
                    "Play failed: ${err.message ?: err.javaClass.simpleName}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                isResolving = false
                resolvingLabel = ""
            }
        }
    }

    val resumeCandidate = episodes.firstOrNull { ep ->
        val h = historyMap[episodeHistoryKey(item.title, ep.season, ep.episode)]
        h != null && !h.isFinished && h.positionMs > 5000
    } ?: episodes.firstOrNull { it.episode == highlightEpisode } ?: episodes.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (item.picture.isNotEmpty()) {
                                AsyncImage(
                                    model = item.picture,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(145.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = TextWhite,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.year != null) {
                                        Text("${item.year}", color = TextMuted, fontSize = 12.sp)
                                    }
                                    if (item.type.isNotEmpty()) {
                                        Text(
                                            "• ${item.type}",
                                            color = AccentRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                if (item.imdbRating > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "★ ${"%.1f".format(item.imdbRating)}",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (item.airingStatus.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.airingStatus.uppercase(),
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }

                        if (!isTv) {
                            Spacer(modifier = Modifier.height(14.dp))
                            TvActionButton(
                                text = if (isResolving) "Resolving…" else "▶ Play",
                                onClick = { if (!isResolving) play() },
                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (resumeCandidate != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val key = episodeHistoryKey(item.title, resumeCandidate.season, resumeCandidate.episode)
                            val isResume = historyMap[key]?.let { !it.isFinished && it.positionMs > 5000 } == true
                            val label = if (isResume) {
                                "▶ Resume S${resumeCandidate.season}E${resumeCandidate.episode}"
                            } else {
                                "▶ Play S${resumeCandidate.season}E${resumeCandidate.episode}"
                            }
                            TvActionButton(
                                text = if (isResolving) "Resolving $resolvingLabel…" else label,
                                onClick = { if (!isResolving) play(resumeCandidate) },
                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (item.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "GENRES",
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                item.tags.take(8).forEach { tag ->
                                    TvFilterChip(
                                        text = tag,
                                        selected = false,
                                        onClick = { onSelectTag?.invoke(tag) },
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "SYNOPSIS",
                            color = AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.synopsis.ifBlank { "No synopsis available." },
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        TvActionButton(
                            text = "← Back to Catalog",
                            onClick = onDismiss,
                            isPrimary = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight(),
                    ) {
                        if (!isTv) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isResolving) "Resolving stream…" else "Movie — use Play on the left",
                                    color = TextMuted,
                                    fontSize = 14.sp,
                                )
                            }
                        } else {
                            Text(
                                text = "SEASONS",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            when {
                                seasonsLoading -> Text("Loading seasons…", color = TextMuted, fontSize = 13.sp)
                                seasons.isEmpty() -> Text(
                                    "Not on stream sources yet (catalog is TMDB; play needs Levidia).",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                )
                                else -> {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        items(seasons) { season ->
                                            TvFilterChip(
                                                text = "Season $season",
                                                selected = selectedSeason == season,
                                                onClick = { selectedSeason = season },
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "EPISODES",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            when {
                                episodesLoading || isResolving -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                if (isResolving) "Resolving stream…" else "Loading episodes…",
                                                color = AccentRed,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (resolvingLabel.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(resolvingLabel, color = TextWhite, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                                episodes.isEmpty() -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (seasons.isEmpty()) {
                                                "No episodes/streams available for this title right now."
                                            } else {
                                                "No episodes found for this season."
                                            },
                                            color = TextMuted,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(episodes, key = { "${it.season}-${it.episode}" }) { ep ->
                                            val key = episodeHistoryKey(item.title, ep.season, ep.episode)
                                            val history = historyMap[key]
                                            val progressPercent = if (history != null && history.durationMs > 0) {
                                                (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            }
                                            Card(
                                                onClick = { if (!isResolving) play(ep) },
                                                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                                                colors = CardDefaults.colors(
                                                    containerColor = SurfaceVariantDark,
                                                    focusedContainerColor = SurfaceDark,
                                                ),
                                                border = CardDefaults.border(
                                                    focusedBorder = Border(
                                                        border = BorderStroke(2.dp, AccentRed),
                                                        shape = RoundedCornerShape(8.dp),
                                                    ),
                                                ),
                                                scale = CardDefaults.scale(focusedScale = 1.02f),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            modifier = Modifier.weight(1f),
                                                        ) {
                                                            val epPad = ep.episode.toString().padStart(2, '0')
                                                            Text(
                                                                text = "EP $epPad",
                                                                color = AccentRed,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                            Column {
                                                                Text(
                                                                    text = ep.title.ifBlank { "Episode ${ep.episode}" },
                                                                    color = TextWhite,
                                                                    fontSize = 14.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                                when {
                                                                    history?.isFinished == true -> {
                                                                        Text("Watched", color = TextMuted, fontSize = 11.sp)
                                                                    }
                                                                    history != null && history.positionMs > 5000 -> {
                                                                        Text(
                                                                            "Resume ${formatTime(history.positionMs)}",
                                                                            color = AccentRed,
                                                                            fontSize = 11.sp,
                                                                        )
                                                                    }
                                                                    ep.episode == highlightEpisode -> {
                                                                        Text("Selected", color = TextMuted, fontSize = 11.sp)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        Text("▶ Play", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    if (progressPercent > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(3.dp)
                                                                .background(Color(0xFF2A2A30)),
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth(progressPercent)
                                                                    .height(3.dp)
                                                                    .background(AccentRed),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
