package eu.lopaciuk.covidcertificateverifier

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.Barcode
import eu.lopaciuk.covidcertificateverifier.ui.theme.CovidCertificateVerifierTheme
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.concurrent.Executors


@androidx.camera.core.ExperimentalGetImage
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var toast: Toast? = null

        setContent {
            val scaffoldState = rememberScaffoldState()
            var isProcessing by mutableStateOf(0)
            val context = this.applicationContext

            CovidCertificateVerifierTheme {
                // A surface container using the 'background' color from the theme
                Scaffold(
                    scaffoldState = scaffoldState,
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AppBar(
                            scaffoldState,
                            isProcessing = isProcessing == 1,
                            onRefresh = {
                                isProcessing = 1
                                onRefreshKeysClicked {
                                    isProcessing = 0
                                }
                            }
                        )
                    },
                    drawerContent = {
                        KeySummary(isProcessing)
                    }
                ) {
                    QRScannerComponent(
                        successListener = { handleBarcodes(it) },
                        failureListener = {
                            toast?.cancel()
                            toast = Toast.makeText(
                                this,
                                "Error: ${it.localizedMessage}",
                                Toast.LENGTH_SHORT
                            )
                            toast?.show()
                        }
                    )

                }
            }
        }
    }

    private fun onRefreshKeysClicked(onDone: () -> Unit) {
        val appContext = this.applicationContext
        Executors.newSingleThreadExecutor().execute {
            KeyDatabaseHelper(appContext).updateKeys()
            onDone()
        }
    }

    private fun handleBarcodes(barcodes: MutableList<Barcode>): Boolean {
        barcodes.removeIf { !it.rawValue!!.startsWith("HC1:") }

        if (barcodes.size > 0) {
            val resultsIntent = Intent(
                this, ResultsActivity::class.java
            ).apply {
                putExtra("qrData", barcodes.map { it.rawValue!!.substring(4) }.toTypedArray())
            }
            startActivity(resultsIntent)
            return true
        }
        return false
    }
}

@Composable
fun AppBar(scaffoldState: ScaffoldState, isProcessing: Boolean, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val modifier: Modifier

    if (isProcessing) {
        val animation = rememberInfiniteTransition()
        val angle = animation.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            )
        )
        modifier = Modifier.rotate(angle.value)
    } else {
        modifier = Modifier.rotate(0f)
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    )
    {
        IconButton(onClick = { scope.launch { scaffoldState.drawerState.open() }}) {
            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Drawer")
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = "Refresh signing keys",
                modifier = modifier
            )
        }
    }
}

@Composable
fun KeySummary(isProcessing: Int) {
    val appContext = LocalContext.current.applicationContext

    val keys = produceState(initialValue = listOf<ByteArray>(), isProcessing) {
        Executors.newSingleThreadExecutor().execute {
            value = KeyDatabaseHelper(appContext).getAllKids()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Stored public keys (${keys.value.size})",
            style = MaterialTheme.typography.subtitle1
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        for (kid in keys.value) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.padding(
                        vertical = 4.dp
                    ),
                    text = byteArrayToHexString(kid)!!,
                    fontFamily = FontFamily.Monospace
                )

                if (Charset.forName("ASCII").newEncoder().canEncode(String(kid))) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "(${String(kid)})")
                }
            }
        }
    }

}