package com.example.plantry.ui.recipe

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import com.example.plantry.R
import java.io.File

/**
 * The recipe photo, fitted into the available space. Pinch to zoom, drag to pan once zoomed
 * (so a parent can still scroll), double-tap to zoom in or reset.
 */
@Composable
fun ZoomablePhoto(bytes: ByteArray, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
    var scale by remember(bytes) { mutableFloatStateOf(1f) }
    var offset by remember(bytes) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun bounded(candidate: Offset, atScale: Float): Offset {
        val maxX = size.width * (atScale - 1) / 2
        val maxY = size.height * (atScale - 1) / 2
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = bounded(offset + pan, scale)
    }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(bytes) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        offset = Offset.Zero
                    },
                )
            }
            .transformable(state, canPan = { scale > 1f }),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.recipe_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Starts the camera or the system photo picker; the result arrives as a content [Uri]. */
class PhotoSource(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

@Composable
fun rememberPhotoSource(onPhoto: (Uri) -> Unit): PhotoSource {
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) onPhoto(captureUri(context))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhoto)
    }
    return remember(camera, picker) {
        PhotoSource(
            takePhoto = { camera.launch(captureUri(context)) },
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** The camera writes the full-size photo here; it is compressed before it is kept. */
private fun captureUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
