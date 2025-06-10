package com.biosdk.livenessdetection.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biosdk.livenessdetection.ui.theme.ErrorRed

@Composable
fun RecordingIndicator(
    timeLeft: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val formatTime = { seconds: Int ->
        val mins = seconds / 60
        val secs = seconds % 60
        "$mins:${secs.toString().padStart(2, '0')}"
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        color = ErrorRed,
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(
                text = "REC",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = formatTime(timeLeft),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}