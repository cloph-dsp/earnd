package com.earnd.app.photo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.earnd.app.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import com.earnd.app.ui.theme.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.earnd.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-screen camera capture. Saves a downscaled JPEG into files/photos and reports its file name.
 * Requests the camera permission when needed; nothing leaves the device.
 */
@Composable
fun PhotoCaptureScreen(
    onCaptured: (fileName: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { PhotoStore(context) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Surface(Modifier.fillMaxSize()) {
        when {
            granted -> CameraContent(store, onCaptured, onCancel)
            asked -> PermissionNeeded(
                onRetry = { launcher.launch(Manifest.permission.CAMERA) },
                onCancel = onCancel,
            )
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CameraContent(store: PhotoStore, onCaptured: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun capture() {
        busy = true
        failed = false
        val raw = File(context.cacheDir, "photo_raw_${System.nanoTime()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(raw).build()
        controller.takePicture(opts, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val name = store.newFileName()
                        val ok = withContext(Dispatchers.IO) {
                            val r = runCatching { PhotoProcessing.downscale(raw, store.path(name)) }
                            raw.delete()
                            r.isSuccess
                        }
                        busy = false
                        if (ok) onCaptured(name) else { store.delete(name); failed = true }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    raw.delete()
                    busy = false
                    failed = true
                }
            })
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text(stringResource(R.string.photo_failed), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy) {
                    Text(stringResource(R.string.photo_cancel))
                }
                Button(onClick = ::capture, enabled = !busy) {
                    Text(stringResource(if (busy) R.string.photo_saving else R.string.photo_take))
                }
            }
        }
    }
}

@Composable
private fun PermissionNeeded(onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.photo_permission_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.photo_permission_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.photo_cancel)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.photo_allow)) }
        }
    }
}
