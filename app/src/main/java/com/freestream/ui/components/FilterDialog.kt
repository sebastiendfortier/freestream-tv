package com.freestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.freestream.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    initialType: String = "ALL",
    initialQuery: String = "",
    initialMinScore: Float = 0f,
    initialMinYear: Int = 0,
    initialAiringStatus: String = "ALL",
    initialTagModes: Map<String, TagFilterMode> = emptyMap(),
    onApply: (type: String, query: String, minScore: Float, minYear: Int, airingStatus: String, tagModes: Map<String, TagFilterMode>) -> Unit,
    onDismiss: () -> Unit
) {
    var typeState by remember { mutableStateOf(initialType) }
    var queryState by remember { mutableStateOf(initialQuery) }
    var scoreInputState by remember { mutableStateOf(if (initialMinScore > 0f) "$initialMinScore" else "") }
    var yearInputState by remember { mutableStateOf(if (initialMinYear > 0) "$initialMinYear" else "") }
    var airingStatusState by remember { mutableStateOf(initialAiringStatus) }
    val tagModes = remember { mutableStateMapOf<String, TagFilterMode>().apply { putAll(initialTagModes) } }

    val popularTags = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Sci-Fi", "Romance", "Shounen", "Isekai", "Slice of Life",
        "Mystery", "Supernatural", "Sports", "Mecha", "Horror",
        "Psychological", "Thriller", "Magic", "Music", "Historical"
    )

    fun parseScore(): Float {
        val s = scoreInputState.trim().toFloatOrNull() ?: 0f
        return s.coerceIn(0f, 10f)
    }

    fun parseYear(): Int {
        val y = yearInputState.trim().toIntOrNull() ?: 0
        return if (y in 1900..2100) y else 0
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
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(vertical = 24.dp, horizontal = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(760.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(24.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FILTERS & SEARCH",
                            color = AccentRed,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvActionButton(
                                text = "✕ Reset All",
                                onClick = {
                                    typeState = "ALL"
                                    queryState = ""
                                    scoreInputState = ""
                                    yearInputState = ""
                                    airingStatusState = "ALL"
                                    tagModes.clear()
                                },
                                isPrimary = false,
                                fontSize = 12.sp,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            )
                            TvActionButton(
                                text = "✓ Apply Filters",
                                onClick = {
                                    onApply(
                                        typeState,
                                        queryState.trim(),
                                        parseScore(),
                                        parseYear(),
                                        airingStatusState,
                                        tagModes.toMap()
                                    )
                                },
                                isPrimary = true,
                                fontSize = 12.sp,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. SEARCH INPUT FIELD
                    Text(
                        text = "SEARCH KEYWORDS (TITLE, STUDIO, SYNOPSIS)",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (queryState.isEmpty()) {
                                Text("Enter search query (e.g. Naruto, Ghibli, Isekai)…", color = TextMuted, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = queryState,
                                onValueChange = { queryState = it },
                                textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(AccentRed),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (queryState.isNotEmpty()) {
                            TvFilterChip(
                                text = "Clear",
                                selected = false,
                                onClick = { queryState = "" },
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. FORMAT / TYPE
                    Text(
                        text = "FORMAT / TYPE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("ALL", "TV", "MOVIE", "OVA").forEach { type ->
                            TvFilterChip(
                                text = type,
                                selected = typeState == type,
                                onClick = { typeState = type },
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2b. AIRING STATUS
                    Text(
                        text = "AIRING STATUS",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("ALL", "AIRING", "AIRED", "UPCOMING").forEach { status ->
                            TvFilterChip(
                                text = status,
                                selected = airingStatusState.equals(status, ignoreCase = true),
                                onClick = { airingStatusState = status },
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. MIN SCORE & MIN YEAR (DIRECT INPUT + QUICK PRESETS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // MIN SCORE COLUMN
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MIN SCORE (0.0 – 10.0)",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (scoreInputState.isEmpty()) {
                                    Text("0.0 (Any score)", color = TextMuted, fontSize = 13.sp)
                                }
                                BasicTextField(
                                    value = scoreInputState,
                                    onValueChange = { scoreInputState = it },
                                    textStyle = TextStyle(color = Color(0xFFFFD700), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    cursorBrush = SolidColor(AccentRed),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Quick score preset chips starting at 0
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("" to "0 (Any)", "7.0" to "7+", "7.5" to "7.5+", "8.0" to "8+", "8.5" to "8.5+").forEach { (valStr, label) ->
                                    val isSel = scoreInputState.trim() == valStr
                                    TvFilterChip(
                                        text = label,
                                        selected = isSel,
                                        onClick = { scoreInputState = valStr },
                                        fontSize = 10.sp,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // MIN YEAR COLUMN
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MIN YEAR (DIRECT INPUT OR PRESET)",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (yearInputState.isEmpty()) {
                                    Text("0 (All years)", color = TextMuted, fontSize = 13.sp)
                                }
                                BasicTextField(
                                    value = yearInputState,
                                    onValueChange = { yearInputState = it },
                                    textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    cursorBrush = SolidColor(AccentRed),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Quick year preset chips starting at 0
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("" to "All", "2000" to "2000+", "2015" to "2015+", "2020" to "2020+", "2024" to "2024+").forEach { (valStr, label) ->
                                    val isSel = yearInputState.trim() == valStr
                                    TvFilterChip(
                                        text = label,
                                        selected = isSel,
                                        onClick = { yearInputState = valStr },
                                        fontSize = 10.sp,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. TRI-STATE GENRES / TAGS (+Include, -Exclude, Neutral)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GENRES & TAGS (+INCLUDE / -EXCLUDE)",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Click to cycle: Neutral → +Include → -Exclude",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        popularTags.forEach { tag ->
                            val currentMode = tagModes[tag] ?: TagFilterMode.NEUTRAL
                            TvTriStateFilterChip(
                                tag = tag,
                                mode = currentMode,
                                onClick = {
                                    val next = currentMode.next()
                                    if (next == TagFilterMode.NEUTRAL) {
                                        tagModes.remove(tag)
                                    } else {
                                        tagModes[tag] = next
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // BOTTOM ACTION BAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvActionButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            isPrimary = false,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        TvActionButton(
                            text = "Apply Filters",
                            onClick = {
                                onApply(
                                    typeState,
                                    queryState.trim(),
                                    parseScore(),
                                    parseYear(),
                                    airingStatusState,
                                    tagModes.toMap()
                                )
                            },
                            isPrimary = true
                        )
                    }
                }
            }
        }
    }
}
