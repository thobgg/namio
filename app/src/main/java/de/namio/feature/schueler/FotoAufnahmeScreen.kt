package de.namio.feature.schueler

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import androidx.camera.lifecycle.awaitInstance

@Composable
fun FotoAufnahmeScreen(
    onZurueck: () -> Unit,
    viewModel: FotoAufnahmeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var berechtigt by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val berechtigungsAnfrage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        berechtigt = it
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.speichereAusUri(uri)
    }

    LaunchedEffect(Unit) {
        if (!berechtigt) berechtigungsAnfrage.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state.fertig) {
        if (state.fertig) onZurueck()
    }

    FotoAufnahmeInhalt(
        state = state,
        berechtigt = berechtigt,
        onZurueck = onZurueck,
        onBerechtigungAnfragen = { berechtigungsAnfrage.launch(Manifest.permission.CAMERA) },
        onKameraWechseln = viewModel::kameraWechseln,
        onAufgenommen = viewModel::speichere,
        onGalerie = { galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onFehlerGesehen = viewModel::fehlerGesehen,
        onUeberspringen = viewModel::ueberspringen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FotoAufnahmeInhalt(
    state: FotoAufnahmeUiState,
    berechtigt: Boolean,
    onZurueck: () -> Unit,
    onBerechtigungAnfragen: () -> Unit,
    onKameraWechseln: () -> Unit,
    onAufgenommen: (ByteArray, Int) -> Unit,
    onGalerie: () -> Unit,
    onFehlerGesehen: () -> Unit,
    onUeberspringen: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val fehlerText = stringResource(R.string.foto_fehler)
    LaunchedEffect(state.fehler) {
        if (state.fehler) {
            snackbar.showSnackbar(fehlerText)
            onFehlerGesehen()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val aktuell = state.aktuell
                    if (state.runde && aktuell != null) {
                        Column {
                            Text("${aktuell.vorname} ${aktuell.nachname}", maxLines = 1)
                            Text(
                                stringResource(R.string.foto_runde_fortschritt, state.position, state.gesamt),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        Text(stringResource(R.string.foto_titel))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
                actions = {
                    if (state.runde) {
                        IconButton(onClick = onUeberspringen, enabled = !state.speichert) {
                            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.foto_runde_ueberspringen))
                        }
                    }
                    if (berechtigt) {
                        IconButton(onClick = onKameraWechseln) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.foto_kamera_wechseln))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        Column(
            Modifier.fillMaxSize().padding(innen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.runde && state.gesamt > 0) {
                LinearProgressIndicator(
                    progress = { (state.position - 1).coerceAtLeast(0) / state.gesamt.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (berechtigt) {
                KameraSucher(
                    frontkamera = state.frontkamera,
                    speichert = state.speichert,
                    imageCapture = imageCapture,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.foto_hinweis),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Box(Modifier.weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.foto_berechtigung_fehlt), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onBerechtigungAnfragen) {
                            Text(stringResource(R.string.foto_berechtigung_anfragen))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (berechtigt) {
                    FilledIconButton(
                        onClick = { ausloesen(context, imageCapture, onAufgenommen) },
                        enabled = !state.speichert,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.foto_ausloesen))
                    }
                }
                OutlinedButton(onClick = onGalerie, enabled = !state.speichert) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.foto_aus_galerie))
                }
            }
        }
    }
}

private fun ausloesen(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onAufgenommen: (ByteArray, Int) -> Unit,
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val puffer = image.planes[0].buffer
                val bytes = ByteArray(puffer.remaining()).also { puffer.get(it) }
                val rotation = image.imageInfo.rotationDegrees
                image.close()
                onAufgenommen(bytes, rotation)
            }

            override fun onError(exception: ImageCaptureException) = Unit
        },
    )
}

@Composable
private fun KameraSucher(
    frontkamera: Boolean,
    speichert: Boolean,
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView statt SurfaceView, damit die Vorschau sauber am Rand beschnitten wird.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(frontkamera) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val selector = if (frontkamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        provider.unbindAll()
        runCatching { provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture) }
    }

    Box(modifier.clipToBounds()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Hilfsrahmen(Modifier.fillMaxSize())
        if (speichert) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

/** Quadratischer Ausschnitt plus ovale Gesichtshilfslinie über dem Sucher. */
@Composable
private fun Hilfsrahmen(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val kante = minOf(size.width, size.height)
        val links = (size.width - kante) / 2f
        val oben = (size.height - kante) / 2f
        val abdunkeln = Color.Black.copy(alpha = 0.5f)
        // Bereiche außerhalb des Quadrats abdunkeln
        drawRect(abdunkeln, size = Size(size.width, oben))
        drawRect(abdunkeln, topLeft = Offset(0f, oben + kante), size = Size(size.width, size.height - oben - kante))
        drawRect(abdunkeln, topLeft = Offset(0f, oben), size = Size(links, kante))
        drawRect(abdunkeln, topLeft = Offset(links + kante, oben), size = Size(size.width - links - kante, kante))
        drawRect(Color.White, topLeft = Offset(links, oben), size = Size(kante, kante), style = Stroke(3f))
        // Gesichtsoval: ~55 % breit, ~75 % hoch, leicht nach oben versetzt
        val ovalBreite = kante * 0.55f
        val ovalHoehe = kante * 0.75f
        drawOval(
            Color.White.copy(alpha = 0.8f),
            topLeft = Offset(links + (kante - ovalBreite) / 2f, oben + kante * 0.10f),
            size = Size(ovalBreite, ovalHoehe),
            style = Stroke(2f),
        )
    }
}
