package com.freestream.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.freestream.data.model.ResolvedStream

object VlcIntentLauncher {

    fun launchVlc(context: Context, resolved: ResolvedStream, title: String) {
        val uri = Uri.parse(resolved.streamUrl)
        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("org.videolan.vlc")
            setDataAndType(uri, "video/*")
            putExtra("title", title)
            putExtra("headers", arrayOf("Referer: ${resolved.headers["Referer"]}", "User-Agent: ${resolved.headers["User-Agent"]}"))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(vlcIntent)
        } catch (_: Exception) {
            // Fallback to any external video player intent
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(genericIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "VLC is not installed. Using built-in player.", Toast.LENGTH_SHORT).show()
                TvPlayerActivity.launch(context, resolved, title)
            }
        }
    }
}
