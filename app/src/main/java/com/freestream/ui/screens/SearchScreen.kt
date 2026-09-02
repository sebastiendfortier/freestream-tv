package com.freestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.freestream.data.model.MediaItem
import com.freestream.data.repository.MediaRepository
import com.freestream.ui.components.MediaCard
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.components.TvFilterChip
import com.freestream.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: MediaRepository,
    initialQuery: String = "",
    onBack: () -> Unit,
    onSelectMedia: (MediaItem, Int?, Int?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var rawInputText by remember { mutableStateOf(initialQuery) }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    fun executeSearch(query: String) {
        val q = query.trim()
        coroutineScope.launch {
            isSearching = true
            searchResults = if (q.isBlank()) {
                repository.getFilteredMedia(limit = 60)
            } else {
                repository.search(q)
            }
            isSearching = false
        }
    }

    // Debounce search query changes to allow natural typing with spaces
    LaunchedEffect(rawInputText) {
        val trimmed = rawInputText.trim()
        if (trimmed.isEmpty()) {
            isSearching = true
            searchResults = repository.getFilteredMedia(limit = 60)
            isSearching = false
        } else {
            delay(350) // 350ms debounce
            isSearching = true
            searchResults = repository.search(trimmed)
            isSearching = false
        }
    }

    val genrePills = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Sci-Fi", "Romance", "Shounen", "Isekai", "Slice of Life",
        "Mystery", "Supernatural", "Sports", "Mecha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        // TOP TOOLBAR: Back Button, Search Input Box, Search Action & Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvActionButton(
                text = "← Back",
                onClick = onBack,
                isPrimary = false,
                fontSize = 13.sp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            )

            // Google TV Focusable Text Input Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantDark)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (rawInputText.isEmpty()) {
                    Text("Search movies, TV, genre, or keyword…", color = TextMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = rawInputText,
                    onValueChange = {
                        // Keep raw input text exactly as typed (preserving spaces)
                        rawInputText = it
                    },
                    textStyle = TextStyle(color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(AccentRed),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { executeSearch(rawInputText) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TvActionButton(
                text = "🔍 Search",
                onClick = { executeSearch(rawInputText) },
                isPrimary = true,
                fontSize = 13.sp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            )

            if (rawInputText.isNotEmpty()) {
                TvActionButton(
                    text = "Clear",
                    onClick = {
                        rawInputText = ""
                    },
                    isPrimary = false,
                    fontSize = 12.sp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Category / Genre Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(genrePills) { genre ->
                val isSelected = rawInputText.trim().equals(genre, ignoreCase = true)
                TvFilterChip(
                    text = genre,
                    selected = isSelected,
                    onClick = {
                        rawInputText = genre
                    },
                    fontSize = 11.sp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rawInputText.isBlank()) "Popular Suggestions" else "Results for \"${rawInputText.trim()}\"",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (searchResults.isNotEmpty()) {
                Text(
                    text = "${searchResults.size} titles",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Searching catalog & WCO…", color = AccentRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else if (searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching titles found for '${rawInputText.trim()}'. Try another title or genre.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { item ->
                    MediaCard(
                        item = item,
                        onClick = { onSelectMedia(item, null, null) }
                    )
                }
            }
        }
    }
}
