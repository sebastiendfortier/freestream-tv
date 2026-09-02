package com.freestream.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.freestream.data.db.WatchHistoryEntity
import com.freestream.data.model.MediaItem
import com.freestream.data.repository.MediaRepository
import com.freestream.ui.components.MediaCard
import com.freestream.ui.components.FilterDialog
import com.freestream.ui.components.TagFilterMode
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.components.TvFilterChip
import com.freestream.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueWatchingRemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF3D1E1A),
            contentColor = Color(0xFFFFC4A8),
            focusedContainerColor = AccentRed,
            focusedContentColor = Color.White,
            pressedContainerColor = AccentRedSoft,
            pressedContentColor = Color.White
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFFC45C2A)), shape = RoundedCornerShape(6.dp)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp))
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .width(34.dp)
            .fillMaxHeight()
    ) {
        Text(
            text = "×",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueWatchingShelfItem(
    item: WatchHistoryEntity,
    progressPercent: Float,
    isInputBlocked: () -> Boolean,
    onOpenMedia: () -> Unit,
    onOpenRemoveOverlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(256.dp)
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            onClick = {
                if (isInputBlocked()) {
                    return@Card
                }
                onOpenMedia()
            },
            onLongClick = onOpenRemoveOverlay,
            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            colors = CardDefaults.colors(
                containerColor = SurfaceVariantDark,
                focusedContainerColor = SurfaceDark
            ),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, AccentRed), shape = RoundedCornerShape(8.dp))
            ),
            scale = CardDefaults.scale(focusedScale = 1.04f),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.seriesTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(42.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.seriesTitle,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.episodeTitle,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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

        ContinueWatchingRemoveButton(onClick = onOpenRemoveOverlay)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActiveFilterPill(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isInclude: Boolean = false,
    isExclude: Boolean = false
) {
    val (bgColor, textColor, borderColor) = when {
        isInclude -> Triple(Color(0xFF1A3D2E), Color(0xFF9DFFC8), Color(0xFF2D8A5C))
        isExclude -> Triple(Color(0xFF3D1E1A), Color(0xFFFFC4A8), Color(0xFFC45C2A))
        else -> Triple(SurfaceVariantDark, AccentRed, BorderDark)
    }

    Button(
        onClick = onRemove,
        colors = ButtonDefaults.colors(
            containerColor = bgColor,
            contentColor = textColor,
            focusedContainerColor = AccentRed,
            focusedContentColor = Color.White
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(6.dp)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp))
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = modifier
    ) {
        Text(
            text = "$label  ✕",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MediaRepository,
    onSelectMedia: (MediaItem, Int?, Int?) -> Unit,
    externalAddTag: String? = null,
    onClearExternalTag: () -> Unit = {},
    onRemoveOverlayVisible: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showFilterDialog by remember { mutableStateOf(false) }

    // Filter states
    var selectedType by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var minScore by remember { mutableStateOf(0f) }
    var minYear by remember { mutableStateOf(0) }
    var airingStatus by remember { mutableStateOf("ALL") }
    val tagModes = remember { mutableStateMapOf<String, TagFilterMode>() }

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // Real-time reactive Continue Watching shelf (persists through all filtering)
    val continueWatchingList by repository.observeContinueWatching(limit = 15)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var itemToRemoveFromHistory by remember { mutableStateOf<WatchHistoryEntity?>(null) }
    var continueWatchingInputBlockedUntil by remember { mutableStateOf(0L) }

    fun openRemoveOverlay(item: WatchHistoryEntity) {
        continueWatchingInputBlockedUntil = System.currentTimeMillis() + 1500
        onRemoveOverlayVisible(true)
        itemToRemoveFromHistory = item
    }

    fun closeRemoveOverlay() {
        continueWatchingInputBlockedUntil = 0L
        itemToRemoveFromHistory = null
        onRemoveOverlayVisible(false)
    }

    fun isContinueWatchingInputBlocked(): Boolean {
        return itemToRemoveFromHistory != null ||
            System.currentTimeMillis() < continueWatchingInputBlockedUntil
    }

    // Handle tag clicked from play dialog
    LaunchedEffect(externalAddTag) {
        if (!externalAddTag.isNullOrBlank()) {
            tagModes[externalAddTag] = TagFilterMode.INCLUDE
            onClearExternalTag()
        }
    }

    val includeTags = tagModes.filter { it.value == TagFilterMode.INCLUDE }.keys.toList()
    val excludeTags = tagModes.filter { it.value == TagFilterMode.EXCLUDE }.keys.toList()

    val activeFilterCount = (if (selectedType != "ALL") 1 else 0) +
            (if (searchQuery.isNotBlank()) 1 else 0) +
            (if (minScore > 0f) 1 else 0) +
            (if (minYear > 0) 1 else 0) +
            (if (!airingStatus.equals("ALL", ignoreCase = true)) 1 else 0) +
            tagModes.size

    val hasActiveFilters = activeFilterCount > 0

    // Fetch initial page of filtered catalog (default-sorted by scoreMean DESC)
    LaunchedEffect(selectedType, searchQuery, minScore, minYear, airingStatus, includeTags, excludeTags) {
        isLoading = true
        val count = repository.getFilteredCount(
            type = selectedType,
            minYear = minYear,
            minScore = minScore,
            airingStatus = airingStatus,
            includeTags = includeTags,
            excludeTags = excludeTags,
            query = searchQuery
        )
        totalCount = count

        val items = repository.getFilteredMedia(
            type = selectedType,
            minYear = minYear,
            minScore = minScore,
            airingStatus = airingStatus,
            includeTags = includeTags,
            excludeTags = excludeTags,
            query = searchQuery,
            sortBy = "score",
            limit = 60,
            offset = 0
        )
        mediaList = items
        isLoading = false
    }

    fun loadMore() {
        if (isLoadingMore || mediaList.size >= totalCount) return
        isLoadingMore = true
        coroutineScope.launch {
            val nextItems = repository.getFilteredMedia(
                type = selectedType,
                minYear = minYear,
                minScore = minScore,
                airingStatus = airingStatus,
                includeTags = includeTags,
                excludeTags = excludeTags,
                query = searchQuery,
                sortBy = "score",
                limit = 60,
                offset = mediaList.size
            )
            mediaList = mediaList + nextItems
            isLoadingMore = false
        }
    }

    fun resetAllFilters() {
        selectedType = "ALL"
        searchQuery = ""
        minScore = 0f
        minYear = 0
        airingStatus = "ALL"
        tagModes.clear()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 32.dp, vertical = 18.dp)
    ) {
        // TOP SLIM HEADER (Parity with web app header)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filters Button with badge count
                val filterBtnLabel = if (activeFilterCount > 0) "☰ Filters ($activeFilterCount)" else "☰ Filters"
                TvActionButton(
                    text = filterBtnLabel,
                    onClick = { showFilterDialog = true },
                    isPrimary = activeFilterCount > 0,
                    fontSize = 13.sp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                )

                Text(
                    text = "FreeStream",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLoading) {
                    val countStr = NumberFormat.getIntegerInstance().format(totalCount)
                    Text(
                        text = "$countStr titles",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                if (hasActiveFilters) {
                    TvFilterChip(
                        text = "✕ Reset All",
                        selected = false,
                        onClick = { resetAllFilters() },
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ACTIVE FILTER PILLS BAR (Shown when filters are applied, dismissible individually)
        if (hasActiveFilters) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (selectedType != "ALL") {
                    item {
                        ActiveFilterPill(
                            label = "Type: $selectedType",
                            onRemove = { selectedType = "ALL" }
                        )
                    }
                }
                if (searchQuery.isNotBlank()) {
                    item {
                        ActiveFilterPill(
                            label = "\"$searchQuery\"",
                            onRemove = { searchQuery = "" }
                        )
                    }
                }
                if (minScore > 0f) {
                    item {
                        ActiveFilterPill(
                            label = "★ >= $minScore",
                            onRemove = { minScore = 0f }
                        )
                    }
                }
                if (minYear > 0) {
                    item {
                        ActiveFilterPill(
                            label = "Year >= $minYear",
                            onRemove = { minYear = 0 }
                        )
                    }
                }
                if (!airingStatus.equals("ALL", ignoreCase = true)) {
                    item {
                        ActiveFilterPill(
                            label = "Status: ${airingStatus.uppercase()}",
                            onRemove = { airingStatus = "ALL" }
                        )
                    }
                }
                items(includeTags) { tag ->
                    ActiveFilterPill(
                        label = "+ $tag",
                        isInclude = true,
                        onRemove = { tagModes.remove(tag) }
                    )
                }
                items(excludeTags) { tag ->
                    ActiveFilterPill(
                        label = "- $tag",
                        isExclude = true,
                        onRemove = { tagModes.remove(tag) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // PERMANENT CONTINUE WATCHING SHELF (Always stays visible across all filtering)
        if (continueWatchingList.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONTINUE WATCHING",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "× or long-press to remove",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(continueWatchingList, key = { it.episodeUrl }) { item ->
                    val progressPercent = if (item.durationMs > 0) {
                        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    ContinueWatchingShelfItem(
                        item = item,
                        progressPercent = progressPercent,
                        isInputBlocked = ::isContinueWatchingInputBlocked,
                        onOpenMedia = {
                            coroutineScope.launch {
                                val catalogItem = repository.getMediaByTitle(item.seriesTitle)
                                    ?: MediaItem(
                                        title = item.seriesTitle,
                                        type = "TV",
                                        picture = item.posterUrl,
                                    )
                                onSelectMedia(
                                    catalogItem,
                                    item.seasonNumber.toIntOrNull(),
                                    item.episodeNumber.toIntOrNull(),
                                )
                            }
                        },
                        onOpenRemoveOverlay = { openRemoveOverlay(item) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // MAIN CATALOG RESULTS GRID (Default Sorted by Score)
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading catalog…", color = TextMuted, fontSize = 16.sp)
            }
        } else if (mediaList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No titles found matching your filter criteria.", color = TextMuted, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    TvActionButton(
                        text = "Reset All Filters",
                        onClick = { resetAllFilters() },
                        isPrimary = true
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    mediaList,
                    key = { _, item -> item.tmdbId?.toString() ?: item.title }
                ) { index, item ->
                    if (index >= mediaList.size - 18 && mediaList.size < totalCount && !isLoadingMore) {
                        LaunchedEffect(Unit) {
                            loadMore()
                        }
                    }

                    MediaCard(
                        item = item,
                        onClick = { onSelectMedia(item, null, null) }
                    )
                }

                if (isLoadingMore) {
                    item(span = { GridItemSpan(6) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading more titles…", color = AccentRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // FILTER MODAL DRAWER
    if (showFilterDialog) {
        FilterDialog(
            initialType = selectedType,
            initialQuery = searchQuery,
            initialMinScore = minScore,
            initialMinYear = minYear,
            initialAiringStatus = airingStatus,
            initialTagModes = tagModes.toMap(),
            onApply = { type, query, score, year, status, newTagModes ->
                selectedType = type
                searchQuery = query
                minScore = score
                minYear = year
                airingStatus = status
                tagModes.clear()
                tagModes.putAll(newTagModes)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    // REMOVE FROM CONTINUE WATCHING CONFIRMATION OVERLAY (in-tree; avoids TV Dialog dismiss on key-up)
    itemToRemoveFromHistory?.let { item ->
        val cancelFocusRequester = remember(item.episodeUrl) { FocusRequester() }
        LaunchedEffect(item.episodeUrl) {
            cancelFocusRequester.requestFocus()
        }

        BackHandler {
            closeRemoveOverlay()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Remove from Continue Watching?",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Remove \"${item.seriesTitle} - ${item.episodeTitle}\" from your continue watching history?",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvActionButton(
                            text = "Cancel",
                            onClick = { closeRemoveOverlay() },
                            isPrimary = false,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .focusRequester(cancelFocusRequester)
                        )
                        TvActionButton(
                            text = "Remove",
                            onClick = {
                                coroutineScope.launch {
                                    repository.deleteWatchProgress(item.episodeUrl)
                                    Toast.makeText(context, "Removed from Continue Watching", Toast.LENGTH_SHORT).show()
                                    closeRemoveOverlay()
                                }
                            },
                            isPrimary = true
                        )
                    }
                }
            }
        }
    }
    }
}
