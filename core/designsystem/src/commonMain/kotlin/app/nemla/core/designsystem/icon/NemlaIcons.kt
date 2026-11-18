package app.nemla.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object NemlaIcons {
    val Eye: ImageVector by lazy {
        lucide(
            "Eye",
            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
            circle(12f, 12f, 3f),
        )
    }

    val EyeOff: ImageVector by lazy {
        lucide(
            "EyeOff",
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "M2 2l20 20",
        )
    }

    val ShieldCheck: ImageVector by lazy {
        lucide(
            "ShieldCheck",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72" +
                "a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "M9 12l2 2 4-4",
        )
    }

    val AlertTriangle: ImageVector by lazy {
        lucide(
            "AlertTriangle",
            "M21.73 18l-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    val Info: ImageVector by lazy {
        lucide("Info", circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01")
    }

    val Check: ImageVector by lazy {
        lucide("Check", "M20 6L9 17l-5-5")
    }

    val ChevronDown: ImageVector by lazy {
        lucide("ChevronDown", "M6 9l6 6 6-6")
    }

    val LogOut: ImageVector by lazy {
        lucide("LogOut", "M16 17l5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4")
    }

    val Mic: ImageVector by lazy {
        lucide(
            "Mic",
            "M12 19v3",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            "M12 2a3 3 0 0 1 3 3v7a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z",
        )
    }

    val Sparkles: ImageVector by lazy {
        lucide(
            "Sparkles",
            "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135" +
                "a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063" +
                "a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
            "M20 3v4",
            "M22 5h-4",
            "M4 17v2",
            "M5 18H3",
        )
    }

    val Square: ImageVector by lazy {
        lucide("Square", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z")
    }

    val Loader: ImageVector by lazy {
        lucide("Loader", "M21 12a9 9 0 1 1-6.219-8.56")
    }

    val Trash2: ImageVector by lazy {
        lucide(
            "Trash2",
            "M3 6h18",
            "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
            "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
            "M10 11v6",
            "M14 11v6",
        )
    }

    val Plus: ImageVector by lazy {
        lucide("Plus", "M5 12h14", "M12 5v14")
    }

    val Pencil: ImageVector by lazy {
        lucide(
            "Pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32" +
                "a2 2 0 0 0 .83-.497z",
            "M15 5l4 4",
        )
    }

    val FileUp: ImageVector by lazy {
        lucide(
            "FileUp",
            "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z",
            "M14 2v4a2 2 0 0 0 2 2h4",
            "M12 12v6",
            "M15 15l-3-3-3 3",
        )
    }

    val X: ImageVector by lazy {
        lucide("X", "M18 6L6 18", "M6 6l12 12")
    }

    val Calendar: ImageVector by lazy {
        lucide(
            "Calendar",
            "M8 2v4",
            "M16 2v4",
            "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M3 10h18",
        )
    }

    val ChevronLeft: ImageVector by lazy {
        lucide("ChevronLeft", "M15 18l-6-6 6-6")
    }

    val ChevronRight: ImageVector by lazy {
        lucide("ChevronRight", "M9 18l6-6-6-6")
    }

    val Sun: ImageVector by lazy {
        lucide(
            "Sun",
            circle(12f, 12f, 4f),
            "M12 2v2",
            "M12 20v2",
            "M4.93 4.93l1.41 1.41",
            "M17.66 17.66l1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "M6.34 17.66l-1.41 1.41",
            "M19.07 4.93l-1.41 1.41",
        )
    }

    val Cloud: ImageVector by lazy {
        lucide("Cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9z")
    }

    val CloudRain: ImageVector by lazy {
        lucide(
            "CloudRain",
            "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242",
            "M16 14v6",
            "M8 14v6",
            "M12 16v6",
        )
    }

    val CloudLightning: ImageVector by lazy {
        lucide(
            "CloudLightning",
            "M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973",
            "M13 12l-3 5h4l-3 5",
        )
    }

    val Filter: ImageVector by lazy {
        lucide("Filter", "M22 3H2l8 9.46V19l4 2v-8.54z")
    }

    val Search: ImageVector by lazy {
        lucide("Search", circle(11f, 11f, 8f), "M21 21l-4.3-4.3")
    }

    val CalendarDays: ImageVector by lazy {
        lucide(
            "CalendarDays",
            "M8 2v4",
            "M16 2v4",
            "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M3 10h18",
            "M8 14h.01",
            "M12 14h.01",
            "M16 14h.01",
            "M8 18h.01",
            "M12 18h.01",
            "M16 18h.01",
        )
    }

    val CreditCard: ImageVector by lazy {
        lucide("CreditCard", "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M2 10h20")
    }

    val UserRound: ImageVector by lazy {
        lucide("UserRound", circle(12f, 8f, 5f), "M20 21a8 8 0 0 0-16 0")
    }

    val RefreshCw: ImageVector by lazy {
        lucide(
            "RefreshCw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    val Wallet: ImageVector by lazy {
        lucide(
            "Wallet",
            "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
            "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
        )
    }

    val Repeat: ImageVector by lazy {
        lucide("Repeat", "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")
    }

    val User: ImageVector by lazy {
        lucide("User", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f))
    }

    val Settings: ImageVector by lazy {
        lucide(
            "Settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38" +
                "a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38" +
                "a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
                "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08" +
                "a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08" +
                "a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }

    val ArrowLeft: ImageVector by lazy {
        lucide("ArrowLeft", "M12 19l-7-7 7-7", "M19 12H5")
    }

    val Moon: ImageVector by lazy {
        lucide("Moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z")
    }

    val Wand: ImageVector by lazy {
        lucide(
            "Wand",
            "M21.64 3.64l-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0" +
                "L21.64 5.36a1.2 1.2 0 0 0 0-1.72",
            "M14 7l3 3",
            "M5 6v4",
            "M19 14v4",
            "M10 2v2",
            "M7 8H3",
            "M21 16h-4",
            "M11 3H9",
        )
    }

    val ListTree: ImageVector by lazy {
        lucide("ListTree", "M21 12h-8", "M21 6H8", "M21 18h-8", "M3 6v4c0 1.1.9 2 2 2h3", "M3 10v6c0 1.1.9 2 2 2h3")
    }

    val HeartHandshake: ImageVector by lazy {
        lucide(
            "HeartHandshake",
            "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5" +
                "c0 2.3 1.5 4.05 3 5.5l7 7z",
            "M12 5L9.04 7.96a2.17 2.17 0 0 0 0 3.08c.82.82 2.13.85 3 .07l2.07-1.9a2.82 2.82 0 0 1 3.79 0l2.96 2.66",
            "M18 15l-2-2",
            "M15 18l-2-2",
        )
    }

    val Baby: ImageVector by lazy {
        lucide(
            "Baby",
            "M9 12h.01",
            "M15 12h.01",
            "M10 16c.5.3 1.2.5 2 .5s1.5-.2 2-.5",
            "M19 6.3a9 9 0 0 1 1.8 3.9 2 2 0 0 1 0 3.6 9 9 0 0 1-17.6 0 2 2 0 0 1 0-3.6A9 9 0 0 1 12 3" +
                "c2 0 3.5 1.1 3.5 2.5s-.9 2.5-2 2.5c-.8 0-1.5-.4-1.5-1",
        )
    }

    val HousePlus: ImageVector by lazy {
        lucide(
            "HousePlus",
            "M13.22 2.416a2 2 0 0 0-2.511.057l-7 5.999A2 2 0 0 0 3 10v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7.354",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M15 6h6",
            "M18 3v6",
        )
    }

    val BarChart: ImageVector by lazy {
        lucide("BarChart", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3")
    }

    val BellRing: ImageVector by lazy {
        lucide(
            "BellRing",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M22 8c0-2.3-.8-4.3-2-6",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8" +
                "c0 4.499-1.411 5.956-2.738 7.326",
            "M4 2C2.8 3.7 2 5.7 2 8",
        )
    }

    val TrendingUp: ImageVector by lazy {
        lucide("TrendingUp", "M16 7h6v6", "M22 7l-8.5 8.5-5-5L2 17")
    }

    val TrendingDown: ImageVector by lazy {
        lucide("TrendingDown", "M16 17h6v-6", "M22 17l-8.5-8.5-5 5L2 7")
    }

    val History: ImageVector by lazy {
        lucide("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")
    }

    private fun circle(
        cx: Float,
        cy: Float,
        r: Float,
    ): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun lucide(
        name: String,
        vararg paths: String,
    ): ImageVector {
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
