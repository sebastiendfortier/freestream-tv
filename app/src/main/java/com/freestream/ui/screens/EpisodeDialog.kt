package com.freestream.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
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
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.freestream.data.db.WatchHistoryEntity
import com.freestream.data.model.AnimeItem
import com.freestream.data.model.EpisodeItem
import com.freestream.data.model.PlaybackEpisode
import com.freestream.data.model.PlaybackSession
import com.freestream.data.model.SeriesDetails
import com.freestream.data.model.SeriesMatch
import com.freestream.data.repository.AnimeRepository
import com.freestream.player.TvPlayerActivity
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.components.TvFilterChip
import com.freestream.ui.theme.*
import kotlinx.coroutines.launch

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
fun EpisodeDialog(
    anime: AnimeItem,
    repository: AnimeRepository,
    onDismiss: () -> Unit,
    onSelectTag: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var seriesMatches by remember { mutableStateOf<List<SeriesMatch>>(emptyList()) }
    var selectedSeriesMatch by remember { mutableStateOf<SeriesMatch?>(null) }
    var seriesDetails by remember { mutableStateOf<SeriesDetails?>(null) }
    var historyMap by remember { mutableStateOf<Map<String, WatchHistoryEntity>>(emptyMap()) }

    var isLoadingEpisodes by remember { mutableStateOf(true) }
    var isResolvingStream by remember { mutableStateOf(false) }
    var resolvingEpisodeTitle by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Subbed") } // "Subbed" or "Dubbed"
    var selectedSeasonFilter by remember { mutableStateOf("ALL") } // "ALL" or "1", "2", "Final", etc.
    var preferredQuality by remember { mutableStateOf(repository.getPreferredQuality()) }

    fun refreshHistory() {
        coroutineScope.launch {
            val history = repository.getSeriesHistory(anime.title)
            historyMap = history.associateBy { it.episodeUrl }
        }
    }

    val playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        refreshHistory()
        if (result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra(TvPlayerActivity.EXTRA_SERIES_COMPLETE, false) == true
        ) {
            onDismiss()
        }
    }

    LaunchedEffect(anime) {
        isLoadingEpisodes = true
        refreshHistory()
        try {
            val matches = repository.searchStreamSeries(anime)
            seriesMatches = matches
            val initialMatch = matches.firstOrNull()
            if (initialMatch != null) {
                selectedSeriesMatch = initialMatch
                val details = repository.listEpisodesForMatch(initialMatch)
                seriesDetails = details
                if (details.subbedEpisodes.isEmpty() && details.dubbedEpisodes.isNotEmpty()) {
                    selectedTab = "Dubbed"
                } else {
                    selectedTab = "Subbed"
                }
            }
        } catch (_: Exception) {
        } finally {
            isLoadingEpisodes = false
        }
    }

    fun switchSeriesMatch(match: SeriesMatch) {
        if (selectedSeriesMatch?.url == match.url) return
        selectedSeriesMatch = match
        isLoadingEpisodes = true
        selectedSeasonFilter = "ALL"
        coroutineScope.launch {
            try {
                val details = repository.listEpisodesForMatch(match)
                seriesDetails = details
                if (details.subbedEpisodes.isEmpty() && details.dubbedEpisodes.isNotEmpty()) {
                    selectedTab = "Dubbed"
                } else if (details.dubbedEpisodes.isEmpty() && details.subbedEpisodes.isNotEmpty()) {
                    selectedTab = "Subbed"
                }
            } catch (_: Exception) {
            } finally {
                isLoadingEpisodes = false
            }
        }
    }

    fun playEpisode(ep: EpisodeItem, playlist: List<EpisodeItem>) {
        if (isResolvingStream) return
        isResolvingStream = true
        resolvingEpisodeTitle = ep.cleanLabel

        coroutineScope.launch {
            try {
                val match = selectedSeriesMatch
                val resolved = if (match?.source == "otaku") {
                    repository.otakuResolver.resolveEpisodeStreams(ep.url, preferredQuality = preferredQuality)
                } else {
                    repository.wcoResolver.resolveEpisodeStreams(ep.url, preferredQuality = preferredQuality)
                }
                val existingHistory = historyMap[ep.url]
                val startPos = if (existingHistory != null && !existingHistory.isFinished && existingHistory.positionMs > 5000) {
                    existingHistory.positionMs
                } else {
                    0L
                }

                val startIndex = playlist.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                val session = PlaybackSession(
                    episodes = playlist.map { item ->
                        PlaybackEpisode(
                            url = item.url,
                            cleanLabel = item.cleanLabel,
                            seasonNumber = item.seasonNumber.orEmpty(),
                            episodeNumber = item.episodeNumber.orEmpty(),
                        )
                    },
                    startIndex = startIndex,
                    source = match?.source ?: "wco",
                    seriesTitle = anime.title,
                    posterUrl = anime.picture,
                    preferredQuality = preferredQuality,
                    malId = anime.malId,
                )

                playerLauncher.launch(
                    TvPlayerActivity.createIntent(
                        context = context,
                        resolved = resolved,
                        title = "${anime.title} - ${ep.cleanLabel}",
                        startPositionMs = startPos,
                        episodeUrl = ep.url,
                        seriesTitle = anime.title,
                        episodeTitle = ep.cleanLabel,
                        seasonNumber = ep.seasonNumber ?: "",
                        episodeNumber = ep.episodeNumber ?: "",
                        posterUrl = anime.picture,
                        session = session
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isResolvingStream = false
                resolvingEpisodeTitle = ""
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // LEFT COLUMN: Poster, Title, Meta, Quality Selector, Tags, Synopsis
                    val leftScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .verticalScroll(leftScrollState)
                    ) {
                        // Poster & Metadata Header
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (anime.picture.isNotEmpty()) {
                                AsyncImage(
                                    model = anime.picture,
                                    contentDescription = anime.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(145.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anime.title,
                                    color = TextWhite,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (anime.year != null) {
                                        Text(text = "${anime.year}", color = TextMuted, fontSize = 12.sp)
                                    }
                                    if (anime.type.isNotEmpty()) {
                                        Text(text = "• ${anime.type}", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (anime.scoreMean > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "★ ${String.format("%.2f", anime.scoreMean)}",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Hero Resume / Play Button
                        val activeEpisodesList = if (selectedTab == "Subbed") {
                            seriesDetails?.subbedEpisodes.orEmpty().ifEmpty { seriesDetails?.episodes.orEmpty() }
                        } else {
                            seriesDetails?.dubbedEpisodes.orEmpty()
                        }
                        val resumeCandidate = activeEpisodesList.firstOrNull {
                            val h = historyMap[it.url]
                            h != null && !h.isFinished && h.positionMs > 5000
                        } ?: activeEpisodesList.firstOrNull()

                        if (resumeCandidate != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val isResume = historyMap[resumeCandidate.url]?.let { !it.isFinished && it.positionMs > 5000 } ?: false
                            val btnLabel = if (isResume) "▶ Resume ${resumeCandidate.cleanLabel}" else "▶ Play ${resumeCandidate.cleanLabel}"
                            TvActionButton(
                                text = btnLabel,
                                onClick = { playEpisode(resumeCandidate, activeEpisodesList) },
                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Quality Preference Selector
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "DEFAULT STREAM QUALITY",
                            color = AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val qualities = listOf("Auto", "1080p", "720p", "480p")
                            qualities.forEach { q ->
                                val isSelected = preferredQuality.equals(q, ignoreCase = true)
                                TvFilterChip(
                                    text = q,
                                    selected = isSelected,
                                    onClick = {
                                        preferredQuality = q
                                        repository.setPreferredQuality(q)
                                    },
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Selectable Tags
                        if (anime.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "GENRES / TAGS",
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                anime.tags.take(8).forEach { tag ->
                                    TvFilterChip(
                                        text = tag,
                                        selected = false,
                                        onClick = { onSelectTag?.invoke(tag) },
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Full Synopsis
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "SYNOPSIS",
                            color = AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val displaySynopsis = anime.synopsis.ifEmpty { seriesDetails?.plot ?: "No synopsis available." }
                        Text(
                            text = displaySynopsis,
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        TvActionButton(
                            text = "← Back to Catalog",
                            onClick = onDismiss,
                            isPrimary = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // RIGHT COLUMN: Multi-Series Tabs, Season Tabs, Audio Tabs & Episode List
                    Column(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight()
                    ) {
                        // Multi-Series matches switcher (e.g. Frieren Season 1, Season 2, Movie)
                        if (seriesMatches.size > 1) {
                            Text(
                                text = "SERIES / SEASONS AVAILABLE",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(seriesMatches, key = { it.url }) { match ->
                                    val isMatchSelected = selectedSeriesMatch?.url == match.url
                                    TvFilterChip(
                                        text = match.title.replace(" English Subbed", "").replace(" English Dubbed", ""),
                                        selected = isMatchSelected,
                                        onClick = { switchSeriesMatch(match) },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Toolbar: Header, Sub/Dub Switcher & Season Chips
                        val subCount = seriesDetails?.subbedEpisodes?.size ?: 0
                        val dubCount = seriesDetails?.dubbedEpisodes?.size ?: 0
                        val rawActiveEpisodes = if (selectedTab == "Subbed") {
                            seriesDetails?.subbedEpisodes.orEmpty().ifEmpty { seriesDetails?.episodes.orEmpty() }
                        } else {
                            seriesDetails?.dubbedEpisodes.orEmpty()
                        }

                        // Extract distinct seasons present in active episodes
                        val distinctSeasons = rawActiveEpisodes
                            .mapNotNull { it.seasonNumber }
                            .distinct()
                            .sorted()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes (${rawActiveEpisodes.size})",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Sub / Dub Tabs
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (subCount > 0 || (subCount == 0 && dubCount == 0)) {
                                    TvFilterChip(
                                        text = "Subbed ($subCount)",
                                        selected = selectedTab == "Subbed",
                                        onClick = {
                                            selectedTab = "Subbed"
                                            selectedSeasonFilter = "ALL"
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                                if (dubCount > 0) {
                                    TvFilterChip(
                                        text = "Dubbed ($dubCount)",
                                        selected = selectedTab == "Dubbed",
                                        onClick = {
                                            selectedTab = "Dubbed"
                                            selectedSeasonFilter = "ALL"
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Season Filter Tabs (when multiple seasons are detected in the same page)
                        if (distinctSeasons.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    TvFilterChip(
                                        text = "All Seasons",
                                        selected = selectedSeasonFilter == "ALL",
                                        onClick = { selectedSeasonFilter = "ALL" },
                                        fontSize = 11.sp
                                    )
                                }
                                items(distinctSeasons) { season ->
                                    val isSelected = selectedSeasonFilter == season
                                    val count = rawActiveEpisodes.count { it.seasonNumber == season }
                                    val sLabel = if (season.equals("Final", ignoreCase = true)) "Final Season" else "Season $season"
                                    TvFilterChip(
                                        text = "$sLabel ($count)",
                                        selected = isSelected,
                                        onClick = { selectedSeasonFilter = season },
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingEpisodes) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Loading episodes from WCO…", color = TextMuted, fontSize = 14.sp)
                            }
                        } else if (isResolvingStream) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Resolving stream…", color = AccentRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    if (resolvingEpisodeTitle.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(resolvingEpisodeTitle, color = TextWhite, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            val activeEpisodes = if (selectedSeasonFilter == "ALL") {
                                rawActiveEpisodes
                            } else {
                                rawActiveEpisodes.filter { it.seasonNumber == selectedSeasonFilter }
                            }

                            if (activeEpisodes.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No episodes found for this selection.", color = TextMuted, fontSize = 14.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(activeEpisodes, key = { it.url }) { ep ->
                                        val history = historyMap[ep.url]
                                        val progressPercent = if (history != null && history.durationMs > 0) {
                                            (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }

                                        Card(
                                            onClick = { playEpisode(ep, activeEpisodes) },
                                            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                                            colors = CardDefaults.colors(
                                                containerColor = SurfaceVariantDark,
                                                focusedContainerColor = SurfaceDark
                                            ),
                                            border = CardDefaults.border(
                                                focusedBorder = Border(
                                                    border = BorderStroke(2.dp, AccentRed),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            ),
                                            scale = CardDefaults.scale(focusedScale = 1.02f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Left: Episode Badge & Title
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        val epBadge = if (!ep.episodeNumber.isNullOrEmpty()) {
                                                            val epPad = if (ep.episodeNumber.length == 1) "0${ep.episodeNumber}" else ep.episodeNumber
                                                            "EP $epPad"
                                                        } else {
                                                            "SPECIAL"
                                                        }

                                                        val displayTitle = if (ep.episodeTitle.isNotEmpty() && ep.episodeTitle != ep.title) {
                                                            if (ep.seasonNumber != null && ep.seasonNumber != "1") {
                                                                "S${ep.seasonNumber} • ${ep.episodeTitle}"
                                                            } else {
                                                                ep.episodeTitle
                                                            }
                                                        } else if (ep.seasonNumber != null && ep.seasonNumber != "1") {
                                                            "Season ${ep.seasonNumber}"
                                                        } else if (ep.episodeNumber == null) {
                                                            ep.cleanLabel
                                                        } else {
                                                            "Full Episode"
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(AccentRed)
                                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                                        ) {
                                                            Text(
                                                                text = epBadge,
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Column {
                                                            Text(
                                                                text = displayTitle,
                                                                color = TextWhite,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )

                                                            if (history != null) {
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                if (history.isFinished) {
                                                                    Text("✓ Watched", color = Color(0xFF4CAF50), fontSize = 11.sp)
                                                                } else if (history.positionMs > 5000) {
                                                                    Text(
                                                                        text = "Resume at ${formatTime(history.positionMs)} / ${formatTime(history.durationMs)}",
                                                                        color = AccentRed,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Right: Play Action Indicator
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "▶ Play",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                // Progress bar
                                                if (progressPercent > 0f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(3.dp)
                                                            .background(BorderDark)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .fillMaxWidth(progressPercent)
                                                                .background(AccentRed)
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
