package com.biosdk.livenessdetection.data.websocket

import android.util.Log
import com.biosdk.livenessdetection.data.models.CompletionMessage
import com.biosdk.livenessdetection.data.models.LivenessResult
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    var onFrameProcessed: ((LivenessResult) -> Unit)? = null
    var onSessionComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onConnectionStatusChange: ((Boolean) -> Unit)? = null
    
    fun connect(sessionId: String) {
        val url = "ws://biosdk.credissuer.com:8001/ws/process/$sessionId"
        val request = Request.Builder()
            .url(url)
            .build()
        
        Log.d("WebSocketManager", "Connecting to: $url")
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketManager", "WebSocket connected")
                CoroutineScope(Dispatchers.Main).launch {
                    onConnectionStatusChange?.invoke(true)
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketManager", "Received message: $text")
                
                try {
                    // Try to parse as completion message first
                    val completionMessage = gson.fromJson(text, CompletionMessage::class.java)
                    if (completionMessage.status == "completed") {
                        Log.d("WebSocketManager", "Session completed")
                        CoroutineScope(Dispatchers.Main).launch {
                            onSessionComplete?.invoke()
                        }
                        return
                    }
                } catch (e: JsonSyntaxException) {
                    // Not a completion message, continue
                }
                
                try {
                    // Try to parse as liveness result
                    val result = gson.fromJson(text, LivenessResult::class.java)
                    CoroutineScope(Dispatchers.Main).launch {
                        onFrameProcessed?.invoke(result)
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e("WebSocketManager", "Error parsing message: $text", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        onError?.invoke("Error parsing server response")
                    }
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketManager", "WebSocket error", t)
                CoroutineScope(Dispatchers.Main).launch {
                    onConnectionStatusChange?.invoke(false)
                    onError?.invoke("WebSocket connection error: ${t.message}")
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketManager", "WebSocket closing: $code $reason")
                CoroutineScope(Dispatchers.Main).launch {
                    onConnectionStatusChange?.invoke(false)
                }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketManager", "WebSocket closed: $code $reason")
                CoroutineScope(Dispatchers.Main).launch {
                    onConnectionStatusChange?.invoke(false)
                }
            }
        })
    }
    
    fun sendFrame(base64Image: String) {
        webSocket?.let { ws ->
            if (ws.send(base64Image)) {
                Log.d("WebSocketManager", "Frame sent successfully")
            } else {
                Log.e("WebSocketManager", "Failed to send frame")
                onError?.invoke("Failed to send frame")
            }
        } ?: run {
            Log.e("WebSocketManager", "WebSocket not connected")
            onError?.invoke("WebSocket not connected")
        }
    }
    
    fun sendStopSignal() {
        webSocket?.let { ws ->
            if (ws.send("stop")) {
                Log.d("WebSocketManager", "Stop signal sent")
            } else {
                Log.e("WebSocketManager", "Failed to send stop signal")
            }
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Session stopped by user")
        webSocket = null
    }
    
    fun isConnected(): Boolean {
        return webSocket != null
    }
}