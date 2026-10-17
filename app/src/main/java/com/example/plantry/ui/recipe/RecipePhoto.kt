package com.example.plantry.ui.recipe

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
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

/**
 * The recipe's photos side by side, each [PHOTO_CARD_HEIGHT] tall and as wide as its own shape, so
 * neither a portrait nor a landscape page leaves empty bars; a panorama wider than the screen is
 * cropped at the sides. A tap opens [onOpen] with the photo's index.
 */
@Composable
fun PhotoRoll(photos: List<ByteArray>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxCardWidth = maxWidth - 32.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(photos) { index, bytes -> PhotoCard(bytes, maxCardWidth) { onOpen(index) } }
        }
    }
}

@Composable
private fun PhotoCard(bytes: ByteArray, maxWidth: Dp, onClick: () -> Unit) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
    val width = (PHOTO_CARD_HEIGHT * bitmap.width.toFloat() / bitmap.height).coerceAtMost(maxWidth)
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.recipe_photo),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width, PHOTO_CARD_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = stringResource(R.string.photo_enlarge), onClick = onClick),
    )
}

private val PHOTO_CARD_HEIGHT = 200.dp

/**
 * The photo over the whole screen, bottom bar included, on black: pinch or double-tap to zoom. The
 * close button and Back dismiss it.
 */
@Composable
fun PhotoViewer(bytes: ByteArray, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Light status bar icons on the black background.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomablePhoto(bytes, Modifier.fillMaxSize())
            IconButton(
                onClick = onClose,
                modifier = Modifier.systemBarsPadding().padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close))
            }
        }
    }
}

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
