package su.radiogram.grom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import su.radiogram.grom.data.Library
import su.radiogram.grom.data.Store
import su.radiogram.grom.ui.GromRoot
import su.radiogram.grom.ui.PlayerConn

class MainActivity : ComponentActivity() {
    private lateinit var conn: PlayerConn
    private val intents = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        conn = PlayerConn(applicationContext)
        intents.value = intent
        setContent { GromRoot(conn, intents) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intents.value = Intent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (Library.hasPermission(this)) Library.start()
        conn.connect()
    }

    override fun onStop() {
        super.onStop()
        Library.tracks.value.maxOfOrNull { it.dateAdded }?.let { Store.markSeen(it) }
    }

    override fun onDestroy() {
        conn.release()
        super.onDestroy()
    }
}
