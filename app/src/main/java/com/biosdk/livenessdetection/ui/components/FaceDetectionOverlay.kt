package com.biosdk.livenessdetection.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biosdk.livenessdetection.ui.theme.SuccessGreen
import com.biosdk.livenessdetection.ui.theme.WarningOrange
import com.biosdk.livenessdetection.viewmodel.SessionState

@Composable
fun FaceDetectionOverlay(
    faceDetected: Boolean,
    confidence: Float,
    sessionState: SessionState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "face_detection")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Face detection circle
        Canvas(
            modifier = Modifier.size(300.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 20.dp.toPx()
            
            // Outer circle (face detection area)
            drawCircle(
                color = when {
                    sessionState == SessionState.SESSION_ACTIVE -> SuccessGreen
                    faceDetected -> SuccessGreen
                    else -> WarningOrange
                },
                radius = radius,
                center = center,
                style = Stroke(
                    width = 4.dp.toPx()
                ),
                alpha = if (sessionState == SessionState.WAITING_FOR_FACE) pulseAlpha else 0.8f
            )
            
            // Inner circle (face area indicator)
            if (faceDetected) {
                drawCircle(
                    color = SuccessGreen,
                    radius = radius * 0.8f,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx()
                    ),
                    alpha = 0.4f
                )
            }
        }
        
        // Status text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (sessionState) {
                SessionState.WAITING_FOR_FACE -> {
                    Text(
                        text = "Position your face in the circle",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (faceDetected) {
                        Text(
                            text = "Face detected! Hold steady...",
                            color = SuccessGreen,
                            fontSize = 14.sp
                        )
                    }
                }
                SessionState.FACE_DETECTED -> {
                    Text(
                        text = "Starting session...",
                        color = SuccessGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                SessionState.SESSION_STARTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = SuccessGreen,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Connecting...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                SessionState.SESSION_ACTIVE -> {
                    Text(
                        text = "Recording in progress",
                        color = SuccessGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Keep your face in the circle",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                SessionState.SESSION_COMPLETED -> {
                    Text(
                        text = "Session completed!",
                        color = SuccessGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Confidence indicator
            if (faceDetected && confidence > 0) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "Confidence: ${(confidence * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}