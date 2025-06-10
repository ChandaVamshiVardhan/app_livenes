package com.biosdk.livenessdetection.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.biosdk.livenessdetection.data.models.FaceDetectionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class FaceDetector {
    private val TAG = "FaceDetector"
    
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()
    
    private val detector = FaceDetection.getClient(options)
    
    suspend fun detectFace(imageProxy: ImageProxy): FaceDetectionResult {
        return try {
            val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
            val faces = detector.process(inputImage).await()
            
            if (faces.isNotEmpty()) {
                val face = faces[0] // Get the first detected face
                val confidence = if (face.trackingId != null) 0.9f else 0.7f
                Log.d(TAG, "Face detected with confidence: $confidence")
                FaceDetectionResult(
                    faceDetected = true,
                    confidence = confidence,
                    boundingBox = face.boundingBox
                )
            } else {
                Log.d(TAG, "No face detected")
                FaceDetectionResult(
                    faceDetected = false,
                    confidence = 0f,
                    boundingBox = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting face", e)
            FaceDetectionResult(
                faceDetected = false,
                confidence = 0f,
                boundingBox = null
            )
        }
    }
    
    suspend fun detectFaceFromBitmap(bitmap: Bitmap): FaceDetectionResult {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(inputImage).await()
            
            if (faces.isNotEmpty()) {
                val face = faces[0]
                val confidence = if (face.trackingId != null) 0.9f else 0.7f
                Log.d(TAG, "Face detected in bitmap with confidence: $confidence")
                FaceDetectionResult(
                    faceDetected = true,
                    confidence = confidence,
                    boundingBox = face.boundingBox
                )
            } else {
                Log.d(TAG, "No face detected in bitmap")
                FaceDetectionResult(
                    faceDetected = false,
                    confidence = 0f,
                    boundingBox = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting face in bitmap", e)
            FaceDetectionResult(
                faceDetected = false,
                confidence = 0f,
                boundingBox = null
            )
        }
    }
    
    fun close() {
        detector.close()
    }
}