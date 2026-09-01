package com.freestream.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.freestream.ui.theme.*

enum class TagFilterMode {
    NEUTRAL,
    INCLUDE,
    EXCLUDE;

    fun next(): TagFilterMode = when (this) {
        NEUTRAL -> INCLUDE
        INCLUDE -> EXCLUDE
        EXCLUDE -> NEUTRAL
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    shapeRadius: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) AccentRed else SurfaceVariantDark,
            contentColor = if (selected) Color.White else TextMuted,
            focusedContainerColor = AccentRed,
            focusedContentColor = Color.White,
            pressedContainerColor = AccentRedSoft,
            pressedContentColor = Color.White
        ),
        border = ButtonDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, AccentRed), shape = RoundedCornerShape(shapeRadius)) else Border(BorderStroke(1.dp, BorderDark), shape = RoundedCornerShape(shapeRadius)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(shapeRadius))
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(shapeRadius)),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTriStateFilterChip(
    tag: String,
    mode: TagFilterMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    shapeRadius: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
) {
    val (bgColor, textColor, borderColor, label) = when (mode) {
        TagFilterMode.NEUTRAL -> Quad(SurfaceVariantDark, TextMuted, BorderDark, tag)
        TagFilterMode.INCLUDE -> Quad(Color(0xFF1A3D2E), Color(0xFF9DFFC8), Color(0xFF2D8A5C), "+ $tag")
        TagFilterMode.EXCLUDE -> Quad(Color(0xFF3D1E1A), Color(0xFFFFC4A8), Color(0xFFC45C2A), "- $tag")
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = bgColor,
            contentColor = textColor,
            focusedContainerColor = when (mode) {
                TagFilterMode.INCLUDE -> Color(0xFF23533E)
                TagFilterMode.EXCLUDE -> Color(0xFF5A2520)
                TagFilterMode.NEUTRAL -> AccentRed
            },
            focusedContentColor = Color.White,
            pressedContainerColor = AccentRedSoft,
            pressedContentColor = Color.White
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(shapeRadius)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(shapeRadius))
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(shapeRadius)),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = if (mode != TagFilterMode.NEUTRAL) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    shapeRadius: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (isPrimary) AccentRed else SurfaceVariantDark,
            contentColor = Color.White,
            focusedContainerColor = AccentRed,
            focusedContentColor = Color.White,
            pressedContainerColor = AccentRedSoft,
            pressedContentColor = Color.White
        ),
        border = ButtonDefaults.border(
            border = if (isPrimary) Border(BorderStroke(1.dp, AccentRed), shape = RoundedCornerShape(shapeRadius)) else Border(BorderStroke(1.dp, BorderDark), shape = RoundedCornerShape(shapeRadius)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(shapeRadius))
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(shapeRadius)),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    }
}
