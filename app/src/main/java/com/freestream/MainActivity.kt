package com.freestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.freestream.data.model.AnimeItem
import com.freestream.data.repository.AnimeRepository
import com.freestream.ui.screens.EpisodeDialog
import com.freestream.ui.screens.HomeScreen
import com.freestream.ui.theme.AnimeTVTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: AnimeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AnimeRepository(applicationContext)

        setContent {
            AnimeTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
                    var pendingTagFilter by remember { mutableStateOf<String?>(null) }
                    var removeOverlayVisible by remember { mutableStateOf(false) }

                    BackHandler(enabled = selectedAnime != null && !removeOverlayVisible) {
                        selectedAnime = null
                    }

                    HomeScreen(
                        repository = repository,
                        onSelectAnime = { anime ->
                            if (!removeOverlayVisible) {
                                selectedAnime = anime
                            }
                        },
                        externalAddTag = pendingTagFilter,
                        onClearExternalTag = { pendingTagFilter = null },
                        onRemoveOverlayVisible = { removeOverlayVisible = it }
                    )

                    if (!removeOverlayVisible) {
                        selectedAnime?.let { anime ->
                            EpisodeDialog(
                                anime = anime,
                                repository = repository,
                                onDismiss = { selectedAnime = null },
                                onSelectTag = { tag ->
                                    selectedAnime = null
                                    pendingTagFilter = tag
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
