package org.mesos.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.mesos.core.MesOSApps
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.startActivitySafely
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class CaptureMode { PHOTO, VIDEO }

/** Something this camera session saved: shown as the thumbnail, opened in MesOS Photos. */
private data class Capture(val uri: Uri, val mimeType: String)

private val RecordingRed = Color(0xFFE03131)
private const val MAX_RESULT_THUMBNAIL_PX = 512

@Composable
internal fun CameraApp(request: CaptureRequest?, onResult: (Int, Intent?) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        PermissionGate(
            permissions = buildList {
                add(Manifest.permission.CAMERA)
                // Saving to shared storage needs this before Android 10.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            },
            rationale = stringResource(R.string.camera_access_rationale),
        ) {
            CameraContent(request, onResult)
        }
    }
}

@Composable
private fun CameraContent(request: CaptureRequest?, onResult: (Int, Intent?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalActivity.current as ComponentActivity
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var mode by rememberSaveable { mutableStateOf(CaptureMode.PHOTO) }
    var backCamera by rememberSaveable { mutableStateOf(true) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var lastCapture by remember { mutableStateOf<Capture?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var capturing by remember { mutableStateOf(false) }
    var shutterFlash by remember { mutableStateOf(false) }

    val controller = remember(context) { LifecycleCameraController(context) }
    DisposableEffect(lifecycleOwner, controller) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            recording?.stop()
            controller.unbind()
        }
    }
    LaunchedEffect(mode) {
        controller.setEnabledUseCases(
            if (mode == CaptureMode.PHOTO) CameraController.IMAGE_CAPTURE else CameraController.VIDEO_CAPTURE,
        )
    }
    LaunchedEffect(backCamera) {
        val selector = if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        runCatching { controller.cameraSelector = selector }
    }
    LaunchedEffect(flashMode) { controller.imageCaptureFlashMode = flashMode }
    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(90)
            shutterFlash = false
        }
    }

    // Video can be recorded without sound if the microphone is refused.
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(mode) {
        if (mode == CaptureMode.VIDEO && !context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun takePhoto() {
        capturing = true
        shutterFlash = true
        val output = request?.output
        when {
            // Another app asked for the photo in its own file.
            request != null && output != null -> {
                val stream = runCatching { context.contentResolver.openOutputStream(output) }.getOrNull()
                if (stream == null) {
                    capturing = false
                    onResult(Activity.RESULT_CANCELED, null)
                    return
                }
                val options = ImageCapture.OutputFileOptions.Builder(stream).build()
                controller.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        runCatching { stream.close() }
                        onResult(Activity.RESULT_OK, null)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runCatching { stream.close() }
                        capturing = false
                        toast(R.string.camera_capture_failed)
                    }
                })
            }
            // Another app asked for a small photo returned in the result.
            request != null -> {
                controller.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.use { thumbnail(it) }
                        onResult(Activity.RESULT_OK, Intent().putExtra("data", bitmap))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        toast(R.string.camera_capture_failed)
                    }
                })
            }
            else -> {
                val options = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaValues("IMG", "image/jpeg"),
                ).build()
                controller.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        capturing = false
                        results.savedUri?.let { lastCapture = Capture(it, "image/jpeg") }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        toast(R.string.camera_capture_failed)
                    }
                })
            }
        }
    }

    @SuppressLint("MissingPermission") // Audio is enabled only when RECORD_AUDIO is granted.
    fun toggleRecording() {
        val active = recording
        if (active != null) {
            active.stop()
            return
        }
        val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(mediaValues("VID", "video/mp4"))
            .build()
        val audio = if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            AudioConfig.create(true)
        } else {
            AudioConfig.AUDIO_DISABLED
        }
        recording = controller.startRecording(options, audio, mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status ->
                    recordedMs = TimeUnit.NANOSECONDS.toMillis(event.recordingStats.recordedDurationNanos)
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recordedMs = 0
                    if (event.hasError()) {
                        toast(R.string.camera_capture_failed)
                    } else {
                        lastCapture = Capture(event.outputResults.outputUri, "video/mp4")
                    }
                }
                else -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (shutterFlash) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.6f)),
            )
        }

        // Top: flash for photos, timer while recording.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                recording != null -> Text(
                    text = "● " + formatElapsed(recordedMs),
                    color = RecordingRed,
                    style = MaterialTheme.typography.titleMedium,
                )
                mode == CaptureMode.PHOTO && backCamera -> FlashButton(flashMode) { flashMode = nextFlashMode(flashMode) }
            }
        }

        // Bottom: mode switch, thumbnail, shutter, camera switch.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (request == null && recording == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(stringResource(R.string.camera_mode_photo), mode == CaptureMode.PHOTO) { mode = CaptureMode.PHOTO }
                    ModeButton(stringResource(R.string.camera_mode_video), mode == CaptureMode.VIDEO) { mode = CaptureMode.VIDEO }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(lastCapture) { capture ->
                    context.startActivitySafely(MesOSApps.viewInPhotos(context, capture.uri, capture.mimeType))
                }
                ShutterButton(
                    video = mode == CaptureMode.VIDEO,
                    recording = recording != null,
                    enabled = !capturing,
                    onClick = { if (mode == CaptureMode.PHOTO) takePhoto() else toggleRecording() },
                )
                IconButton(
                    onClick = { backCamera = !backCamera },
                    enabled = recording == null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_switch_camera),
                        contentDescription = stringResource(R.string.camera_switch),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashButton(flashMode: Int, onClick: () -> Unit) {
    val (icon, label) = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on to R.string.camera_flash_on
        ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_on to R.string.camera_flash_auto
        else -> R.drawable.ic_flash_off to R.string.camera_flash_off
    }
    TextButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White)
        Text(stringResource(label), color = Color.White, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Thumbnail(capture: Capture?, onOpen: (Capture) -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .then(if (capture != null) Modifier.clickable { onOpen(capture) } else Modifier),
    ) {
        if (capture != null) {
            AsyncImage(
                model = capture.uri,
                contentDescription = stringResource(R.string.camera_last_capture),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ShutterButton(video: Boolean, recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        when {
            recording -> R.string.camera_stop_recording
            video -> R.string.camera_start_recording
            else -> R.string.camera_take_photo
        },
    )
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(BorderStroke(4.dp, Color.White), CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val (size, shape, color) = when {
            recording -> Triple(30.dp, RoundedCornerShape(6.dp), RecordingRed)
            video -> Triple(60.dp, CircleShape, RecordingRed)
            else -> Triple(62.dp, CircleShape, if (enabled) Color.White else Color.Gray)
        }
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(color),
        )
    }
}

private fun nextFlashMode(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
    else -> ImageCapture.FLASH_MODE_OFF
}

/** MediaStore entry in DCIM/MesOS, e.g. "MesOS_IMG_20260926_201530". */
private fun mediaValues(kind: String, mimeType: String): ContentValues = ContentValues().apply {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    put(MediaStore.MediaColumns.DISPLAY_NAME, "MesOS_${kind}_$stamp")
    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/MesOS")
    }
}

/** Small, upright bitmap for ACTION_IMAGE_CAPTURE callers that did not pass EXTRA_OUTPUT. */
private fun thumbnail(image: ImageProxy): Bitmap {
    val source = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    val scale = MAX_RESULT_THUMBNAIL_PX.toFloat() / maxOf(source.width, source.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotation != 0) postRotate(rotation.toFloat())
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
