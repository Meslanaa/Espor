package org.mesos.photos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Photos: gallery of all photos and videos, and viewer for media opened by other apps. */
class PhotosActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installImageLoader()

        val viewUri = if (intent.action == Intent.ACTION_VIEW) intent.data else null
        val viewType = intent.type
        setContent {
            MesOSUserTheme {
                PhotosApp(viewUri = viewUri, viewType = viewType, onExit = ::finish)
            }
        }
    }
}

/** Coil loader that can also draw video thumbnails. */
internal fun installImageLoader() {
    SingletonImageLoader.setSafe { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
}
