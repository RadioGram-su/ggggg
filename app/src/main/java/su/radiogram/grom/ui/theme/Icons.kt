package su.radiogram.grom.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/** Собственный набор иконок: тонкая техничная линия, квадратная геометрия. */
object Ic {
    private fun line(name: String, w: Float = 1.6f, vararg paths: PathBuilder.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = PathData(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = w,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun solid(name: String, vararg paths: PathBuilder.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) b.addPath(pathData = PathData(p), fill = SolidColor(Color.Black))
        return b.build()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    val Play = solid("play", {
        moveTo(8.2f, 5.2f)
        curveTo(7.5f, 4.8f, 6.8f, 5.3f, 6.8f, 6.1f)
        lineTo(6.8f, 17.9f)
        curveTo(6.8f, 18.7f, 7.5f, 19.2f, 8.2f, 18.8f)
        lineTo(18.4f, 12.9f)
        curveTo(19.1f, 12.5f, 19.1f, 11.5f, 18.4f, 11.1f)
        close()
    })

    val Pause = solid("pause", {
        moveTo(7f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(7f, 19f); close()
    }, {
        moveTo(14f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(14f, 19f); close()
    })

    val Next = solid("next", {
        moveTo(5.5f, 6.3f); lineTo(14.2f, 12f); lineTo(5.5f, 17.7f); close()
    }, {
        moveTo(16f, 6f); lineTo(18.4f, 6f); lineTo(18.4f, 18f); lineTo(16f, 18f); close()
    })

    val Prev = solid("prev", {
        moveTo(18.5f, 6.3f); lineTo(9.8f, 12f); lineTo(18.5f, 17.7f); close()
    }, {
        moveTo(5.6f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(5.6f, 18f); close()
    })

    val Shuffle = line("shuffle", 1.6f, {
        moveTo(4f, 7f); lineTo(7.5f, 7f); curveTo(11f, 7f, 12.5f, 17f, 16.5f, 17f); lineTo(20f, 17f)
    }, {
        moveTo(4f, 17f); lineTo(7.5f, 17f); curveTo(9.2f, 17f, 10.4f, 15.4f, 11.2f, 13.6f)
    }, {
        moveTo(12.9f, 10.4f); curveTo(13.8f, 8.6f, 14.9f, 7f, 16.5f, 7f); lineTo(20f, 7f)
    }, {
        moveTo(17.6f, 4.6f); lineTo(20f, 7f); lineTo(17.6f, 9.4f)
    }, {
        moveTo(17.6f, 14.6f); lineTo(20f, 17f); lineTo(17.6f, 19.4f)
    })

    val Repeat = line("repeat", 1.6f, {
        moveTo(5f, 11f); lineTo(5f, 9.5f); curveTo(5f, 8.1f, 6.1f, 7f, 7.5f, 7f); lineTo(19f, 7f)
    }, {
        moveTo(16.6f, 4.6f); lineTo(19f, 7f); lineTo(16.6f, 9.4f)
    }, {
        moveTo(19f, 13f); lineTo(19f, 14.5f); curveTo(19f, 15.9f, 17.9f, 17f, 16.5f, 17f); lineTo(5f, 17f)
    }, {
        moveTo(7.4f, 14.6f); lineTo(5f, 17f); lineTo(7.4f, 19.4f)
    })

    val Heart = line("heart", 1.7f, {
        moveTo(12f, 19.6f)
        curveTo(12f, 19.6f, 4f, 14.6f, 4f, 9.2f)
        curveTo(4f, 6.7f, 5.9f, 5f, 8.1f, 5f)
        curveTo(9.7f, 5f, 11.1f, 5.9f, 12f, 7.2f)
        curveTo(12.9f, 5.9f, 14.3f, 5f, 15.9f, 5f)
        curveTo(18.1f, 5f, 20f, 6.7f, 20f, 9.2f)
        curveTo(20f, 14.6f, 12f, 19.6f, 12f, 19.6f)
        close()
    })

    val HeartFill = solid("heartFill", {
        moveTo(12f, 19.6f)
        curveTo(12f, 19.6f, 4f, 14.6f, 4f, 9.2f)
        curveTo(4f, 6.7f, 5.9f, 5f, 8.1f, 5f)
        curveTo(9.7f, 5f, 11.1f, 5.9f, 12f, 7.2f)
        curveTo(12.9f, 5.9f, 14.3f, 5f, 15.9f, 5f)
        curveTo(18.1f, 5f, 20f, 6.7f, 20f, 9.2f)
        curveTo(20f, 14.6f, 12f, 19.6f, 12f, 19.6f)
        close()
    })

    val Search = line("search", 1.7f, { circle(10.5f, 10.5f, 5.5f) }, { moveTo(14.6f, 14.6f); lineTo(19.5f, 19.5f) })
    val Close = line("close", 1.7f, { moveTo(6f, 6f); lineTo(18f, 18f) }, { moveTo(18f, 6f); lineTo(6f, 18f) })
    val Back = line("back", 1.7f, { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) })
    val Down = line("down", 1.7f, { moveTo(5.5f, 9f); lineTo(12f, 15.5f); lineTo(18.5f, 9f) })
    val Plus = line("plus", 1.7f, { moveTo(12f, 5f); lineTo(12f, 19f) }, { moveTo(5f, 12f); lineTo(19f, 12f) })
    val Check = line("check", 1.8f, { moveTo(5f, 12.5f); lineTo(9.8f, 17f); lineTo(19f, 7f) })
    val More = solid("more", { circle(12f, 5.5f, 1.6f) }, { circle(12f, 12f, 1.6f) }, { circle(12f, 18.5f, 1.6f) })

    val Sliders = line("sliders", 1.6f,
        { moveTo(6f, 4f); lineTo(6f, 20f) }, { moveTo(12f, 4f); lineTo(12f, 20f) }, { moveTo(18f, 4f); lineTo(18f, 20f) },
        { moveTo(3.8f, 14f); lineTo(8.2f, 14f) }, { moveTo(9.8f, 8f); lineTo(14.2f, 8f) }, { moveTo(15.8f, 16f); lineTo(20.2f, 16f) },
    )

    val Gear = line("gear", 1.6f, { circle(12f, 12f, 3f) }, {
        // зубчатое кольцо как у переключателя
        moveTo(12f, 3.5f); lineTo(12f, 6f)
        moveTo(12f, 18f); lineTo(12f, 20.5f)
        moveTo(3.5f, 12f); lineTo(6f, 12f)
        moveTo(18f, 12f); lineTo(20.5f, 12f)
        moveTo(6f, 6f); lineTo(7.8f, 7.8f)
        moveTo(16.2f, 16.2f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(16.2f, 7.8f)
        moveTo(7.8f, 16.2f); lineTo(6f, 18f)
    }, { circle(12f, 12f, 6.2f) })

    val Timer = line("timer", 1.6f, { circle(12f, 13f, 7f) }, { moveTo(12f, 13f); lineTo(12f, 9.5f) }, { moveTo(9.5f, 3.5f); lineTo(14.5f, 3.5f) },
        { moveTo(17.6f, 6.6f); lineTo(18.8f, 5.4f) })

    val Lyrics = line("lyrics", 1.6f, { moveTo(4f, 6f); lineTo(20f, 6f) }, { moveTo(4f, 10.5f); lineTo(16f, 10.5f) },
        { moveTo(4f, 15f); lineTo(11f, 15f) }, { circle(16.5f, 17f, 2f) }, { moveTo(18.5f, 17f); lineTo(18.5f, 11.5f); lineTo(20.5f, 12.3f) })

    val Queue = line("queue", 1.6f, { moveTo(4f, 6f); lineTo(16f, 6f) }, { moveTo(4f, 11f); lineTo(16f, 11f) },
        { moveTo(4f, 16f); lineTo(11f, 16f) }, { moveTo(15f, 14f); lineTo(15f, 20f); lineTo(20f, 17f); close() })

    val Car = line("car", 1.6f, {
        moveTo(4f, 16f); lineTo(4f, 12.5f); lineTo(6f, 7.8f); curveTo(6.3f, 7.1f, 6.9f, 6.7f, 7.7f, 6.7f)
        lineTo(16.3f, 6.7f); curveTo(17.1f, 6.7f, 17.7f, 7.1f, 18f, 7.8f); lineTo(20f, 12.5f); lineTo(20f, 16f); close()
    }, { moveTo(4.5f, 12.5f); lineTo(19.5f, 12.5f) }, { moveTo(6f, 16f); lineTo(6f, 18.5f) }, { moveTo(18f, 16f); lineTo(18f, 18.5f) },
        { circle(7.8f, 14.3f, 0.6f) }, { circle(16.2f, 14.3f, 0.6f) })

    val Orbit = line("orbit", 1.5f, { circle(12f, 12f, 2.6f) }, {
        moveTo(3f, 12f); curveTo(3f, 8.5f, 7f, 6.5f, 12f, 6.5f); curveTo(17f, 6.5f, 21f, 8.5f, 21f, 12f)
        curveTo(21f, 15.5f, 17f, 17.5f, 12f, 17.5f); curveTo(7f, 17.5f, 3f, 15.5f, 3f, 12f); close()
    }, { moveTo(19.2f, 5.2f); lineTo(20.6f, 3.8f) })

    val Trash = line("trash", 1.6f, { moveTo(4.5f, 7f); lineTo(19.5f, 7f) }, { moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f) },
        { moveTo(6.5f, 7f); lineTo(7.4f, 19.5f); lineTo(16.6f, 19.5f); lineTo(17.5f, 7f) }, { moveTo(10.3f, 10.5f); lineTo(10.3f, 16f) },
        { moveTo(13.7f, 10.5f); lineTo(13.7f, 16f) })

    val Edit = line("edit", 1.6f, { moveTo(5f, 19f); lineTo(5.6f, 15.4f); lineTo(15.8f, 5.2f); lineTo(18.8f, 8.2f); lineTo(8.6f, 18.4f); close() },
        { moveTo(13.6f, 7.4f); lineTo(16.6f, 10.4f) })

    val Restore = line("restore", 1.6f, { moveTo(5f, 12f); curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f); curveTo(15.9f, 5f, 19f, 8.1f, 19f, 12f)
        curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f); curveTo(9.6f, 19f, 7.5f, 17.8f, 6.2f, 16f) }, { moveTo(5f, 7.5f); lineTo(5f, 12f); lineTo(9.5f, 12f) })

    val Import = line("import", 1.6f, { moveTo(12f, 4f); lineTo(12f, 14.5f) }, { moveTo(7.8f, 10.5f); lineTo(12f, 14.7f); lineTo(16.2f, 10.5f) },
        { moveTo(5f, 15.5f); lineTo(5f, 19.5f); lineTo(19f, 19.5f); lineTo(19f, 15.5f) })

    val Export = line("export", 1.6f, { moveTo(12f, 15f); lineTo(12f, 4.5f) }, { moveTo(7.8f, 8.5f); lineTo(12f, 4.3f); lineTo(16.2f, 8.5f) },
        { moveTo(5f, 15.5f); lineTo(5f, 19.5f); lineTo(19f, 19.5f); lineTo(19f, 15.5f) })

    val Image = line("image", 1.6f, { moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close() },
        { moveTo(4.5f, 16.5f); lineTo(9.5f, 11.5f); lineTo(13f, 15f); lineTo(15.5f, 12.5f); lineTo(19.5f, 16.5f) }, { circle(15.5f, 8.7f, 1.4f) })

    val NextUp = line("nextUp", 1.6f, { moveTo(4f, 7f); lineTo(14f, 7f) }, { moveTo(4f, 12f); lineTo(11f, 12f) }, { moveTo(4f, 17f); lineTo(11f, 17f) },
        { moveTo(17.5f, 10f); lineTo(17.5f, 20f) }, { moveTo(14.5f, 13f); lineTo(17.5f, 10f); lineTo(20.5f, 13f) })

    val AddList = line("addList", 1.6f, { moveTo(4f, 7f); lineTo(16f, 7f) }, { moveTo(4f, 12f); lineTo(16f, 12f) }, { moveTo(4f, 17f); lineTo(11f, 17f) },
        { moveTo(17.5f, 14f); lineTo(17.5f, 20f) }, { moveTo(14.5f, 17f); lineTo(20.5f, 17f) })

    val Speaker = line("speaker", 1.6f, { moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close() },
        { moveTo(15.5f, 9f); curveTo(16.5f, 10.5f, 16.5f, 13.5f, 15.5f, 15f) }, { moveTo(18.2f, 6.8f); curveTo(20.4f, 9.5f, 20.4f, 14.5f, 18.2f, 17.2f) })

    val Headphones = line("headphones", 1.6f, { moveTo(4.5f, 16f); lineTo(4.5f, 12.5f); curveTo(4.5f, 8.4f, 7.9f, 5f, 12f, 5f); curveTo(16.1f, 5f, 19.5f, 8.4f, 19.5f, 12.5f); lineTo(19.5f, 16f) },
        { moveTo(4.5f, 14f); lineTo(7.5f, 14f); lineTo(7.5f, 19.5f); lineTo(4.5f, 19.5f); close() }, { moveTo(16.5f, 14f); lineTo(19.5f, 14f); lineTo(19.5f, 19.5f); lineTo(16.5f, 19.5f); close() })

    val Folder = line("folder", 1.6f, { moveTo(4f, 7f); lineTo(4f, 18.5f); lineTo(20f, 18.5f); lineTo(20f, 8.5f); lineTo(11.5f, 8.5f); lineTo(9.8f, 6f); lineTo(4f, 6f); close() })

    val Eye = line("eye", 1.6f, { moveTo(3f, 12f); curveTo(5.5f, 7.5f, 8.5f, 5.8f, 12f, 5.8f); curveTo(15.5f, 5.8f, 18.5f, 7.5f, 21f, 12f)
        curveTo(18.5f, 16.5f, 15.5f, 18.2f, 12f, 18.2f); curveTo(8.5f, 18.2f, 5.5f, 16.5f, 3f, 12f); close() }, { circle(12f, 12f, 2.8f) })
}
