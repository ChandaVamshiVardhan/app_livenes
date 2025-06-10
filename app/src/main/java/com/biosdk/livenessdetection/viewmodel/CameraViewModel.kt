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
    val savedFilePath: String? = null
)

class CameraViewModel(private val context: Context) : ViewModel() {
    private val TAG = "CameraViewModel"
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    private var webSocket: WebSocket? = null
    private var sessionId: String? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    init {
        Log.d(TAG, "Initializing CameraViewModel")
        startSession()
    }

    private fun startSession() {
        Log.d(TAG, "Starting new session...")
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
                            _uiState.update { it.copy(error = "Failed to start session") }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting session", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = "Error starting session: ${e.message}") }
                }
            }
        }
    }

    private fun connectWebSocket() {
        if (sessionId == null) {
            Log.d(TAG, "Cannot connect WebSocket without session ID")
            startSession()
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
                            _uiState.update { it.copy(isConnected = true) }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            Log.d(TAG, "Received WebSocket message: $text")
                            val jsonResponse = JSONObject(text)
                            if (jsonResponse.has("status") && jsonResponse.getString("status") == "completed") {
                                Log.d(TAG, "Session completed, getting results")
                                getSessionResults()
                            } else {
                                viewModelScope.launch(Dispatchers.Main) {
                                    _uiState.update { state ->
                                        state.copy(
                                            livenessScore = jsonResponse.optInt("liveness_score", state.livenessScore),
                                            frameCount = jsonResponse.optInt("frame_number", state.frameCount)
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
                            _uiState.update { it.copy(isConnected = false, error = "WebSocket error: ${t.message}") }
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $reason")
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to WebSocket", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = "Error connecting to WebSocket: ${e.message}") }
                }
            }
        }
    }
    
    fun startRecording() {
        Log.d(TAG, "Starting recording...")
        if (sessionId == null) {
            Log.d(TAG, "No session ID, starting new session")
            startSession()
            return
        }
        
        if (!_uiState.value.isConnected) {
            Log.d(TAG, "WebSocket not connected, reconnecting")
            connectWebSocket()
            return
        }
        
        Log.d(TAG, "Starting recording with session ID: $sessionId")
        _uiState.update { it.copy(isRecording = true, timeLeft = 15) }
        viewModelScope.launch {
            for (i in 15 downTo 0) {
                if (!_uiState.value.isRecording) break
                _uiState.update { it.copy(timeLeft = i) }
                if (i == 0) {
                    Log.d(TAG, "Recording time completed, stopping...")
                    stopRecording()
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    fun stopRecording() {
        Log.d(TAG, "Stopping recording...")
        _uiState.update { it.copy(isRecording = false) }
        webSocket?.send("stop")
        Log.d(TAG, "Stop signal sent to WebSocket")
    }

    private fun getSessionStatus() {
        Log.d(TAG, "Getting session status...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://biosdk.credissuer.com:8001/session/${sessionId}")
                    .header("accept", "application/json")
                    .build()

                Log.d(TAG, "Making HTTP request to get session status")
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(response.body?.string())
                        val status = jsonResponse.getString("status")
                        val isActive = jsonResponse.getBoolean("is_active")
                        val frameCount = jsonResponse.getInt("frame_count")
                        val currentFps = jsonResponse.getDouble("current_fps")
                        
                        Log.d(TAG, "Session status: $status, Active: $isActive, Frames: $frameCount, FPS: $currentFps")
                        
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(
                                frameCount = frameCount,
                                isConnected = isActive
                            )}
                        }
                    } else {
                        Log.e(TAG, "Failed to get session status: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting session status", e)
            }
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
                        // Get filename from Content-Disposition header or generate one
                        val fileName = response.header("content-disposition")
                            ?.substringAfter("filename=")
                            ?.trim('"')
                            ?: "LivenessResults_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
                        
                        Log.d(TAG, "Results retrieved successfully. Saving file: $fileName")

                        // Create Downloads directory if it doesn't exist
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
                        
                        // Make file visible in Downloads
                        makeFileVisibleInDownloads(file)
                        
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(savedFilePath = file.absolutePath) }
                        }
                        
                        // Close WebSocket before stopping session
                        webSocket?.close(1000, "Session completed")
                        delay(1000) // Give WebSocket time to close
                        
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
                // Ensure WebSocket is closed
                webSocket?.close(1000, "Session stopped")
                
                val request = Request.Builder()
                    .url("http://biosdk.credissuer.com:8001/stop-session/${sessionId}?keep=true") // Changed to keep=true to preserve results
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
        if (_uiState.value.isRecording && sessionId != null) {
            if (!_uiState.value.isConnected) {
                Log.d(TAG, "WebSocket not connected, attempting to reconnect")
                connectWebSocket()
                return
            }
            
            try {
                // Convert frame data to base64 string
                val base64Frame = android.util.Base64.encodeToString(frameData, android.util.Base64.NO_WRAP)
                val sent = webSocket?.send(base64Frame) ?: false
                
                if (sent) {
                    _uiState.update { currentState ->
                        val newFrameCount = currentState.frameCount + 1
                        if (newFrameCount % 30 == 0) { // Log every 30 frames
                            Log.d(TAG, "Sent frame $newFrameCount")
                        }
                        currentState.copy(frameCount = newFrameCount)
                    }
                } else {
                    Log.e(TAG, "Failed to send frame")
                    _uiState.update { it.copy(error = "Failed to send frame") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending frame", e)
                _uiState.update { it.copy(error = "Error sending frame: ${e.message}") }
            }
        } else {
            if (!_uiState.value.isConnected) {
                Log.d(TAG, "WebSocket not connected, attempting to reconnect")
                connectWebSocket()
            }
            if (sessionId == null) {
                Log.d(TAG, "No session ID, starting new session")
                startSession()
            }
        }
    }
    
    private fun makeFileVisibleInDownloads(file: File) {
        try {
            val targetFile = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10 and above: Use MediaStore
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
                // Below Android 10: Direct file copy
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