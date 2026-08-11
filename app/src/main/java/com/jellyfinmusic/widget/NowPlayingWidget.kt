package com.jellyfinmusic.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.action.actionStartActivity
import com.jellyfinmusic.MainActivity
import com.jellyfinmusic.R

/**
 * Home screen widget showing what is playing, with transport controls.
 *
 * Widgets run in the launcher's process, so this cannot reach the player
 * directly. State is written into the widget's own store by the service, and
 * the buttons send media commands back — the same route the notification and
 * lock screen already use.
 */
class NowPlayingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val title = prefs[KEY_TITLE].orEmpty()
        val artist = prefs[KEY_ARTIST].orEmpty()
        val playing = prefs[KEY_PLAYING] == "true"

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title.isBlank()) {
                    Text(
                        "Nothing playing",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                    )
                } else {
                    Text(
                        title,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        artist,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }

                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(R.drawable.ic_widget_previous, "Previous", ACTION_PREVIOUS)
                    ControlButton(
                        if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        if (playing) "Pause" else "Play",
                        ACTION_PLAY_PAUSE
                    )
                    ControlButton(R.drawable.ic_widget_next, "Next", ACTION_NEXT)
                }
            }
        }
    }

    @Composable
    private fun ControlButton(iconRes: Int, description: String, action: String) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            modifier = GlanceModifier
                .size(44.dp)
                .padding(8.dp)
                .clickable(
                    androidx.glance.appwidget.action.actionSendBroadcast(
                        Intent(action).setPackage("com.jellyfinmusic")
                    )
                )
        )
    }

    companion object {
        val KEY_TITLE = stringPreferencesKey("title")
        val KEY_ARTIST = stringPreferencesKey("artist")
        val KEY_PLAYING = stringPreferencesKey("playing")

        const val ACTION_PLAY_PAUSE = "com.jellyfinmusic.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.jellyfinmusic.widget.NEXT"
        const val ACTION_PREVIOUS = "com.jellyfinmusic.widget.PREVIOUS"

        /** Called by the service whenever the track or transport state changes. */
        suspend fun publish(context: Context, title: String, artist: String, isPlaying: Boolean) {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            manager.getGlanceIds(NowPlayingWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[KEY_TITLE] = title
                    prefs[KEY_ARTIST] = artist
                    prefs[KEY_PLAYING] = isPlaying.toString()
                }
                NowPlayingWidget().update(context, id)
            }
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
