package eu.lopaciuk.covidcertificateverifier

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.*


@Composable
@androidx.camera.core.ExperimentalGetImage
fun QRScannerComponent(
    successListener: (barcodes: MutableList<Barcode>) -> Boolean,
    failureListener: (Exception) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(LocalContext.current)
    var isPermissionGranted by remember {
        mutableStateOf<Boolean?>(null)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            isGranted -> isPermissionGranted = isGranted
    }

    when (isPermissionGranted) {
        true -> CameraPreview(cameraProviderFuture, successListener, failureListener)
        false -> Center {
            Text(
                text = "Camera permission needs to be granted to scan QR codes.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h6
            )
            Spacer(modifier = Modifier.height(24.dp))
            val context = LocalContext.current
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri: Uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                context.startActivity(intent)
                isPermissionGranted = null
            }) {
                Text(
                    text = "Open app settings..."
                )
            }
        }
        null -> Center {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Start scanning"
                )
            }
        }
    }
}

@Composable
fun Center(composable: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        composable()
    }
}

@androidx.camera.core.ExperimentalGetImage
fun bindPreview(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    successListener: (barcodes: MutableList<Barcode>) -> Boolean,
    failureListener: (Exception) -> Unit
) {
    val preview: Preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .build()

    val cameraSelector: CameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()

    preview.setSurfaceProvider(previewView.surfaceProvider)

    val imageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(1280, 720))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    val queue: BlockingQueue<Runnable> = ArrayBlockingQueue(10)
    val executor: Executor = ThreadPoolExecutor(1,1, 0L, TimeUnit.MILLISECONDS, queue)

    val imageAnalyzer = QRCodeImageAnalyzer(successListener = { barcodes ->
        if (barcodes.size > 0) {
            var codesAccepted = successListener(barcodes)

            // After we accept the code, we purge the queue and disconnect the
            // analyzer to avoid passing the results multiple times
            if (codesAccepted) {
                imageAnalysis.clearAnalyzer()
                queue.clear()
            } else {
                failureListener(java.lang.Exception("This is not a valid certificate code"))
            }
        }
    }, failureListener)

    lifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun setAnalyzerOnResume() {
            // Rebind the analyser on resume, as we disconnect it on positive result
            // to avoid registering the same barcode multiple times
            imageAnalysis.setAnalyzer(executor, imageAnalyzer)
        }
    })

    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis, preview)
}

@Composable
@androidx.camera.core.ExperimentalGetImage
fun CameraPreview(
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    successListener: (barcodes: MutableList<Barcode>) -> Boolean,
    failureListener: (Exception) -> Unit
) {

    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                setBackgroundColor(android.graphics.Color.GREEN)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_START
                post {
                    cameraProviderFuture.addListener(Runnable {
                        val cameraProvider = cameraProviderFuture.get()
                        bindPreview(
                            cameraProvider,
                            lifecycleOwner,
                            this,
                            successListener,
                            failureListener
                        )
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        }
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .offset(y = -(70.dp))
                .size(280.dp)
                .border(width = 3.dp, color = Color.White.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
        )
    }
}


@androidx.camera.core.ExperimentalGetImage
private class QRCodeImageAnalyzer(
    val successListener: (barcodes: MutableList<Barcode>) -> Unit,
    val failureListener: (Exception) -> Unit
) : ImageAnalysis.Analyzer {

    private var options: BarcodeScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient(options)
            val result = scanner.process(image)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener)
                .addOnCompleteListener { imageProxy.close() }
        }
    }
}