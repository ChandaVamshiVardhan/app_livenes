package com.biosdk.livenessdetection.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import com.biosdk.livenessdetection.viewmodel.ViewModelFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biosdk.livenessdetection.ui.theme.*
import com.biosdk.livenessdetection.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraScreen"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(
        factory = ViewModelFactory(LocalContext.current)
    )
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val uiState by viewModel.uiState.collectAsState()
    
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var cameraExecutor by remember { mutableStateOf<ExecutorService?>(null) }
    
    // Initialize camera executor
    LaunchedEffect(Unit) {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor?.shutdown()
        }
    }
    
    if (!cameraPermissionState.status.isGranted) {
        // Permission request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, DarkSurface)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Text("Grant Camera Permission")
            }
        }
        return
    }
    
    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            )
    ) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        Log.d(TAG, "Initializing camera provider")
                        cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder()
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        
                        imageAnalysis = ImageAnalysis.Builder()
                            .setTargetRotation(previewView.display.rotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .build()
                        
                        imageAnalysis?.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            if (uiState.isRecording) {
                                try {
                                    processImageProxy(imageProxy, viewModel)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing image", e)
                                }
                            }
                            imageProxy.close()
                        }
                        
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build()
                        
                        Log.d(TAG, "Binding camera use cases")
                        cameraProvider?.unbindAll()
                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        Log.d(TAG, "Camera setup completed")
                    } catch (exc: Exception) {
                        Log.e(TAG, "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
        
        // Controls Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = if (uiState.isRecording) "Recording..." else "Ready to Record",
                color = Color.White,
                fontSize = 20.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Frames: ${uiState.frameCount}",
                color = Color.White
            )
            
            Text(
                text = "Liveness Score: ${uiState.livenessScore}%",
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (uiState.isRecording) {
                        viewModel.stopRecording()
                    } else {
                        viewModel.startRecording()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecording) ErrorRed else PrimaryBlue
                ),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isRecording) "Stop Recording" else "Start Recording",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun processImageProxy(imageProxy: ImageProxy, viewModel: CameraViewModel) {
    val image = imageProxy.image
    if (image != null) {
        try {
            Log.d(TAG, "Processing frame: ${image.width}x${image.height}")
            
            // Get the Y plane buffer
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            // Copy Y plane
            yBuffer.get(nv21, 0, ySize)
            
            // Copy UV planes
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                image.width,
                image.height,
                null
            )
            
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height),
                90,
                out
            )
            
            val jpegData = out.toByteArray()
            Log.d(TAG, "Compressed frame size: ${jpegData.size} bytes")
            viewModel.sendFrame(jpegData)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        }
    }
}