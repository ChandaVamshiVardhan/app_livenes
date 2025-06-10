package com.biosdk.livenessdetection.viewmodel

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment

data class CameraUiState(
    val isRecording: Boolean = false,
    val frameCount: Int = 0,
    val livenessScore: Int = 0,
    val timeLeft: Int = 15,
    val isConnected: Boolean = false,
    val error: String? = null,
    val savedFilePath: String? = null,
    val faceDetected: Boolean = false,
    val faceConfidence: Float = 0f,
    val sessionState: SessionState = SessionState.WAITING_FOR_FACE,
    val currentFps: Double = 0.0
)

enum class SessionState {
    WAITING_FOR_FACE,
    FACE_DETECTED,
    SESSION_STARTING,
    SESSION_ACTIVE,
    SESSION_COMPLETED
}

class CameraViewModel(private val context: Context) : ViewModel() {
    private val TAG = "CameraViewModel"
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    private var webSocket: WebSocket? = null
    private var sessionId: String? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var framesSentCount = 0
    private var lastFpsCalculation = System.currentTimeMillis()
    private var faceDetectionCount = 0
    private val requiredFaceDetections = 10 // Require 10 consecutive face detections before starting session
    
    init {
        Log.d(TAG, "Initializing CameraViewModel")
    }

    fun onFaceDetected(detected: Boolean, confidence: Float) {
        if (detected && confidence > 0.7f) {
            faceDetectionCount++
            _uiState.update { it.copy(
                faceDetected = true,
                faceConfidence = confidence
            )}
            
            // Start session after consistent face detection
            if (faceDetectionCount >= requiredFaceDetections && 
                _uiState.value.sessionState == SessionState.WAITING_FOR_FACE) {
                Log.d(TAG, "Face consistently detected, starting session...")
                _uiState.update { it.copy(sessionState = SessionState.FACE_DETECTED) }
                startSession()
            }
        } else {
            faceDetectionCount = 0
            _uiState.update { it.copy(
                faceDetected = false,
                faceConfidence = confidence
            )}
            
            // Reset session if face is lost during recording
            if (_uiState.value.sessionState == SessionState.SESSION_ACTIVE && !detected) {
                Log.d(TAG, "Face lost during session, stopping...")
                stopRecording()
            }
        }
    }

    private fun startSession() {
        Log.d(TAG, "Starting new session...")
        _uiState.update { it.copy(sessionState = SessionState.SESSION_STARTING) }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://biosdk.credissuer.com:8001/start-session")
                    .post(RequestBody.create("application/json".toMediaType(), ""))
                    .header("accept", "application/json")
                    .build()

                Log.d(TAG, "Making HTTP request to start session")
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(response.body?.string())
                        sessionId = jsonResponse.getString("session_id")
                        Log.d(TAG, "Session started successfully with ID: $sessionId")
                        withContext(Dispatchers.Main) {
                            connectWebSocket()
                        }
                    } else {
                        Log.e(TAG, "Failed to start session: ${response.code}")
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(
                                error = "Failed to start session",
                                sessionState = SessionState.WAITING_FOR_FACE
                            )}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting session", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        error = "Error starting session: ${e.message}",
                        sessionState = SessionState.WAITING_FOR_FACE
                    )}
                }
            }
        }
    }

    private fun connectWebSocket() {
        if (sessionId == null) {
            Log.d(TAG, "Cannot connect WebSocket without session ID")
            return
        }
        
        Log.d(TAG, "Connecting to WebSocket...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Close existing WebSocket if any
                webSocket?.close(1000, "Reconnecting")
                
                val request = Request.Builder()
                    .url("ws://biosdk.credissuer.com:8001/ws/process/${sessionId}")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connection opened")
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update { it.copy(
                                isConnected = true,
                                sessionState = SessionState.SESSION_ACTIVE
                            )}
                            startRecording()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            Log.d(TAG, "Received WebSocket message: $text")
                            val jsonResponse = JSONObject(text)
                            if (jsonResponse.has("status") && jsonResponse.getString("status") == "completed") {
                                Log.d(TAG, "Session completed, getting results")
                                viewModelScope.launch(Dispatchers.Main) {
                                    _uiState.update { it.copy(sessionState = SessionState.SESSION_COMPLETED) }
                                }
                                getSessionResults()
                            } else {
                                viewModelScope.launch(Dispatchers.Main) {
                                    _uiState.update { state ->
                                        state.copy(
                                            livenessScore = jsonResponse.optInt("liveness_score", state.livenessScore),
                                            frameCount = jsonResponse.optInt("frame_number", state.frameCount),
                                            currentFps = jsonResponse.optDouble("current_fps", state.currentFps)
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing WebSocket message", e)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket error: ${t.message}", t)
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update { it.copy(
                                isConnected = false, 
                                error = "WebSocket error: ${t.message}",
                                sessionState = SessionState.WAITING_FOR_FACE
                            )}
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update { it.copy(isConnected = false) }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to WebSocket", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        error = "Error connecting to WebSocket: ${e.message}",
                        sessionState = SessionState.WAITING_FOR_FACE
                    )}
                }
            }
        }
    }
    
    private fun startRecording() {
        Log.d(TAG, "Starting 15-second recording...")
        _uiState.update { it.copy(isRecording = true, timeLeft = 15) }
        framesSentCount = 0
        lastFpsCalculation = System.currentTimeMillis()
        
        viewModelScope.launch {
            for (i in 15 downTo 0) {
                if (!_uiState.value.isRecording) break
                _uiState.update { it.copy(timeLeft = i) }
                if (i == 0) {
                    Log.d(TAG, "Recording time completed, stopping...")
                    stopRecording()
                }
                delay(1000)
            }
        }
    }
    
    fun stopRecording() {
        Log.d(TAG, "Stopping recording...")
        _uiState.update { it.copy(isRecording = false) }
        webSocket?.send("stop")
        Log.d(TAG, "Stop signal sent to WebSocket")
    }

    fun resetSession() {
        Log.d(TAG, "Resetting session...")
        stopRecording()
        webSocket?.close(1000, "Session reset")
        sessionId = null
        faceDetectionCount = 0
        framesSentCount = 0
        _uiState.update { 
            CameraUiState(sessionState = SessionState.WAITING_FOR_FACE)
        }
    }

    private fun getSessionResults() {
        Log.d(TAG, "Getting session results...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(2000) // Give server time to process and save results
                
                val request = Request.Builder()
                    .url("http://biosdk.credissuer.com:8001/session/${sessionId}/results")
                    .header("accept", "application/json")
                    .build()

                Log.d(TAG, "Making HTTP request to get results")
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val fileName = response.header("content-disposition")
                            ?.substringAfter("filename=")
                            ?.trim('"')
                            ?: "LivenessResults_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
                        
                        Log.d(TAG, "Results retrieved successfully. Saving file: $fileName")

                        val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs()
                        }
                        
                        val file = File(downloadsDir, fileName)
                        file.outputStream().use { fos ->
                            response.body?.bytes()?.let { bytes ->
                                fos.write(bytes)
                            }
                        }
                                
                        Log.d(TAG, "Excel file saved successfully at: ${file.absolutePath}")
                        makeFileVisibleInDownloads(file)
                        
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(savedFilePath = file.absolutePath) }
                        }
                        
                        webSocket?.close(1000, "Session completed")
                        delay(1000)
                        stopSession()
                    } else if (response.code == 400) {
                        Log.d(TAG, "Processing not completed yet, will retry...")
                        delay(2000)
                        getSessionResults()
                    } else {
                        Log.e(TAG, "Failed to get session results: ${response.code}")
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(error = "Failed to get session results") }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting session results", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = "Error getting session results: ${e.message}") }
                }
            }
        }
    }

    private fun stopSession() {
        Log.d(TAG, "Stopping session...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                webSocket?.close(1000, "Session stopped")
                
                val request = Request.Builder()
                    .url("http://biosdk.credissuer.com:8001/stop-session/${sessionId}?keep=true")
                    .post(RequestBody.create("application/json".toMediaType(), ""))
                    .header("accept", "application/json")
                    .build()

                Log.d(TAG, "Making HTTP request to stop session")
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(response.body?.string())
                        val message = jsonResponse.getString("message")
                        Log.d(TAG, "Session stopped successfully: $message")
                    } else {
                        Log.e(TAG, "Failed to stop session: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping session", e)
            }
        }
    }

    fun sendFrame(frameData: ByteArray) {
        if (_uiState.value.isRecording && sessionId != null && _uiState.value.isConnected) {
            try {
                val base64Frame = android.util.Base64.encodeToString(frameData, android.util.Base64.NO_WRAP)
                val sent = webSocket?.send(base64Frame) ?: false
                
                if (sent) {
                    framesSentCount++
                    
                    // Calculate FPS every second
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFpsCalculation >= 1000) {
                        val fps = framesSentCount.toDouble() / ((currentTime - lastFpsCalculation) / 1000.0)
                        lastFpsCalculation = currentTime
                        framesSentCount = 0
                        
                        _uiState.update { it.copy(currentFps = fps) }
                        Log.d(TAG, "Current FPS: $fps")
                    }
                } else {
                    Log.e(TAG, "Failed to send frame")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending frame", e)
            }
        }
    }
    
    private fun makeFileVisibleInDownloads(file: File) {
        try {
            val targetFile = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val itemUri = resolver.insert(collection, contentValues)

                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { os ->
                        file.inputStream().use { it.copyTo(os) }
                    }
                }
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.name)
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadDir, file.name)
                file.copyTo(targetFile, overwrite = true)
                targetFile
            }
            
            Log.d(TAG, "File made visible in Downloads: ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error making file visible in Downloads", e)
        }
    }
    
    override fun onCleared() {
        Log.d(TAG, "Clearing ViewModel resources")
        super.onCleared()
        webSocket?.close(1000, "ViewModel cleared")
        client.dispatcher.executorService.shutdown()
    }
}