package org.mesos.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import org.mesos.core.log.MesOSLog
import java.io.IOException

/** A decoded code: its text and the kind of symbol it came from. */
data class DecodedCode(val text: String, val format: BarcodeFormat)

private val formats = listOf(
    BarcodeFormat.QR_CODE,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.AZTEC,
    BarcodeFormat.PDF_417,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.ITF,
)

private fun reader(tryHarder: Boolean) = MultiFormatReader().apply {
    val hints = mutableMapOf<DecodeHintType, Any>(
        DecodeHintType.POSSIBLE_FORMATS to formats,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )
    if (tryHarder) hints[DecodeHintType.TRY_HARDER] = true
    setHints(hints)
}

private fun MultiFormatReader.tryDecode(source: LuminanceSource, histogram: Boolean = false): Result? =
    try {
        val binarizer = if (histogram) GlobalHistogramBinarizer(source) else HybridBinarizer(source)
        decodeWithState(BinaryBitmap(binarizer))
    } catch (e: NotFoundException) {
        null
    } catch (e: ReaderException) {
        null
    } finally {
        reset()
    }

/**
 * Camera frames → codes. Uses only the luminance (Y) plane; tries the inverted
 * image too, for light-on-dark codes.
 */
internal class CodeAnalyzer(private val onCode: (DecodedCode) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = reader(tryHarder = false)

    @Volatile
    var paused = false

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            if (paused) return
            val plane = frame.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val rowStride = plane.rowStride
            val rows = if (rowStride > 0) (bytes.size + rowStride - 1) / rowStride else 0
            if (rowStride < frame.width || rows < frame.height) return
            // Pad to a whole number of rows so ZXing can index every row safely.
            val data = if (bytes.size == rowStride * rows) bytes else bytes.copyOf(rowStride * rows)
            val source = try {
                PlanarYUVLuminanceSource(data, rowStride, rows, 0, 0, frame.width, frame.height, false)
            } catch (e: IllegalArgumentException) {
                return
            }
            val result = reader.tryDecode(source) ?: reader.tryDecode(source.invert()) ?: return
            if (result.text.isNullOrEmpty()) return
            paused = true
            onCode(DecodedCode(result.text, result.barcodeFormat))
        }
    }
}

/** Finds a code in a picture the user chose. Runs on a background thread. */
internal object ImageDecoderHelper {
    private const val MAX_SIDE = 1600

    fun decode(context: Context, uri: Uri): DecodedCode? {
        val bitmap = load(context, uri) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val reader = reader(tryHarder = true)
        val result = reader.tryDecode(source)
            ?: reader.tryDecode(source, histogram = true)
            ?: reader.tryDecode(source.invert())
            ?: return null
        return DecodedCode(result.text, result.barcodeFormat)
    }

    private fun load(context: Context, uri: Uri): Bitmap? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val largest = maxOf(info.size.width, info.size.height)
                    if (largest > MAX_SIDE) {
                        val scale = MAX_SIDE.toFloat() / largest
                        decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (e: IOException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Could not read image for scanning", e)
            null
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "No access to image for scanning", e)
            null
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Image could not be decoded for scanning", e)
            null
        }
}
