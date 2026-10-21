package com.example.plantry.ui.ingredient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.openfoodfacts.OffLookup
import com.example.plantry.data.openfoodfacts.OffProduct
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** A barcode lookup in Open Food Facts: running, or finished with its result. */
sealed interface BarcodeLookup {
    data object Searching : BarcodeLookup
    data class Done(val result: OffLookup) : BarcodeLookup
}

/**
 * Returns a function that opens the Google Code Scanner (its own camera UI, no camera permission)
 * for product barcodes. Cancelling calls nothing; a scanner that cannot start calls [onUnavailable].
 */
@Composable
fun rememberBarcodeScanner(onScanned: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scanned by rememberUpdatedState(onScanned)
    val unavailable by rememberUpdatedState(onUnavailable)
    return remember(context) {
        {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let { scanned(it) } }
                .addOnFailureListener { e ->
                    // Leaving the scanner with back is reported as a failure, but it is no error.
                    if ((e as? MlKitException)?.errorCode != MlKitException.CANCELLED) unavailable()
                }
        }
    }
}

/** Shown while Open Food Facts is asked; dismissing it cancels the lookup. */
@Composable
fun BarcodeSearchingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.barcode_searching))
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/** "Gefunden: <Produkt>, <Menge>". */
@Composable
fun foundText(product: OffProduct): String = product.quantity
    ?.let { stringResource(R.string.barcode_found_quantity, product.productName, it) }
    ?: stringResource(R.string.barcode_found, product.productName)

/** The message for a lookup that found nothing. */
@Composable
fun failureText(result: OffLookup): String = stringResource(
    if (result is OffLookup.Offline) R.string.barcode_offline else R.string.barcode_not_found,
)
