package com.biosdk.livenessdetection.data.models

import com.google.gson.annotations.SerializedName

data class SessionResponse(
    @SerializedName("session_id") val sessionId: String,
    val status: String,
    val message: String
)

data class LivenessResult(
    @SerializedName("frame_number") val frameNumber: Int,
    @SerializedName("liveness_score") val livenessScore: Double,
    val decision: String,
    @SerializedName("blink_detected") val blinkDetected: Boolean,
    @SerializedName("blink_count") val blinkCount: Int,
    @SerializedName("current_fps") val currentFps: Double
)

data class SessionSummary(
    @SerializedName("total_frames_processed") val totalFramesProcessed: Int,
    @SerializedName("output_folder") val outputFolder: String,
    @SerializedName("excel_file") val excelFile: String,
    @SerializedName("session_timestamp") val sessionTimestamp: String,
    @SerializedName("average_fps") val averageFps: Double
)

data class SessionStatus(
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("frame_count") val frameCount: Int,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("current_fps") val currentFps: Double
)

data class StopSessionResponse(
    val message: String
)

data class CompletionMessage(
    val status: String
)