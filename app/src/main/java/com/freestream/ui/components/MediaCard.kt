package com.freestream.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.freestream.data.model.MediaItem
import com.freestream.ui.theme.AccentRed
import com.freestream.ui.theme.SurfaceDark
import com.freestream.ui.theme.SurfaceVariantDark
import com.freestream.ui.theme.TextMuted
import com.freestream.ui.theme.TextWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
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
        scale = CardDefaults.scale(focusedScale = 1.08f),
        modifier = modifier
            .width(160.dp)
            .height(240.dp)
            .padding(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.picture.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.picture)
                        .size(Size(320, 480))
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceVariantDark),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.type.ifEmpty { "TITLE" },
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            val airingBadge = when (item.airingStatus.lowercase()) {
                "airing" -> "AIRING"
                "upcoming" -> "SOON"
                else -> null
            }
            if (airingBadge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (item.airingStatus.equals("airing", ignoreCase = true)) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFF1565C0)
                            },
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = airingBadge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xEE0A0A0C)),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = item.title,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.imdbRating > 0.0) {
                    Text(
                        text = "★ ${"%.1f".format(item.imdbRating)}",
                        color = Color(0xFFFFCC00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
