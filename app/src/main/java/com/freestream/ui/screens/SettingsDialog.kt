package com.freestream.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.freestream.data.repository.MediaRepository
import com.freestream.ui.components.TvActionButton
import com.freestream.ui.theme.SurfaceDark

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsDialog(
    repository: MediaRepository,
    defaultApiUrl: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
) {
    var apiUrl by remember { mutableStateOf(repository.getApiBaseUrl(defaultApiUrl)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.5f),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(containerColor = SurfaceDark),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Settings", fontSize = 22.sp, color = Color.White)
                Text(
                    "Optional: remote resolver URL (leave blank for on-device playback)",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                )
                BasicTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        text = "Save",
                        onClick = {
                            repository.setApiBaseUrl(apiUrl)
                            onSaved()
                            onDismiss()
                        },
                        isPrimary = true,
                    )
                    TvActionButton(text = "Cancel", onClick = onDismiss)
                }
            }
        }
    }
}
