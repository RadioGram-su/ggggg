package su.radiogram.grom.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import su.radiogram.grom.audio.PlaybackService
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.Store
import su.radiogram.grom.data.Track
import su.radiogram.grom.ui.comp.Toaster

/** Связь интерфейса с сервисом воспроизведения через MediaController. */
class PlayerConn(private val ctx: Context) {
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    val connected = MutableStateFlow(false)
    val currentId = MutableStateFlow(-1L)
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val queue = MutableStateFlow<List<Long>>(emptyList())
    val queuePos = MutableStateFlow(0)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val externalTitle = MutableStateFlow<String?>(null)
    private var pending: ((MediaController) -> Unit)? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                syncAll()
                connected.value = true
                pending?.invoke(c)
                pending = null
            } catch (e: Exception) {
                e.printStackTrace()
                future = null
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        connected.value = false
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending = block; connect() }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) syncQueue()
            syncState()
        }
    }

    private fun syncAll() { syncQueue(); syncState() }

    private fun syncState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        currentId.value = item?.mediaId?.toLongOrNull() ?: -1L
        externalTitle.value = if (item != null && item.mediaId.toLongOrNull() == null) item.mediaMetadata.title?.toString() ?: "Файл" else null
        isPlaying.value = c.isPlaying
        shuffle.value = c.shuffleModeEnabled
        repeat.value = c.repeatMode
        poll()
    }

    /** Порядок очереди с учётом перемешивания. */
    private fun syncQueue() {
        val c = controller ?: return
        val tl = c.currentTimeline
        val ids = ArrayList<Long>(tl.windowCount)
        if (!tl.isEmpty) {
            val sh = c.shuffleModeEnabled
            var i = tl.getFirstWindowIndex(sh)
            val window = Timeline.Window()
            var pos = 0
            var curPos = 0
            while (i != C.INDEX_UNSET && ids.size < 20000) {
                if (i == c.currentMediaItemIndex) curPos = pos
                ids += tl.getWindow(i, window).mediaItem.mediaId.toLongOrNull() ?: -1L
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, sh)
                pos++
            }
            queuePos.value = curPos
        }
        queue.value = ids
    }

    fun poll() {
        val c = controller ?: return
        position.value = c.currentPosition.coerceAtLeast(0)
        val d = c.duration
        duration.value = if (d == C.TIME_UNSET || d < 0) (Library[currentId.value]?.durationMs ?: 0L) else d
    }

    // ---------- команды ----------

    fun playList(tracks: List<Track>, start: Int = 0, shuffled: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffled
            val idx = if (shuffled) tracks.indices.random() else start.coerceIn(0, tracks.size - 1)
            c.setMediaItems(tracks.map { it.toMediaItem() }, idx, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playUri(uri: Uri, title: String?) {
        withController { c ->
            val item = MediaItem.Builder()
                .setMediaId("ext:$uri")
                .setUri(uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title ?: "Файл").build())
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun playNext(t: Track) {
        withController { c ->
            if (c.mediaItemCount == 0) { playList(listOf(t)); return@withController }
            c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
            Toaster.show("Сыграет следующим")
        }
    }

    fun addToQueue(t: Track) {
        withController { c ->
            if (c.mediaItemCount == 0) { playList(listOf(t)); return@withController }
            c.addMediaItem(t.toMediaItem())
            Toaster.show("Добавлено в очередь")
        }
    }

    fun toggle() = withController { c ->
        if (c.mediaItemCount == 0) {
            val all = Library.tracks.value
            if (all.isNotEmpty()) playList(all)
            return@withController
        }
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun prev() = withController { c -> if (c.currentPosition > 4000) c.seekTo(0) else c.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); position.value = ms }
    fun setShuffle(v: Boolean) = withController { it.shuffleModeEnabled = v }
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Переход к элементу очереди по позиции в отображаемом порядке. */
    fun jumpToQueue(displayPos: Int) = withController { c ->
        val idx = displayToIndex(c, displayPos) ?: return@withController
        c.seekTo(idx, 0)
        c.play()
    }

    fun removeFromQueue(displayPos: Int) = withController { c ->
        val idx = displayToIndex(c, displayPos) ?: return@withController
        c.removeMediaItem(idx)
    }

    private fun displayToIndex(c: MediaController, displayPos: Int): Int? {
        val tl = c.currentTimeline
        if (tl.isEmpty) return null
        val sh = c.shuffleModeEnabled
        var i = tl.getFirstWindowIndex(sh)
        var p = 0
        while (i != C.INDEX_UNSET) {
            if (p == displayPos) return i
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, sh)
            p++
        }
        return null
    }

    /** Если плеер пуст — вернуть прошлую очередь (на паузе). */
    fun restoreIfEmpty() = withController { c ->
        if (c.mediaItemCount > 0) return@withController
        val q = Store.queue ?: return@withController
        val items = q.ids.mapNotNull { Library[it] }
        if (items.isEmpty()) return@withController
        val idx = q.ids.getOrNull(q.index)?.let { id -> items.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
        c.setMediaItems(items.map { it.toMediaItem() }, idx, q.pos)
        c.prepare()
    }

    fun removeTrackEverywhere(id: Long) = withController { c ->
        var i = c.mediaItemCount - 1
        while (i >= 0) {
            if (c.getMediaItemAt(i).mediaId == id.toString()) c.removeMediaItem(i)
            i--
        }
    }
}
