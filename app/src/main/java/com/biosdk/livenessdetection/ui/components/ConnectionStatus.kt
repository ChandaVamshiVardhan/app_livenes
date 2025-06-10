package com.biosdk.livenessdetection.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biosdk.livenessdetection.ui.theme.ErrorRed
import com.biosdk.livenessdetection.ui.theme.SuccessGreen

@Composable
fun ConnectionStatus(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Card(
        modifier = modifier
            .alpha(if (isConnected) alpha else 1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                SuccessGreen.copy(alpha = 0.2f) 
            else 
                ErrorRed.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isConnected) SuccessGreen else ErrorRed,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isConnected) SuccessGreen else ErrorRed
            )
        }
    }
}