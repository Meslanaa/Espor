package org.mesos.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesos.core.ui.theme.Manrope
import java.io.IOException
import kotlin.math.abs

/**
 * Loads a small bitmap for a content:// image (album art, contact photo, media
 * thumbnail) off the main thread. Null while loading or when there is none.
 */
@Composable
fun rememberContentThumbnail(uri: Uri?, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, px) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { loadThumbnail(context, uri, px) }
    }
    return bitmap
}

fun loadThumbnail(context: Context, uri: Uri, sizePx: Int): ImageBitmap? =
    try {
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
        bitmap?.asImageBitmap()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: UnsupportedOperationException) {
        null
    }

private val avatarColors = listOf(
    Color(0xFF6366F1), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFF43F5E), Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFEC4899),
)

/** Colour an [Avatar] uses for [name]; the same name always gets the same colour. */
fun avatarColor(name: String): Color = avatarColors[abs(name.hashCode() % avatarColors.size)]

/** Initials of a display name ("Ayşe Yılmaz" → "AY"), or null when it has no letters. */
fun initialsOf(name: String): String? {
    val words = name.trim().split(Regex("\\s+")).filter { word -> word.firstOrNull()?.isLetter() == true }
    if (words.isEmpty()) return null
    val first = words.first().first()
    val last = if (words.size > 1) words.last().first() else null
    return (first.toString() + (last?.toString() ?: "")).uppercase()
}

/** Round contact picture: the photo when there is one, else initials on a colour. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, photo: Uri? = null, size: Dp = 44.dp) {
    val bitmap = rememberContentThumbnail(photo, size)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            val initials = initialsOf(name)
            if (initials != null) {
                Text(
                    text = initials,
                    color = Color.White,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp),
                )
            } else {
                androidx.compose.material3.Icon(
                    MesOSGlyphs.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}
