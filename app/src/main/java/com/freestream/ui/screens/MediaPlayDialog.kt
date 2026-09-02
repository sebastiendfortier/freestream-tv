package com.freestream.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.freestream.data.model.MediaItem
import com.freestream.player.TvPlayerActivity
import com.freestream.resolver.StreamResolver
import com.freestream.resolver.TvEpisodeInfo
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.theme.SurfaceDark
import com.freestream.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaPlayDialog(
    item: MediaItem,
    streamResolver: StreamResolver,
    onDismiss: () -> Unit,
    onSelectTag: ((String) -> Unit)? = null,
    initialSeason: Int = 1,
    initialEpisode: Int = 1,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var season by remember { mutableIntStateOf(initialSeason.coerceAtLeast(1)) }
    var episode by remember { mutableIntStateOf(initialEpisode.coerceAtLeast(1)) }
    var episodes by remember { mutableStateOf<List<TvEpisodeInfo>>(emptyList()) }
    var episodesLoading by remember { mutableStateOf(false) }
    val isTv = item.type.equals("TV", ignoreCase = true)

    LaunchedEffect(item.title, item.year, season, isTv) {
        if (!isTv) {
            episodes = emptyList()
            return@LaunchedEffect
        }
        episodesLoading = true
        episodes = runCatching {
            streamResolver.listEpisodes(item.title, item.year, season)
        }.getOrElse {
            emptyList()
        }
        episodesLoading = false
        if (episodes.isNotEmpty() && episodes.none { it.episode == episode }) {
            episode = episodes.first().episode
        }
    }

    val playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    fun play(selectedSeason: Int = season, selectedEpisode: Int = episode) {
        scope.launch {
            isResolving = true
            try {
                val streams = streamResolver.resolve(
                    imdbId = item.imdbId.ifBlank { "tt0000000" },
                    title = item.title,
                    mediaType = if (isTv) "tv" else "movie",
                    year = item.year,
                    season = if (isTv) selectedSeason else null,
                    episode = if (isTv) selectedEpisode else null,
                )
                val payload = streams.firstOrNull()
                    ?: throw IllegalStateException("No streams returned")
                val resolved = streamResolver.toResolvedStream(payload)
                val epKey = payload.sourceUrl.ifBlank { payload.streamUrl }
                playerLauncher.launch(
                    TvPlayerActivity.createIntent(
                        context = context,
                        resolved = resolved,
                        title = item.title,
                        seriesTitle = item.title,
                        episodeUrl = epKey,
                        episodeTitle = if (isTv) "S${selectedSeason}E${selectedEpisode}" else item.title,
                        seasonNumber = selectedSeason.toString(),
                        episodeNumber = selectedEpisode.toString(),
                        posterUrl = item.picture,
                    ),
                )
            } catch (err: Exception) {
                Toast.makeText(context, "Play failed: ${err.message}", Toast.LENGTH_LONG).show()
            } finally {
                isResolving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(containerColor = SurfaceDark),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AsyncImage(
                        model = item.picture,
                        contentDescription = item.title,
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "${item.type} · ${item.year ?: "?"} · ★ ${"%.1f".format(item.imdbRating)}",
                            color = TextMuted,
                        )
                        if (item.tags.isNotEmpty()) {
                            Text("Genres: ${item.tags.take(5).joinToString(", ")}", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }
                if (item.synopsis.isNotBlank()) {
                    Text(item.synopsis, color = Color(0xFFCCCCCC), fontSize = 15.sp)
                }
                if (isTv) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Season $season", color = Color.White)
                        TvActionButton("S+", onClick = { season = (season + 1).coerceAtMost(30) })
                        TvActionButton("S-", onClick = { season = (season - 1).coerceAtLeast(1) })
                    }
                    when {
                        episodesLoading -> Text("Loading episodes…", color = TextMuted)
                        episodes.isEmpty() -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Episode $episode", color = Color.White)
                                TvActionButton("E+", onClick = { episode = (episode + 1).coerceAtMost(50) })
                                TvActionButton("E-", onClick = { episode = (episode - 1).coerceAtLeast(1) })
                            }
                        }
                        else -> {
                            Text("Episodes (${episodes.size})", color = Color.White, fontWeight = FontWeight.SemiBold)
                            episodes.chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { ep ->
                                        TvActionButton(
                                            text = "E${ep.episode}",
                                            onClick = { play(season, ep.episode) },
                                            isPrimary = ep.episode == episode,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        if (isResolving) "Resolving…" else if (isTv && episodes.isNotEmpty()) "Play E$episode" else "Play",
                        onClick = { if (!isResolving) play() },
                    )
                    TvActionButton("Close", onClick = onDismiss)
                }
                item.tags.take(6).forEach { tag ->
                    TvActionButton(tag, onClick = { onSelectTag?.invoke(tag) })
                }
            }
        }
    }
}
