package org.mesos.photos

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.hasPermission
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import org.mesos.core.R as CoreR

private enum class EditMode { CROP, FILTERS, ADJUST }

/** One complete edit, applied the same way to the preview and the saved copy. */
private data class Edit(
    val rotation: Int = 0,
    val flipped: Boolean = false,
    val crop: CropRect = CropRect(),
    val filter: PhotoFilter = PhotoFilter.ORIGINAL,
    val adjustments: Adjustments = Adjustments(),
) {
    val isEmpty: Boolean
        get() = rotation == 0 && !flipped && crop.isFull && filter == PhotoFilter.ORIGINAL && adjustments.isNeutral
}

/**
 * The MesOS photo editor: crop, rotate, flip, filters and adjustments. The original
 * is never changed; the result is saved as a new photo in Pictures/MesOS.
 */
@Composable
internal fun PhotoEditor(item: MediaItem, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(Edit()) }
    var mode by remember { mutableStateOf(EditMode.CROP) }
    var aspect by remember { mutableStateOf(CropAspect.FREE) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(item.uri) {
        source = withContext(Dispatchers.IO) { loadBitmap(context, item.uri, PREVIEW_SIZE) }
        failed = source == null
    }
    // The preview turned and mirrored like the result; crop and colours are applied on top.
    val oriented = remember(source, edit.rotation, edit.flipped) {
        source?.let { orient(it, edit.rotation, edit.flipped).asImageBitmap() }
    }
    val colorFilter = remember(edit.filter, edit.adjustments) {
        ColorFilter.colorMatrix(ColorMatrix(ColorMatrices.edit(edit.filter, edit.adjustments)))
    }

    val saved = stringResource(R.string.editor_saved)
    val saveFailed = stringResource(R.string.editor_save_failed)
    val save: () -> Unit = {
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveCopy(context, item, edit) }
            saving = false
            if (ok) {
                Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, saveFailed, Toast.LENGTH_LONG).show()
            }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(context, saveFailed, Toast.LENGTH_LONG).show()
    }

    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(MesOSGlyphs.Close, contentDescription = stringResource(CoreR.string.mesos_close), tint = Color.White) }
            Text(
                stringResource(R.string.editor_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { edit = Edit() }, enabled = !edit.isEmpty) {
                Text(stringResource(R.string.editor_reset), color = if (edit.isEmpty) Color.White.copy(alpha = 0.4f) else Color.White)
            }
            TextButton(
                enabled = !edit.isEmpty && !saving && source != null,
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        !context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                },
            ) { Text(stringResource(R.string.editor_save_copy)) }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val image = oriented
            when {
                failed -> Text(stringResource(R.string.editor_load_failed), color = Color.White, textAlign = TextAlign.Center)
                image == null -> CircularProgressIndicator(color = Color.White)
                mode == EditMode.CROP -> CropCanvas(
                    image = image,
                    crop = edit.crop,
                    colorFilter = colorFilter,
                    ratio = aspect.ratio,
                    onCrop = { edit = edit.copy(crop = it) },
                )
                else -> CroppedPreview(image, edit.crop, colorFilter)
            }
            if (saving) CircularProgressIndicator(color = Color.White)
        }

        when (mode) {
            EditMode.CROP -> CropTools(
                aspect = aspect,
                onAspect = { choice ->
                    aspect = choice
                    val image = oriented
                    if (image != null) edit = edit.copy(crop = CropMath.fit(CropRect(), choice.ratio, image.width.toFloat() / image.height))
                },
                // Edits act on what is on screen: turning a mirrored photo clockwise is a -90° base rotation.
                onRotate = { edit = edit.copy(rotation = (edit.rotation + if (edit.flipped) 270 else 90) % 360, crop = CropMath.rotateClockwise(edit.crop)) },
                onFlip = { edit = edit.copy(flipped = !edit.flipped, crop = CropMath.flipHorizontal(edit.crop)) },
            )
            EditMode.FILTERS -> FilterStrip(image = oriented, selected = edit.filter, onSelect = { edit = edit.copy(filter = it) })
            EditMode.ADJUST -> AdjustTools(edit.adjustments) { edit = edit.copy(adjustments = it) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ModeButton(MesOSGlyphs.Crop, stringResource(R.string.editor_crop), mode == EditMode.CROP) { mode = EditMode.CROP }
            ModeButton(MesOSGlyphs.Wand, stringResource(R.string.editor_filters), mode == EditMode.FILTERS) { mode = EditMode.FILTERS }
            ModeButton(MesOSGlyphs.Sliders, stringResource(R.string.editor_adjust), mode == EditMode.ADJUST) { mode = EditMode.ADJUST }
        }
    }
}

private class DrawnBounds {
    var rect: Rect = Rect.Zero
}

private const val PREVIEW_SIZE = 1600
private const val SAVE_SIZE = 4096

@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f)
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Where the (whole) image is drawn inside the canvas. */
private fun DrawScope.fitRect(image: ImageBitmap): Rect {
    val scale = min(size.width / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    return Rect(Offset((size.width - width) / 2, (size.height - height) / 2), Size(width, height))
}

@Composable
private fun CroppedPreview(image: ImageBitmap, crop: CropRect, colorFilter: ColorFilter) {
    Canvas(Modifier.fillMaxSize()) {
        val srcLeft = (crop.left * image.width).roundToInt()
        val srcTop = (crop.top * image.height).roundToInt()
        val srcWidth = (crop.width * image.width).roundToInt().coerceAtLeast(1)
        val srcHeight = (crop.height * image.height).roundToInt().coerceAtLeast(1)
        val scale = min(size.width / srcWidth, size.height / srcHeight)
        val width = srcWidth * scale
        val height = srcHeight * scale
        drawImage(
            image = image,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcWidth, srcHeight),
            dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun CropCanvas(image: ImageBitmap, crop: CropRect, colorFilter: ColorFilter, ratio: Float?, onCrop: (CropRect) -> Unit) {
    val latestCrop by rememberUpdatedState(crop)
    val latestRatio by rememberUpdatedState(ratio)
    val imageAspect = image.width.toFloat() / image.height
    // Where the photo was last drawn; read by the drag handler, so not Compose state.
    val drawn = remember { DrawnBounds() }
    // -1: moving the whole crop; 0..3: dragging a corner.
    var handle by remember { mutableIntStateOf(-1) }
    val accent = MaterialTheme.colorScheme.primary

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(image) {
                detectDragGestures(
                    onDragStart = { start ->
                        val c = latestCrop
                        val b = drawn.rect
                        val corners = listOf(
                            Offset(b.left + c.left * b.width, b.top + c.top * b.height),
                            Offset(b.left + c.right * b.width, b.top + c.top * b.height),
                            Offset(b.left + c.right * b.width, b.top + c.bottom * b.height),
                            Offset(b.left + c.left * b.width, b.top + c.bottom * b.height),
                        )
                        val nearest = corners.indices.minBy { hypot(corners[it].x - start.x, corners[it].y - start.y) }
                        handle = if (hypot(corners[nearest].x - start.x, corners[nearest].y - start.y) < 48.dp.toPx()) nearest else -1
                    },
                    onDragEnd = { handle = -1 },
                    onDragCancel = { handle = -1 },
                    onDrag = { change, amount ->
                        change.consume()
                        val b = drawn.rect
                        if (b.width <= 0f || b.height <= 0f) return@detectDragGestures
                        val dx = amount.x / b.width
                        val dy = amount.y / b.height
                        onCrop(
                            if (handle < 0) {
                                CropMath.move(latestCrop, dx, dy)
                            } else {
                                CropMath.dragCorner(latestCrop, handle, dx, dy, latestRatio, imageAspect)
                            },
                        )
                    },
                )
            },
    ) {
        val rect = fitRect(image)
        drawn.rect = rect
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
            colorFilter = colorFilter,
        )
        val c = Rect(
            rect.left + crop.left * rect.width,
            rect.top + crop.top * rect.height,
            rect.left + crop.right * rect.width,
            rect.top + crop.bottom * rect.height,
        )
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, Offset(rect.left, rect.top), Size(rect.width, c.top - rect.top))
        drawRect(shade, Offset(rect.left, c.bottom), Size(rect.width, rect.bottom - c.bottom))
        drawRect(shade, Offset(rect.left, c.top), Size(c.left - rect.left, c.height))
        drawRect(shade, Offset(c.right, c.top), Size(rect.right - c.right, c.height))
        drawRect(Color.White, c.topLeft, c.size, style = Stroke(1.5.dp.toPx()))
        // Rule-of-thirds guides.
        for (i in 1..2) {
            val x = c.left + c.width * i / 3
            val y = c.top + c.height * i / 3
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x, c.top), Offset(x, c.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.35f), Offset(c.left, y), Offset(c.right, y), 1.dp.toPx())
        }
        val arm = 18.dp.toPx()
        val stroke = 4.dp.toPx()
        listOf(c.topLeft to Offset(1f, 1f), c.topRight to Offset(-1f, 1f), c.bottomRight to Offset(-1f, -1f), c.bottomLeft to Offset(1f, -1f))
            .forEachIndexed { index, (corner, dir) ->
                val color = if (index == handle) accent else Color.White
                drawLine(color, corner, corner + Offset(arm * dir.x, 0f), stroke)
                drawLine(color, corner, corner + Offset(0f, arm * dir.y), stroke)
            }
    }
}

@Composable
private fun CropTools(aspect: CropAspect, onAspect: (CropAspect) -> Unit, onRotate: () -> Unit, onFlip: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CropAspect.entries) { choice ->
                FilterChip(
                    selected = choice == aspect,
                    onClick = { onAspect(choice) },
                    label = { Text(aspectLabel(choice)) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onRotate) {
                Icon(MesOSGlyphs.Rotate, contentDescription = null, tint = Color.White)
                Text(stringResource(R.string.editor_rotate), color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onFlip) {
                Icon(MesOSGlyphs.Flip, contentDescription = null, tint = Color.White)
                Text(stringResource(R.string.editor_flip), color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun aspectLabel(aspect: CropAspect): String = when (aspect) {
    CropAspect.FREE -> stringResource(R.string.editor_aspect_free)
    CropAspect.SQUARE -> stringResource(R.string.editor_aspect_square)
    CropAspect.FOUR_THREE -> "4:3"
    CropAspect.THREE_FOUR -> "3:4"
    CropAspect.SIXTEEN_NINE -> "16:9"
    CropAspect.NINE_SIXTEEN -> "9:16"
}

@Composable
private fun FilterStrip(image: ImageBitmap?, selected: PhotoFilter, onSelect: (PhotoFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(PhotoFilter.entries) { filter ->
            val filterMatrix = remember(filter) { ColorFilter.colorMatrix(ColorMatrix(ColorMatrices.filter(filter))) }
            Column(
                Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(filter) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                ) {
                    if (image != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val side = min(image.width, image.height)
                            drawImage(
                                image = image,
                                srcOffset = IntOffset((image.width - side) / 2, (image.height - side) / 2),
                                srcSize = IntSize(side, side),
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                colorFilter = filterMatrix,
                            )
                        }
                    }
                    if (filter == selected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        )
                    }
                }
                Text(
                    stringResource(filterLabel(filter)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (filter == selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

private fun filterLabel(filter: PhotoFilter): Int = when (filter) {
    PhotoFilter.ORIGINAL -> R.string.editor_filter_original
    PhotoFilter.VIVID -> R.string.editor_filter_vivid
    PhotoFilter.WARM -> R.string.editor_filter_warm
    PhotoFilter.COOL -> R.string.editor_filter_cool
    PhotoFilter.FADE -> R.string.editor_filter_fade
    PhotoFilter.MONO -> R.string.editor_filter_mono
    PhotoFilter.NOIR -> R.string.editor_filter_noir
    PhotoFilter.SEPIA -> R.string.editor_filter_sepia
}

@Composable
private fun AdjustTools(adjustments: Adjustments, onChange: (Adjustments) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        AdjustSlider(stringResource(R.string.editor_brightness), adjustments.brightness) { onChange(adjustments.copy(brightness = it)) }
        AdjustSlider(stringResource(R.string.editor_contrast), adjustments.contrast) { onChange(adjustments.copy(contrast = it)) }
        AdjustSlider(stringResource(R.string.editor_saturation), adjustments.saturation) { onChange(adjustments.copy(saturation = it)) }
        AdjustSlider(stringResource(R.string.editor_warmth), adjustments.warmth) { onChange(adjustments.copy(warmth = it)) }
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.width(96.dp))
        Slider(
            value = local,
            onValueChange = {
                // Snap to zero near the middle so "untouched" is easy to find again.
                local = if (abs(it) < 0.04f) 0f else it
                onChange(local)
            },
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f),
        )
        Text(
            (local * 100).roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
    }
}

// ---- Bitmaps ----

private fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF orientation itself.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > maxSide) {
                    val scale = maxSide.toFloat() / largest
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
            val degrees = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (degrees == 0) decoded else orient(decoded, degrees, false)
        }
    } catch (e: IOException) {
        MesOSLog.w(MesOSLog.SYSTEM, "Photo could not be opened for editing", e)
        null
    } catch (e: RuntimeException) {
        MesOSLog.w(MesOSLog.SYSTEM, "Photo could not be decoded for editing", e)
        null
    } catch (e: OutOfMemoryError) {
        MesOSLog.w(MesOSLog.SYSTEM, "Photo too large to edit", null)
        null
    }

/** Rotates, then mirrors horizontally (so a flip always mirrors what is on screen). */
private fun orient(bitmap: Bitmap, rotation: Int, flipped: Boolean): Bitmap {
    if (rotation == 0 && !flipped) return bitmap
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        if (flipped) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Renders [edit] at full quality and saves it as a new JPEG. Call off the main thread. */
private fun saveCopy(context: Context, item: MediaItem, edit: Edit): Boolean {
    val full = loadBitmap(context, item.uri, SAVE_SIZE) ?: return false
    return try {
        val oriented = orient(full, edit.rotation, edit.flipped)
        val left = (edit.crop.left * oriented.width).roundToInt().coerceIn(0, oriented.width - 1)
        val top = (edit.crop.top * oriented.height).roundToInt().coerceIn(0, oriented.height - 1)
        val width = (edit.crop.width * oriented.width).roundToInt().coerceIn(1, oriented.width - left)
        val height = (edit.crop.height * oriented.height).roundToInt().coerceIn(1, oriented.height - top)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(result).drawBitmap(
            oriented,
            android.graphics.Rect(left, top, left + width, top + height),
            android.graphics.Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrices.edit(edit.filter, edit.adjustments))
            },
        )
        val name = item.name.substringBeforeLast('.') + "-edit-" + System.currentTimeMillis() / 1000 + ".jpg"
        writeJpeg(context, name, result)
    } catch (e: OutOfMemoryError) {
        MesOSLog.w(MesOSLog.SYSTEM, "Edited photo too large to save", null)
        false
    }
}

private fun writeJpeg(context: Context, name: String, bitmap: Bitmap): Boolean {
    val compress = { out: OutputStream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MesOS")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        return try {
            val ok = resolver.openOutputStream(uri)?.use(compress) == true
            if (ok) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            ok
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }
    @Suppress("DEPRECATION")
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MesOS")
    if (!dir.exists() && !dir.mkdirs()) return false
    val file = File(dir, name)
    return try {
        val ok = FileOutputStream(file).use(compress)
        if (ok) MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        ok
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
