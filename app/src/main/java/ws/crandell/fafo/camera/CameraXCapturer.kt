package ws.crandell.fafo.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX ImageCapture bound to a lifecycle (the armed LifecycleService, or the activity for the
 * self-test). No Preview use case (AC5.1). Output: JPEG, max 1600 px long edge, quality 80 (AC5.4).
 */
class CameraXCapturer(private val context: Context, private val owner: LifecycleOwner) : Capturer {
    private var provider: ProcessCameraProvider? = null
    private var bound: Pair<Lens, ImageCapture>? = null

    private suspend fun provider(): ProcessCameraProvider = provider ?: suspendCancellableCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({
            try {
                cont.resume(f.get().also { provider = it })
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun captureFor(lens: Lens): ImageCapture {
        bound?.let { if (it.first == lens) return it.second }
        val p = provider()
        val selector = if (lens == Lens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val ic = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        withContext(Dispatchers.Main) {
            p.unbindAll()
            p.bindToLifecycle(owner, selector, ic)
        }
        bound = lens to ic
        return ic
    }

    override suspend fun capture(lens: Lens): CapturedPhoto? {
        val ic = try {
            captureFor(lens)
        } catch (e: Exception) {
            // Lens missing or camera busy: try the other one once.
            val other = if (lens == Lens.FRONT) Lens.BACK else Lens.FRONT
            runCatching { captureFor(other) }.getOrNull() ?: return null
        }
        val image: ImageProxy = suspendCancellableCoroutine { cont ->
            ic.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)
                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            })
        }
        return withContext(Dispatchers.Default) {
            image.use { img ->
                val rotated = rotate(img.toBitmap(), img.imageInfo.rotationDegrees)
                val scaled = scaleDown(rotated, MAX_EDGE)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                CapturedPhoto(out.toByteArray(), thumbnail(scaled))
            }
        }
    }

    fun release() {
        provider?.unbindAll()
        bound = null
    }

    companion object {
        const val MAX_EDGE = 1600
        const val QUALITY = 80

        fun rotate(b: Bitmap, degrees: Int): Bitmap {
            if (degrees == 0) return b
            val m = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        }

        fun scaleDown(b: Bitmap, maxEdge: Int): Bitmap {
            val edge = maxOf(b.width, b.height)
            if (edge <= maxEdge) return b
            val f = maxEdge.toFloat() / edge
            return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        }

        /** 32x32 grayscale (0..255) for big-change detection. */
        fun thumbnail(b: Bitmap): IntArray {
            val t = Bitmap.createScaledBitmap(b, BigChange.SIZE, BigChange.SIZE, true)
            val px = IntArray(BigChange.SIZE * BigChange.SIZE)
            t.getPixels(px, 0, BigChange.SIZE, 0, 0, BigChange.SIZE, BigChange.SIZE)
            return IntArray(px.size) {
                val c = px[it]
                (((c shr 16) and 0xff) * 299 + ((c shr 8) and 0xff) * 587 + (c and 0xff) * 114) / 1000
            }
        }
    }
}
