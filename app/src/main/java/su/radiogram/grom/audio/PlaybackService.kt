package su.radiogram.grom.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import su.radiogram.grom.R
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.QueueSnap
import su.radiogram.grom.data.Store
import kotlin.math.min

const val LONG_TRACK_MS = 20L * 60 * 1000

object SleepTimer {
    data class State(val endAt: Long, val finishTrack: Boolean, val fade: Boolean, val waiting: Boolean = false)

    val state = MutableStateFlow<State?>(null)

    fun start(minutes: Int, finishTrack: Boolean, fade: Boolean) {
        AudioEngine.fade = 1f
        state.value = State(System.currentTimeMillis() + minutes * 60_000L, finishTrack, fade)
    }

    fun cancel() {
        state.value = null
        AudioEngine.fade = 1f
        PlaybackService.instance?.player?.pauseAtEndOfMediaItems = false
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    lateinit var player: ExoPlayer
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var curId = -1L
    private var listenedMs = 0L
    private var counted = false
    private var lastQueueSave = 0L

    companion object {
        @Volatile var instance: PlaybackService? = null
        const val ACTION_FAV = "grom.fav"
        val FAV_CMD = SessionCommand(ACTION_FAV, Bundle.EMPTY)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val processor = GromProcessor()
        val sink = MeterSink(
            DefaultAudioSink.Builder(this)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                .build()
        )
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink))
        }
        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(listener)

        val open = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).apply {
            putExtra("open_player", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(this, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(pi)
            .build()

        scope.launch { Store.favorites.collect { updateLayout() } }
        scope.launch {
            while (true) {
                delay(500)
                tick()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveState()
        instance = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(FAV_CMD).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_FAV) {
                if (curId >= 0) Store.toggleFavorite(curId)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            return Futures.immediateFuture(mediaItems.map { resolve(it) }.toMutableList())
        }
    }

    private fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        item.mediaId.toLongOrNull()?.let { id -> Library[id]?.let { return it.toMediaItem() } }
        val uri = item.requestMetadata.mediaUri ?: return item
        return item.buildUpon().setUri(uri).build()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val prev = curId
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && prev >= 0) Store.clearPosition(prev)
            curId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
            listenedMs = 0
            counted = false
            val t = Library[curId]
            if (t != null && t.durationMs >= LONG_TRACK_MS) {
                val p = Store.positions.value[curId]
                if (p != null && p > 5_000 && p < t.durationMs - 15_000) player.seekTo(p)
            }
            updateLayout()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                val t = Library[curId]
                if (t != null && t.durationMs >= LONG_TRACK_MS) Store.savePosition(curId, player.currentPosition)
                saveState()
            }
        }
    }

    private fun updateLayout() {
        val s = session ?: return
        val fav = curId >= 0 && Store.isFav(curId)
        val btn = CommandButton.Builder()
            .setDisplayName(if (fav) "Убрать из избранного" else "В избранное")
            .setIconResId(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            .setSessionCommand(FAV_CMD)
            .build()
        s.setCustomLayout(ImmutableList.of(btn))
    }

    private fun tick() {
        SleepTimer.state.value?.let { handleSleep(it) }
        if (!player.isPlaying || curId < 0) return
        listenedMs += 500
        val t = Library[curId]
        if (!counted) {
            val dur = player.duration.takeIf { it > 0 } ?: t?.durationMs ?: 0L
            val need = if (dur > 0) min(30_000L, dur / 2) else 30_000L
            if (listenedMs >= need) {
                counted = true
                Store.recordPlay(curId)
            }
        }
        if (t != null && t.durationMs >= LONG_TRACK_MS && listenedMs % 5_000L == 0L) {
            Store.savePosition(curId, player.currentPosition)
        }
        if (System.currentTimeMillis() - lastQueueSave > 15_000) saveState()
    }

    private fun saveState() {
        if (!::player.isInitialized) return
        lastQueueSave = System.currentTimeMillis()
        val n = player.mediaItemCount
        if (n == 0) return
        val ids = ArrayList<Long>(min(n, 5000))
        for (i in 0 until min(n, 5000)) {
            val id = player.getMediaItemAt(i).mediaId.toLongOrNull() ?: return
            ids += id
        }
        Store.saveQueue(QueueSnap(ids, player.currentMediaItemIndex.coerceIn(0, ids.size - 1), player.currentPosition))
    }

    private fun handleSleep(st: SleepTimer.State) {
        val now = System.currentTimeMillis()
        if (!st.waiting) {
            val rem = st.endAt - now
            if (st.fade && !st.finishTrack) AudioEngine.fade = (rem / 30_000f).coerceIn(0f, 1f)
            if (rem <= 0) {
                if (st.finishTrack && player.isPlaying) {
                    SleepTimer.state.value = st.copy(waiting = true)
                    player.pauseAtEndOfMediaItems = true
                } else {
                    player.pause()
                    finishSleep()
                }
            }
        } else {
            if (st.fade) {
                val d = player.duration
                val p = player.currentPosition
                if (d > 0) AudioEngine.fade = ((d - p) / 15_000f).coerceIn(0f, 1f)
            }
            if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) finishSleep()
        }
    }

    private fun finishSleep() {
        player.pauseAtEndOfMediaItems = false
        SleepTimer.state.value = null
        scope.launch {
            delay(600)
            AudioEngine.fade = 1f
        }
    }
}
