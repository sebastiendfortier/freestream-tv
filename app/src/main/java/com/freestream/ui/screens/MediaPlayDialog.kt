package com.freestream.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.freestream.data.model.AnimeItem
import com.freestream.data.model.ResolvedStream
import com.freestream.player.TvPlayerActivity
import com.freestream.resolver.StreamResolver
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.theme.SurfaceDark
import com.freestream.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaPlayDialog(
    item: AnimeItem,
    streamResolver: StreamResolver,
    onDismiss: () -> Unit,
    onSelectTag: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf(1) }
    var episode by remember { mutableStateOf(1) }
    val isTv = item.type.equals("TV", ignoreCase = true)

    val playerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun play() {
        scope.launch {
            isResolving = true
            try {
                val imdb = item.imdbId.ifBlank { "tt0000000" }
                val streams = streamResolver.resolve(
                    imdbId = imdb,
                    title = item.title,
                    mediaType = if (isTv) "tv" else "movie",
                    year = item.year,
                    season = if (isTv) season else null,
                    episode = if (isTv) episode else null,
                )
                val stream = streams.firstOrNull()
                    ?: throw IllegalStateException("No streams returned")
                val resolved = ResolvedStream(
                    streamUrl = stream.streamUrl,
                    quality = stream.quality,
                    headers = stream.headers,
                    sourceUrl = stream.sourceUrl.ifBlank { stream.streamUrl },
                    contentType = stream.contentType,
                )
                playerLauncher.launch(
                    TvPlayerActivity.createIntent(
                        context = context,
                        resolved = resolved,
                        title = item.title,
                        seriesTitle = item.title,
                        posterUrl = item.picture,
                    )
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
                            "${item.type} · ${item.year ?: "?"} · ★ ${"%.1f".format(item.scoreMean)}",
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Season $season  Episode $episode", color = Color.White)
                        TvActionButton("S+", onClick = { season = (season + 1).coerceAtMost(30) })
                        TvActionButton("S-", onClick = { season = (season - 1).coerceAtLeast(1) })
                        TvActionButton("E+", onClick = { episode = (episode + 1).coerceAtMost(50) })
                        TvActionButton("E-", onClick = { episode = (episode - 1).coerceAtLeast(1) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        if (isResolving) "Resolving…" else "Play",
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
