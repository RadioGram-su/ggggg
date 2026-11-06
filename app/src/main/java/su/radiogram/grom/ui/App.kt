package su.radiogram.grom.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import su.radiogram.grom.data.ArtLoader
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.M3u
import su.radiogram.grom.data.Playlist
import su.radiogram.grom.data.Store
import su.radiogram.grom.data.Track
import su.radiogram.grom.data.Trash
import su.radiogram.grom.data.TrashItem
import su.radiogram.grom.ui.comp.Toaster
import su.radiogram.grom.ui.comp.ToastHost
import su.radiogram.grom.ui.comp.tracksWord
import su.radiogram.grom.ui.screens.ActionsSheet
import su.radiogram.grom.ui.screens.AddToPlaylistSheet
import su.radiogram.grom.ui.screens.CollectionScreen
import su.radiogram.grom.ui.screens.DrivingScreen
import su.radiogram.grom.ui.screens.EqScreen
import su.radiogram.grom.ui.screens.HiddenScreen
import su.radiogram.grom.ui.screens.HomeScreen
import su.radiogram.grom.ui.screens.MiniPlayer
import su.radiogram.grom.ui.screens.PermissionScreen
import su.radiogram.grom.ui.screens.PlayerScreen
import su.radiogram.grom.ui.screens.QueueSheet
import su.radiogram.grom.ui.screens.SettingsScreen
import su.radiogram.grom.ui.screens.SleepSheet
import su.radiogram.grom.ui.screens.TagEditor
import su.radiogram.grom.ui.screens.TrashScreen
import su.radiogram.grom.ui.theme.LocalGrom
import su.radiogram.grom.ui.theme.SIGNAL
import su.radiogram.grom.ui.theme.amoled
import su.radiogram.grom.ui.theme.graphite
import java.io.File

enum class CollKind { NEW, TOP, FORGOT, NEVER, PLAYLIST, ALBUM, ARTIST }

sealed interface Route {
    val key: String
    data object Home : Route { override val key = "home" }
    data class Eq(val fromPlayer: Boolean = false) : Route { override val key = "eq" }
    data object Settings : Route { override val key = "settings" }
    data object Trash : Route { override val key = "trash" }
    data object Hidden : Route { override val key = "hidden" }
    data object Driving : Route { override val key = "driving" }
    data class Coll(val kind: CollKind, val id: String, val title: String) : Route { override val key = "coll:$kind:$id" }
}

data class ActionCtx(val trackId: Long, val playlistId: String? = null)

class AppState(val conn: PlayerConn) {
    val stack = mutableStateListOf<Route>(Route.Home)
    var playerOpen by mutableStateOf(false)
    var actions by mutableStateOf<ActionCtx?>(null)
    var addToPlaylist by mutableStateOf<List<Long>?>(null)
    var editTags by mutableStateOf<Long?>(null)
    var sleepSheet by mutableStateOf(false)
    var queueSheet by mutableStateOf(false)
    var showLyrics by mutableStateOf(false)
    var homeTab by mutableIntStateOf(0)
    val seenAtStart: Long = Store.lastSeenAdded

    val top: Route get() = stack.last()

    fun push(r: Route) {
        if (stack.lastOrNull() != r) stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        val r = stack.removeAt(stack.lastIndex)
        if (r is Route.Eq && r.fromPlayer) playerOpen = true
        return true
    }

    fun openEqFromPlayer() {
        playerOpen = false
        push(Route.Eq(fromPlayer = true))
    }
}

/** Системные окна: корзина, выбор файлов, экспорт. */
class Launchers(
    val trash: (List<Track>) -> Unit,
    val restore: (List<TrashItem>) -> Unit,
    val deleteForever: (List<TrashItem>, () -> Unit) -> Unit,
    val pickLrc: (Long) -> Unit,
    val pickCover: ((Uri?) -> Unit) -> Unit,
    val exportM3u: (Playlist) -> Unit,
    val importM3u: () -> Unit,
    val allFiles: () -> Unit,
)

val LocalApp = staticCompositionLocalOf<AppState> { error("no app") }
val LocalLaunchers = staticCompositionLocalOf<Launchers> { error("no launchers") }

@Composable
fun GromRoot(conn: PlayerConn, intents: StateFlow<Intent?>) {
    val ctx = LocalContext.current
    val app = remember { AppState(conn) }
    val scope = rememberCoroutineScope()
    val settings by Store.settings.collectAsState()
    val currentId by conn.currentId.collectAsState()
    val all by Library.all.collectAsState()
    val loaded by Library.loaded.collectAsState()
    val track = all[currentId]

    // ---------- цвет из обложки ----------
    var coverAccent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(track?.id, track?.coverPath, settings.coverTint) {
        coverAccent = if (settings.coverTint && track != null) ArtLoader.accent(ctx, track) else null
    }
    val accent by animateColorAsState(coverAccent ?: SIGNAL, tween(900), label = "accent")
    val colors = if (settings.amoled) amoled(accent) else graphite(accent)

    // ---------- разрешения ----------
    var hasPerm by remember { mutableStateOf(Library.hasPermission(ctx)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = Library.hasPermission(ctx)
        if (hasPerm) {
            Library.start()
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun requestPerms() {
        val list = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permLauncher.launch(list)
    }

    // ---------- системные окна ----------
    var afterSender by remember { mutableStateOf<(() -> Unit)?>(null) }
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) afterSender?.invoke()
        afterSender = null
    }
    var lrcFor by remember { mutableStateOf(-1L) }
    val lrcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = lrcFor
        if (uri != null && id >= 0) {
            try {
                val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }
                val f = File(dir, "$id.lrc")
                ctx.contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
                Store.setLyricsFile(id, f.absolutePath)
                Toaster.show("Текст прикреплён")
            } catch (e: Exception) {
                Toaster.show("Не удалось прочитать файл")
            }
        }
    }
    var coverCb by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        coverCb?.invoke(uri); coverCb = null
    }
    var exportPl by remember { mutableStateOf<Playlist?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val p = exportPl
        if (uri != null && p != null) {
            val tracks = p.ids.mapNotNull { Library[it] }
            Toaster.show(if (M3u.export(ctx, uri, tracks)) "Сохранено: ${tracksWord(tracks.size)}" else "Не получилось сохранить")
        }
        exportPl = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val r = M3u.import(ctx, uri, Library.all.value.values)
            if (r == null) Toaster.show("Не удалось прочитать плейлист")
            else if (r.ids.isEmpty()) Toaster.show("Ни один трек из плейлиста не найден")
            else {
                Store.createPlaylist(r.name, r.ids)
                Toaster.show("«${r.name}»: найдено ${r.ids.size} из ${r.total}")
            }
        }
    }

    val launchers = remember {
        Launchers(
            trash = { tracks ->
                if (Trash.system) {
                    val pi = Trash.trashRequest(ctx, tracks.map { it.uri }, true)
                    if (pi != null) {
                        afterSender = {
                            tracks.forEach { conn.removeTrackEverywhere(it.id) }
                            Library.refresh(300)
                            Toaster.show("В корзине. Вернуть можно 30 дней")
                        }
                        senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    Store.trashLegacy(tracks.map { it.id })
                    tracks.forEach { conn.removeTrackEverywhere(it.id) }
                    Toaster.show("В корзине. Вернуть можно 30 дней")
                }
            },
            restore = { items ->
                if (Trash.system) {
                    val pi = Trash.trashRequest(ctx, items.map { it.uri }, false)
                    if (pi != null) {
                        afterSender = { Library.refresh(300); Toaster.show("Возвращено") }
                        senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    Store.restoreLegacy(items.map { it.id })
                    Toaster.show("Возвращено")
                }
            },
            deleteForever = { items, done ->
                if (Trash.system) {
                    val pi = Trash.deleteRequest(ctx, items.map { it.uri })
                    if (pi != null) {
                        afterSender = { Library.refresh(300); Toaster.show("Удалено навсегда"); done() }
                        senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    val n = Trash.deleteLegacy(ctx, items)
                    Toaster.show(if (n > 0) "Удалено навсегда" else "Система не дала удалить файл")
                    done()
                }
            },
            pickLrc = { id -> lrcFor = id; lrcLauncher.launch(arrayOf("*/*")) },
            pickCover = { cb ->
                coverCb = cb
                coverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            exportM3u = { p -> exportPl = p; exportLauncher.launch(p.name.replace('/', '_') + ".m3u8") },
            importM3u = {
                importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
            },
            allFiles = {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                    } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            },
        )
    }

    // ---------- позиция воспроизведения ----------
    val isPlaying by conn.isPlaying.collectAsState()
    LaunchedEffect(isPlaying) {
        while (true) {
            conn.poll()
            delay(if (isPlaying) 90 else 500)
        }
    }

    // ---------- первый запуск, восстановление очереди, интенты ----------
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        if (Store.lastSeenAdded == 0L) Library.tracks.value.maxOfOrNull { it.dateAdded }?.let { Store.markSeen(it) }
        conn.restoreIfEmpty()
    }
    LaunchedEffect(Unit) {
        Library.arrivals.collect { n -> Toaster.show("+ ${tracksWord(n)} — уже сверху списка") }
    }
    val intent by intents.collectAsState()
    LaunchedEffect(intent, loaded) {
        val i = intent ?: return@LaunchedEffect
        if (i.getBooleanExtra("open_player", false)) {
            app.playerOpen = true
            i.removeExtra("open_player")
        }
        if (i.action == Intent.ACTION_VIEW && i.data != null) {
            val uri = i.data!!
            var name: String? = null
            try {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
            } catch (_: Exception) {}
            conn.playUri(uri, name?.substringBeforeLast('.'))
            app.playerOpen = true
            i.data = null
        }
        val ci = i.getStringExtra("ci")
        if (ci != null && loaded) {
            i.removeExtra("ci")
            for (step in ci.split(',')) {
                when (step.trim()) {
                    "play" -> { conn.playList(Library.tracks.value); delay(1500) }
                    "fav" -> { Library.tracks.value.take(3).forEach { if (!Store.isFav(it.id)) Store.toggleFavorite(it.id) } }
                    "player" -> { app.stack.retainAll { it == Route.Home }; app.playerOpen = true }
                    "close" -> app.playerOpen = false
                    "lyrics" -> { app.playerOpen = true; app.showLyrics = true }
                    "nolyrics" -> app.showLyrics = false
                    "eq" -> { app.playerOpen = false; app.push(Route.Eq()) }
                    "settings" -> { app.playerOpen = false; app.push(Route.Settings) }
                    "driving" -> { app.playerOpen = false; app.push(Route.Driving) }
                    "home" -> { app.playerOpen = false; app.stack.retainAll { it == Route.Home } }
                    "lists" -> { app.playerOpen = false; app.stack.retainAll { it == Route.Home }; app.homeTab = 2 }
                    "tracks" -> { app.homeTab = 0 }
                    "8d" -> Store.updateEq { it.copy(eightD = !it.eightD) }
                    "preset" -> su.radiogram.grom.audio.Presets.byId("punch")?.let { Store.applyPreset(it) }
                    "sleep" -> app.sleepSheet = true
                    "queue" -> app.queueSheet = true
                    "actions" -> Library.tracks.value.firstOrNull()?.let { app.actions = ActionCtx(it.id) }
                    "dismiss" -> { app.sleepSheet = false; app.queueSheet = false; app.actions = null }
                }
                delay(400)
            }
        }
    }

    CompositionLocalProvider(LocalGrom provides colors, LocalApp provides app, LocalLaunchers provides launchers) {
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = colors.bg, surface = colors.panel, onSurface = colors.text)) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
                if (!hasPerm) {
                    PermissionScreen(onGrant = { requestPerms() })
                } else {
                    MainContent(app, track)
                }
            }
        }
    }
}

@Composable
private fun MainContent(app: AppState, track: Track?) {
    val scope = rememberCoroutineScope()
    val holder = rememberSaveableStateHolder()
    val ext by app.conn.externalTitle.collectAsState()
    val top = app.top

    BackHandler(enabled = app.stack.size > 1) { app.pop() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                val forward = app.stack.indexOf(targetState) >= app.stack.indexOf(initialState) || initialState !in app.stack
                if (forward) {
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 3 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { -it / 6 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(180)))
                }
            },
            label = "nav",
        ) { r ->
            holder.SaveableStateProvider(r.key) {
                when (r) {
                    Route.Home -> HomeScreen()
                    is Route.Eq -> EqScreen()
                    Route.Settings -> SettingsScreen()
                    Route.Trash -> TrashScreen()
                    Route.Hidden -> HiddenScreen()
                    Route.Driving -> DrivingScreen(track)
                    is Route.Coll -> CollectionScreen(r)
                }
            }
        }

        val showMini = (track != null || ext != null) && top != Route.Driving
        val open = remember { Animatable(0f) }
        LaunchedEffect(app.playerOpen) {
            open.animateTo(if (app.playerOpen) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 340f))
        }
        if (showMini) {
            Box(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = 1f - open.value; translationY = open.value * 60.dp.toPx() }) {
                MiniPlayer(track, ext) { app.playerOpen = true }
            }
        }
        if (open.value > 0.001f) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - open.value) * size.height }) {
                PlayerScreen(
                    track = track,
                    onClose = { app.playerOpen = false },
                    onDrag = { dy, h -> scope.launch { open.snapTo((open.value - dy / h).coerceIn(0f, 1f)) } },
                    onDragEnd = {
                        if (open.value < 0.8f) app.playerOpen = false
                        else scope.launch { open.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                    },
                )
            }
        }
        BackHandler(enabled = app.playerOpen) { app.playerOpen = false }

        ToastHost(bottom = if (showMini && !app.playerOpen) 96.dp else 40.dp)

        app.actions?.let { a -> ActionsSheet(a) { app.actions = null } }
        app.addToPlaylist?.let { ids -> AddToPlaylistSheet(ids) { app.addToPlaylist = null } }
        app.editTags?.let { id -> TagEditor(id) { app.editTags = null } }
        if (app.sleepSheet) SleepSheet { app.sleepSheet = false }
        if (app.queueSheet) QueueSheet { app.queueSheet = false }
    }
}
