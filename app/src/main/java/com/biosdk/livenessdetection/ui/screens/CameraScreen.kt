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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biosdk.livenessdetection.ui.theme.*
import com.biosdk.livenessdetection.ui.components.*
import com.biosdk.livenessdetection.viewmodel.CameraViewModel
import com.biosdk.livenessdetection.viewmodel.SessionState
import com.biosdk.livenessdetection.utils.FaceDetector
import com.biosdk.livenessdetection.utils.VideoFrameExtractor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraScreen"
private const val TARGET_FPS = 25

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
    val scope = rememberCoroutineScope()
    
    val uiState by viewModel.uiState.collectAsState()
    
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var cameraExecutor by remember { mutableStateOf<ExecutorService?>(null) }
    
    // Initialize utilities
    val faceDetector = remember { FaceDetector() }
    val frameExtractor = remember { VideoFrameExtractor() }
    
    // Frame rate control
    var lastFrameTime by remember { mutableStateOf(0L) }
    val frameInterval = 1000L / TARGET_FPS // 40ms for 25 FPS
    
    // Initialize camera executor
    LaunchedEffect(Unit) {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            faceDetector.close()
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
                            val currentTime = System.currentTimeMillis()
                            
                            // Control frame rate to 25 FPS
                            if (currentTime - lastFrameTime >= frameInterval) {
                                lastFrameTime = currentTime
                                
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        // Always perform face detection
                                        val faceResult = faceDetector.detectFace(imageProxy)
                                        
                                        // Update face detection state
                                        launch(Dispatchers.Main) {
                                            viewModel.onFaceDetected(
                                                faceResult.faceDetected,
                                                faceResult.confidence
                                            )
                                        }
                                        
                                        // Send frame if recording
                                        if (uiState.isRecording) {
                                            frameExtractor.extractFrameAsJpeg(imageProxy, 90)?.let { jpegData ->
                                                viewModel.sendFrame(jpegData)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error processing image", e)
                                    }
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
        
        // Face Detection Overlay
        FaceDetectionOverlay(
            faceDetected = uiState.faceDetected,
            confidence = uiState.faceConfidence,
            sessionState = uiState.sessionState,
            modifier = Modifier.fillMaxSize()
        )
        
        // Top Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ConnectionStatus(
                isConnected = uiState.isConnected
            )
            
            if (uiState.isRecording) {
                RecordingIndicator(
                    timeLeft = uiState.timeLeft
                )
            }
        }
        
        // Bottom Stats and Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Statistics Cards
            if (uiState.sessionState == SessionState.SESSION_ACTIVE || uiState.sessionState == SessionState.SESSION_COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Frames",
                        value = uiState.frameCount.toString(),
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatCard(
                        title = "Liveness",
                        value = "${uiState.livenessScore}%",
                        color = if (uiState.livenessScore > 70) SuccessGreen else WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatCard(
                        title = "FPS",
                        value = String.format("%.1f", uiState.currentFps),
                        color = if (uiState.currentFps >= 20) SuccessGreen else WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Status Text
            Text(
                text = when (uiState.sessionState) {
                    SessionState.WAITING_FOR_FACE -> "Position your face to start"
                    SessionState.FACE_DETECTED -> "Face detected! Starting session..."
                    SessionState.SESSION_STARTING -> "Connecting to server..."
                    SessionState.SESSION_ACTIVE -> "Recording liveness data..."
                    SessionState.SESSION_COMPLETED -> "Session completed successfully!"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                if (uiState.sessionState == SessionState.SESSION_COMPLETED || uiState.error != null) {
                    Button(
                        onClick = { viewModel.resetSession() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SecondaryBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Session",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Session")
                    }
                }
                
                // Stop Button (only during active recording)
                if (uiState.isRecording) {
                    Button(
                        onClick = { viewModel.stopRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            // Error Display
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}