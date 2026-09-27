package org.mesos.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Aurora glyph set: rounded 2 dp stroke icons on a 24 dp grid, drawn from SVG
 * path data so every MesOS screen uses the same line style. Tint them like any
 * other icon (`Icon(MesOSGlyphs.Wifi, …)`).
 */
object MesOSGlyphs {

    // Navigation and actions
    val Back by glyph { s("M15,5 L8,12 L15,19") }
    val ChevronRight by glyph { s("M9,5 L16,12 L9,19") }
    val ChevronDown by glyph { s("M5,9 L12,16 L19,9") }
    val ChevronUp by glyph { s("M5,15 L12,8 L19,15") }
    val Close by glyph { s("M6,6 L18,18 M18,6 L6,18") }
    val Check by glyph { s("M5,12.5 L10,17.5 L19,7") }
    val Plus by glyph { s("M12,5 V19 M5,12 H19") }
    val Minus by glyph { s("M5,12 H19") }
    val Search by glyph { s(circle(11f, 11f, 7f) + " M16.2,16.2 L20.5,20.5") }
    val More by glyph { f(circle(12f, 5f, 1.7f) + circle(12f, 12f, 1.7f) + circle(12f, 19f, 1.7f)) }
    val Trash by glyph { s("M4,7 H20 M9,7 V4.5 H15 V7 M6.5,7 L7.5,20 H16.5 L17.5,7 M10,11 V16 M14,11 V16") }
    val Share by glyph {
        s(circle(18f, 5.5f, 2.5f) + circle(6f, 12f, 2.5f) + circle(18f, 18.5f, 2.5f))
        s("M8.2,10.8 L15.8,6.7 M8.2,13.2 L15.8,17.3")
    }
    val Edit by glyph {
        s("M4,20 L4.8,15.6 L15.5,4.9 A2.1,2.1 0 0 1 18.5,4.9 L19.1,5.5 A2.1,2.1 0 0 1 19.1,8.5 L8.4,19.2 Z M13.5,7 L17,10.5")
    }
    val Copy by glyph {
        s("M8,8 H18 A2,2 0 0 1 20,10 V18 A2,2 0 0 1 18,20 H10 A2,2 0 0 1 8,18 Z M16,8 V6 A2,2 0 0 0 14,4 H6 A2,2 0 0 0 4,6 V14 A2,2 0 0 0 6,16 H8")
    }
    val Link by glyph {
        s("M10,14 A4,4 0 0 0 15.7,14 L18.5,11.2 A4,4 0 0 0 12.8,5.5 L11.5,6.8 M14,10 A4,4 0 0 0 8.3,10 L5.5,12.8 A4,4 0 0 0 11.2,18.5 L12.5,17.2")
    }
    val Refresh by glyph { s("M20,12 A8,8 0 1 1 17.7,6.3 M20,3.5 V8.3 H15.2") }
    val Undo by glyph { s("M9,14 L4,9 L9,4 M4,9 H14 A6,6 0 0 1 14,21 H10") }
    val Info by glyph {
        s(circle(12f, 12f, 9f) + " M12,11 V16.5")
        f(circle(12f, 7.8f, 1.3f))
    }
    val Warning by glyph {
        s("M12,3.5 L21.5,20 H2.5 Z M12,10 V14")
        f(circle(12f, 17f, 1.2f))
    }
    val Download by glyph {
        s("M12,4 V14 M8,10.5 L12,14.5 L16,10.5 M5,16 V17.5 A2.5,2.5 0 0 0 7.5,20 H16.5 A2.5,2.5 0 0 0 19,17.5 V16")
    }
    val Upload by glyph {
        s("M12,15 V4.5 M8,8 L12,4 L16,8 M5,16 V17.5 A2.5,2.5 0 0 0 7.5,20 H16.5 A2.5,2.5 0 0 0 19,17.5 V16")
    }
    val Star by glyph { s(STAR) }
    val StarFilled by glyph { f(STAR) }
    val Heart by glyph { s("M12,20 C12,20 4,15 4,9.5 A4.2,4.2 0 0 1 12,7.5 A4.2,4.2 0 0 1 20,9.5 C20,15 12,20 12,20 Z") }
    val Eye by glyph {
        s("M2.5,12 C5,7 8.5,5 12,5 C15.5,5 19,7 21.5,12 C19,17 15.5,19 12,19 C8.5,19 5,17 2.5,12 Z")
        s(circle(12f, 12f, 3f))
    }
    val Sparkle by glyph {
        f("M11,2.5 L12.8,8.7 L19,10.5 L12.8,12.3 L11,18.5 L9.2,12.3 L3,10.5 L9.2,8.7 Z")
        f("M18.5,15 L19.3,17.2 L21.5,18 L19.3,18.8 L18.5,21 L17.7,18.8 L15.5,18 L17.7,17.2 Z")
    }

    // System
    val Settings by glyph {
        s(gear())
        s(circle(12f, 12f, 3f))
    }
    val Bell by glyph { s("M6,16.5 V11 A6,6 0 0 1 18,11 V16.5 L19.5,18 H4.5 Z M10,20.5 A2.2,2.2 0 0 0 14,20.5") }
    val Shield by glyph {
        s("M12,3 L19,5.8 V11 C19,15.6 16,19.2 12,21 C8,19.2 5,15.6 5,11 V5.8 Z M9,12 L11.2,14.2 L15.2,10")
    }
    val Lock by glyph { s("M6,11 H18 V20 H6 Z M8.5,11 V8 A3.5,3.5 0 0 1 15.5,8 V11") }
    val Grid by glyph { s("M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M14,14 H20 V20 H14 Z") }
    val Widgets by glyph { s("M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M17,14 V20 M14,17 H20") }
    val Apps by glyph {
        f(
            circle(6f, 6f, 1.8f) + circle(12f, 6f, 1.8f) + circle(18f, 6f, 1.8f) +
                circle(6f, 12f, 1.8f) + circle(12f, 12f, 1.8f) + circle(18f, 12f, 1.8f) +
                circle(6f, 18f, 1.8f) + circle(12f, 18f, 1.8f) + circle(18f, 18f, 1.8f),
        )
    }
    val Palette by glyph {
        s("M12,3 A9,9 0 1 0 12,21 C13.5,21 14,20 13.5,18.8 C13,17.5 14,16.5 15.3,16.5 H17 A4,4 0 0 0 21,12.5 C21,7.3 17,3 12,3 Z")
        f(circle(7.5f, 11.5f, 1.3f) + circle(10f, 7.5f, 1.3f) + circle(14.5f, 7.5f, 1.3f) + circle(17f, 11.5f, 1.3f))
    }
    val Image by glyph {
        s("M4,5 H20 V19 H4 Z M4,16 L9,11 L13,15 L15.5,12.5 L20,17")
        f(circle(15.5f, 8.5f, 1.5f))
    }
    val Globe by glyph {
        s(circle(12f, 12f, 9f) + " M3,12 H21 M12,3 C15,6 15,18 12,21 M12,3 C9,6 9,18 12,21")
    }
    val Home by glyph { s("M4,11 L12,4 L20,11 V20 H14.5 V14.5 H9.5 V20 H4 Z") }
    val Storage by glyph {
        s("M4,6 C4,4.3 7.6,3 12,3 C16.4,3 20,4.3 20,6 V18 C20,19.7 16.4,21 12,21 C7.6,21 4,19.7 4,18 Z M4,6 C4,7.7 7.6,9 12,9 C16.4,9 20,7.7 20,6 M4,12 C4,13.7 7.6,15 12,15 C16.4,15 20,13.7 20,12")
    }
    val Memory by glyph {
        s("M7,7 H17 V17 H7 Z M10,3.5 V7 M14,3.5 V7 M10,17 V20.5 M14,17 V20.5 M3.5,10 H7 M3.5,14 H7 M17,10 H20.5 M17,14 H20.5")
    }
    val Battery by glyph {
        s("M3,8 H18 V16 H3 Z M20.5,10.5 V13.5")
        f("M5,10 H11 V14 H5 Z")
    }
    val Bolt by glyph { f("M13,2.5 L5,13.5 H11 L10,21.5 L19,10 H13 Z") }
    val Thermometer by glyph { s("M10,13.5 V5 A2,2 0 0 1 14,5 V13.5 A4,4 0 1 1 10,13.5 Z") }

    // Control center
    val Wifi by glyph {
        s("M2.5,9 C8,3.8 16,3.8 21.5,9 M5.5,12.5 C9.5,8.9 14.5,8.9 18.5,12.5 M8.7,16 C10.6,14.4 13.4,14.4 15.3,16")
        f(circle(12f, 19.3f, 1.5f))
    }
    val Bluetooth by glyph { s("M6.5,7.5 L17,16.5 L12,21 V3 L17,7.5 L6.5,16.5") }
    val Flashlight by glyph { s("M8,2.5 H16 V6 L14,9.5 V21.5 H10 V9.5 L8,6 Z M12,13 V15.5") }
    val Moon by glyph { f("M20,14.5 A8.5,8.5 0 1 1 9.5,4 A7,7 0 0 0 20,14.5 Z") }
    val Rotate by glyph {
        s("M4,12 A8,8 0 0 1 18,6.7 M18,2.5 V6.7 H13.8 M20,12 A8,8 0 0 1 6,17.3 M6,21.5 V17.3 H10.2")
    }
    val Contrast by glyph {
        s(circle(12f, 12f, 8f))
        f("M12,4 A8,8 0 0 1 12,20 Z")
    }
    val Record by glyph {
        s(circle(12f, 12f, 8.5f))
        f(circle(12f, 12f, 4f))
    }
    val Qr by glyph {
        s("M3.5,8 V5.5 A2,2 0 0 1 5.5,3.5 H8 M16,3.5 H18.5 A2,2 0 0 1 20.5,5.5 V8 M20.5,16 V18.5 A2,2 0 0 1 18.5,20.5 H16 M8,20.5 H5.5 A2,2 0 0 1 3.5,18.5 V16 M7,12 H17")
    }
    val Location by glyph {
        s("M12,21 C12,21 5,14.6 5,9.5 A7,7 0 0 1 19,9.5 C19,14.6 12,21 12,21 Z")
        f(circle(12f, 9.5f, 2.4f))
    }
    val Sun by glyph {
        s(circle(12f, 12f, 4.2f))
        s("M12,2.5 V4.5 M12,19.5 V21.5 M2.5,12 H4.5 M19.5,12 H21.5 M5.3,5.3 L6.7,6.7 M17.3,17.3 L18.7,18.7 M5.3,18.7 L6.7,17.3 M17.3,6.7 L18.7,5.3")
    }
    val Volume by glyph {
        f("M3.5,9.5 H7.5 L12.5,5.5 V18.5 L7.5,14.5 H3.5 Z")
        s("M16,8.5 A5,5 0 0 1 16,15.5 M18.5,6 A8.5,8.5 0 0 1 18.5,18")
    }
    val VolumeOff by glyph {
        f("M3.5,9.5 H7.5 L12.5,5.5 V18.5 L7.5,14.5 H3.5 Z")
        s("M16,9.5 L21,14.5 M21,9.5 L16,14.5")
    }

    // Communication
    val Phone by glyph {
        s("M5,4.5 H8.5 L10,8.5 L8,10 C9,12.5 11.5,15 14,16 L15.5,14 L19.5,15.5 V19 A1.5,1.5 0 0 1 18,20.5 C10.5,20 4,13.5 3.5,6 A1.5,1.5 0 0 1 5,4.5 Z")
    }
    val CallEnd by glyph {
        f("M2.5,13.5 C7,9 17,9 21.5,13.5 L19.8,16.3 L16,15 V12.6 C13.4,11.8 10.6,11.8 8,12.6 V15 L4.2,16.3 Z")
    }
    val Message by glyph {
        s("M12,4 C16.97,4 21,7.36 21,11.5 C21,15.64 16.97,19 12,19 C10.8,19 9.7,18.8 8.7,18.5 L4,20 L5.3,16.3 C3.9,15 3,13.3 3,11.5 C3,7.36 7.03,4 12,4 Z")
    }
    val Send by glyph { s("M4,12 L20,4 L14,20 L11.5,13 Z M11.5,13 L20,4") }
    val Person by glyph {
        s(circle(12f, 8f, 4f))
        s("M4.5,20 C5.5,16 8.5,14 12,14 C15.5,14 18.5,16 19.5,20")
    }
    val PersonAdd by glyph {
        s(circle(10f, 8f, 4f))
        s("M3,20 C4,16 7,14 10,14 C12,14 13.8,14.6 15.2,15.8 M19,11 V17 M16,14 H22")
    }
    val Mail by glyph { s("M3.5,6 H20.5 V18 H3.5 Z M4,6.5 L12,13 L20,6.5") }
    val Mic by glyph { s("M9,6 A3,3 0 0 1 15,6 V11 A3,3 0 0 1 9,11 Z M5.5,11 A6.5,6.5 0 0 0 18.5,11 M12,17.5 V21") }
    val MicOff by glyph {
        s("M9,6 A3,3 0 0 1 15,6 V11 A3,3 0 0 1 9,11 Z M5.5,11 A6.5,6.5 0 0 0 18.5,11 M12,17.5 V21 M4,4 L20,20")
    }
    val Keypad by glyph {
        f(
            circle(6f, 4.5f, 1.6f) + circle(12f, 4.5f, 1.6f) + circle(18f, 4.5f, 1.6f) +
                circle(6f, 10f, 1.6f) + circle(12f, 10f, 1.6f) + circle(18f, 10f, 1.6f) +
                circle(6f, 15.5f, 1.6f) + circle(12f, 15.5f, 1.6f) + circle(18f, 15.5f, 1.6f) +
                circle(12f, 21f, 1.6f),
        )
    }
    val Backspace by glyph { s("M8.5,5.5 H20.5 V18.5 H8.5 L3,12 Z M11.5,9.5 L16.5,14.5 M16.5,9.5 L11.5,14.5") }

    // Media
    val Play by glyph { f("M7,4.5 L19.5,12 L7,19.5 Z") }
    val Pause by glyph { f("M6.5,4.5 H10 V19.5 H6.5 Z M14,4.5 H17.5 V19.5 H14 Z") }
    val Stop by glyph { f("M6,6 H18 V18 H6 Z") }
    val Next by glyph {
        f("M5,5.5 L15,12 L5,18.5 Z")
        s("M18,5 V19")
    }
    val Previous by glyph {
        f("M19,5.5 L9,12 L19,18.5 Z")
        s("M6,5 V19")
    }
    val Shuffle by glyph {
        s("M4,7 H7.5 C11,7 13,17 16.5,17 H20 M4,17 H7.5 C9.3,17 10.5,14.5 11.5,12 M20,7 H16.5 C14.7,7 13.5,9.5 12.5,12 M17.5,4.5 L20,7 L17.5,9.5 M17.5,14.5 L20,17 L17.5,19.5")
    }
    val Repeat by glyph {
        s("M4,11 V9 A3,3 0 0 1 7,6 H19 M16,3 L19,6 L16,9 M20,13 V15 A3,3 0 0 1 17,18 H5 M8,21 L5,18 L8,15")
    }
    val Music by glyph {
        s("M9,18 V6 L19,4 V16")
        f(circle(6.5f, 18f, 2.6f) + circle(16.5f, 16f, 2.6f))
    }
    val Camera by glyph {
        s("M4,8 H7 L9,5.5 H15 L17,8 H20 V19 H4 Z")
        s(circle(12f, 13f, 3.5f))
    }
    val Video by glyph { s("M3.5,6.5 H15 V17.5 H3.5 Z M15,10.5 L20.5,7.5 V16.5 L15,13.5") }
    val Folder by glyph {
        s("M3.5,6.5 A1.5,1.5 0 0 1 5,5 H9.5 L11.5,7 H19 A1.5,1.5 0 0 1 20.5,8.5 V17.5 A1.5,1.5 0 0 1 19,19 H5 A1.5,1.5 0 0 1 3.5,17.5 Z")
    }

    // Time
    val Clock by glyph { s(circle(12f, 12f, 9f) + " M12,7 V12 L15.5,14") }
    val Alarm by glyph { s(circle(12f, 13f, 7.5f) + " M12,9.5 V13 L14.5,14.5 M4,5.5 L7,3 M20,5.5 L17,3") }
    val Stopwatch by glyph { s(circle(12f, 13.5f, 7.5f) + " M12,13.5 V9.5 M10,3 H14 M12,3 V6 M18.5,7 L20,5.5") }
    val Hourglass by glyph {
        s("M6.5,3.5 H17.5 M6.5,20.5 H17.5 M7.5,3.5 C7.5,9 16.5,9 16.5,12 C16.5,15 7.5,15 7.5,20.5 M16.5,3.5 C16.5,9 7.5,9 7.5,12 C7.5,15 16.5,15 16.5,20.5")
    }
    val Flag by glyph { s("M5,21 V4 M5,4 H17 L15,8 L17,12 H5") }
    val Calendar by glyph { s("M4,6 H20 V20 H4 Z M4,10 H20 M8,3.5 V7 M16,3.5 V7") }
    val History by glyph { s("M3.5,12 A8.5,8.5 0 1 0 6,6 M3.5,3.5 V8 H8 M12,8 V12.5 L15,14") }

    // Weather
    val Cloud by glyph { s("M7,18.5 H17 A4,4 0 0 0 17.5,10.5 A5.5,5.5 0 0 0 7,11.2 A3.7,3.7 0 0 0 7,18.5 Z") }
    val Drop by glyph { s("M12,3 C12,3 5.5,10 5.5,14.5 A6.5,6.5 0 0 0 18.5,14.5 C18.5,10 12,3 12,3 Z") }
    val Wind by glyph { s("M3,9 H14 A3,3 0 1 0 11,6 M3,15 H17 A3,3 0 1 1 14,18 M3,12 H9") }
    val Sunrise by glyph {
        s("M4,18 H20 M7.5,18 A4.5,4.5 0 0 1 16.5,18 M12,4 V10 M9.5,6.5 L12,4 L14.5,6.5 M5,12 L6.5,13.5 M19,12 L17.5,13.5")
    }
    val Sunset by glyph {
        s("M4,18 H20 M7.5,18 A4.5,4.5 0 0 1 16.5,18 M12,4 V10 M9.5,7.5 L12,10 L14.5,7.5 M5,12 L6.5,13.5 M19,12 L17.5,13.5")
    }
    val Umbrella by glyph { s("M3,12 A9,9 0 0 1 21,12 Z M12,12 V18.5 A2,2 0 0 1 8,18.5") }

    // Browser
    val Tabs by glyph { s("M7,7 H19 V19 H7 Z M4,16 V5 A1,1 0 0 1 5,4 H16") }
    val Bookmark by glyph { s("M6,4 H18 V20.5 L12,16.5 L6,20.5 Z") }

    // Editing
    val Crop by glyph { s("M6,2.5 V18 H21.5 M2.5,6 H18 V21.5") }
    val Flip by glyph { s("M12,3 V21 M9,6 L4,18 H9 Z M15,6 L20,18 H15 Z") }
    val Sliders by glyph {
        s("M4,7 H14 M18,7 H20 M4,17 H6 M10,17 H20")
        s(circle(16f, 7f, 2f) + circle(8f, 17f, 2f))
    }
    val Wand by glyph {
        s("M4,20 L14,10 M12.5,8.5 L15.5,11.5")
        f("M18,3 L18.7,4.8 L20.5,5.5 L18.7,6.2 L18,8 L17.3,6.2 L15.5,5.5 L17.3,4.8 Z")
    }

    private const val STAR = "M12,3.5 L14.6,9 L20.5,9.7 L16.1,13.7 L17.3,19.6 L12,16.6 L6.7,19.6 L7.9,13.7 L3.5,9.7 L9.4,9 Z"
}

private class GlyphBuilder {
    val parts = mutableListOf<Pair<String, Boolean>>()

    /** Stroked path. */
    fun s(path: String) {
        parts += path to false
    }

    /** Filled path. */
    fun f(path: String) {
        parts += path to true
    }
}

/** Lazily builds a 24 × 24 glyph from SVG path data. */
private fun glyph(block: GlyphBuilder.() -> Unit): Lazy<ImageVector> = lazy {
    val parts = GlyphBuilder().apply(block).parts
    val builder = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for ((path, filled) in parts) {
        builder.addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = if (filled) 0f else 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    builder.build()
}

/** SVG path of a circle (two relative arcs), usable inside a larger path. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    String.format(Locale.US, " M%.2f,%.2f a%.2f,%.2f 0 1 0 %.2f,0 a%.2f,%.2f 0 1 0 %.2f,0 Z", cx - r, cy, r, r, 2 * r, r, r, -2 * r)

/** A gear outline with eight teeth, centred on the 24 grid. */
private fun gear(): String {
    val teeth = 8
    val outer = 9.6
    val inner = 7.4
    val step = 2 * Math.PI / teeth
    val sb = StringBuilder()
    for (i in 0 until teeth) {
        val a = i * step - Math.PI / 2
        val points = listOf(
            inner to a - step * 0.36,
            outer to a - step * 0.2,
            outer to a + step * 0.2,
            inner to a + step * 0.36,
        )
        points.forEachIndexed { index, (r, angle) ->
            val x = 12 + r * cos(angle)
            val y = 12 + r * sin(angle)
            sb.append(if (i == 0 && index == 0) "M" else " L")
            sb.append(String.format(Locale.US, "%.2f,%.2f", x, y))
        }
    }
    sb.append(" Z")
    return sb.toString()
}
