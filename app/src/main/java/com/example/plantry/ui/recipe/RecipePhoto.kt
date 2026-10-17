package com.example.plantry.ui.recipe

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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
 * (so a parent can still scroll), double-tap to zoom in or reset. [onZoomedChange] tells whether it
 * is zoomed in.
 */
@Composable
fun ZoomablePhoto(bytes: ByteArray, modifier: Modifier = Modifier, onZoomedChange: (Boolean) -> Unit = {}) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
    var scale by remember(bytes) { mutableFloatStateOf(1f) }
    var offset by remember(bytes) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val zoomed = scale > 1f
    LaunchedEffect(zoomed) { onZoomedChange(zoomed) }

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
 * cropped at the sides. A tap opens [onOpen] with the photo's index. With [onAdd], a "Foto
 * hinzufügen" tile ends the roll.
 */
@Composable
fun PhotoRoll(
    photos: List<ByteArray>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    onAdd: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxCardWidth = maxWidth - 32.dp
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(photos) { index, bytes -> PhotoCard(bytes, maxCardWidth) { onOpen(index) } }
            if (onAdd != null) item { AddPhotoTile(onAdd) }
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

/** A bordered empty card, about the shape of a portrait page, to take or pick another photo. */
@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .size(ADD_TILE_WIDTH, PHOTO_CARD_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.photo_add),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val PHOTO_CARD_HEIGHT = 200.dp
private val ADD_TILE_WIDTH = 150.dp

/**
 * The photos over the whole screen, bottom bar included, on black, starting at [page]: swipe for
 * the next page, pinch or double-tap to zoom; while zoomed a drag pans instead. Dots at the bottom
 * show the page. The close button and Back dismiss it; [onPageChange] follows the swipes.
 */
@Composable
fun PhotoViewer(photos: List<ByteArray>, page: Int, onPageChange: (Int) -> Unit, onClose: () -> Unit) {
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
        val pager = rememberPagerState(initialPage = page) { photos.size }
        LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(onPageChange) }
        // Only the page shown can be zoomed, as a page can't be left while zoomed.
        var zoomed by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { index ->
                ZoomablePhoto(photos[index], Modifier.fillMaxSize(), onZoomedChange = { if (index == pager.currentPage) zoomed = it })
            }
            if (photos.size > 1) {
                PageDots(
                    count = photos.size,
                    current = pager.currentPage,
                    modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp),
                )
            }
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

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.photo_page, current + 1, count)
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Color.White else Color.White.copy(alpha = 0.4f)),
            )
        }
    }
}

/** Camera or gallery for a page photo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onTakePhoto: () -> Unit, onPickPhoto: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.photo_take)) },
                leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onTakePhoto),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.photo_pick)) },
                leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onPickPhoto),
            )
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
