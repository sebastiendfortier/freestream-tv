package com.freestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.freestream.data.model.MediaItem
import com.freestream.data.repository.MediaRepository
import com.freestream.resolver.StreamResolver
import com.freestream.ui.screens.HomeScreen
import com.freestream.ui.screens.MediaPlayDialog
import com.freestream.ui.theme.FreeStreamTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: MediaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MediaRepository(applicationContext)
        val streamResolver = StreamResolver()

        setContent {
            FreeStreamTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
                    var selectedSeason by remember { mutableIntStateOf(1) }
                    var selectedEpisode by remember { mutableIntStateOf(1) }
                    var pendingTagFilter by remember { mutableStateOf<String?>(null) }
                    var removeOverlayVisible by remember { mutableStateOf(false) }

                    BackHandler(enabled = selectedItem != null && !removeOverlayVisible) {
                        selectedItem = null
                    }

                    HomeScreen(
                        repository = repository,
                        onSelectMedia = { item, season, episode ->
                            if (!removeOverlayVisible) {
                                selectedItem = item
                                selectedSeason = season ?: 1
                                selectedEpisode = episode ?: 1
                            }
                        },
                        externalAddTag = pendingTagFilter,
                        onClearExternalTag = { pendingTagFilter = null },
                        onRemoveOverlayVisible = { removeOverlayVisible = it },
                    )

                    if (!removeOverlayVisible) {
                        selectedItem?.let { item ->
                            MediaPlayDialog(
                                item = item,
                                streamResolver = streamResolver,
                                repository = repository,
                                onDismiss = { selectedItem = null },
                                onSelectTag = { tag ->
                                    selectedItem = null
                                    pendingTagFilter = tag
                                },
                                initialSeason = selectedSeason,
                                initialEpisode = selectedEpisode,
                            )
                        }
                    }
                }
            }
        }
    }
}
