package com.biosdk.livenessdetection.utils

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class VideoFrameExtractor {
    private val TAG = "VideoFrameExtractor"
    
    fun extractFrameAsJpeg(imageProxy: ImageProxy, quality: Int = 90): ByteArray? {
        return try {
            val image = imageProxy.image ?: return null
            
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
                quality,
                out
            )
            
            val jpegData = out.toByteArray()
            Log.d(TAG, "Compressed frame size: ${jpegData.size} bytes")
            jpegData
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame", e)
            null
        }
    }
    
    fun extractFrameAsBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val jpegData = extractFrameAsJpeg(imageProxy) ?: return null
            android.graphics.BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap from frame", e)
            null
        }
    }
}