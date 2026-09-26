package org.mesos.camera

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import org.mesos.core.ui.theme.MesOSTheme

/** MesOS Camera. Also answers other apps' "take a picture" requests. */
class CameraActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The viewfinder is always dark, whatever the MesOS appearance.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build()
        }
        val request = CaptureRequest.from(intent)
        setContent {
            MesOSTheme(darkTheme = true) {
                CameraApp(
                    request = request,
                    onResult = { code, data ->
                        setResult(code, data)
                        finish()
                    },
                )
            }
        }
    }
}

/** Another app asked for a photo (ACTION_IMAGE_CAPTURE), optionally into [output]. */
internal data class CaptureRequest(val output: Uri?) {
    companion object {
        fun from(intent: Intent): CaptureRequest? {
            if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
            val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
            }
            return CaptureRequest(output)
        }
    }
}
