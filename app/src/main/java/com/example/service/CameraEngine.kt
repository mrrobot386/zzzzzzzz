package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraEngine"
        private const val FRAME_INTERVAL_MS = 1500L // 1 frame per 1.5s is optimal for Gemini Live
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var isFrontFacing: Boolean = false
    private var isStreamingVideo: Boolean = true
    private var lastSentFrameTime: Long = 0L

    var onFrameCaptured: ((ByteArray) -> Unit)? = null

    fun setPreviewView(view: PreviewView) {
        this.previewView = view
        startCamera()
    }

    fun toggleCameraFacing() {
        isFrontFacing = !isFrontFacing
        startCamera()
    }

    fun isFrontFacing(): Boolean = isFrontFacing

    fun setStreamingVideo(enabled: Boolean) {
        isStreamingVideo = enabled
    }

    fun captureImmediateFrame() {
        // Trigger manual frame capture
        lastSentFrameTime = 0L
    }

    private fun startCamera() {
        val view = previewView ?: return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(view: PreviewView) {
        val provider = cameraProvider ?: return

        try {
            provider.unbindAll()

            val lensFacing = if (isFrontFacing) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera use case binding failed", e)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (!isStreamingVideo || now - lastSentFrameTime < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            // Scale bitmap to reasonable resolution (max dimension 640px)
            val maxDim = 640
            val width = bitmap.width
            val height = bitmap.height
            val scale = if (width > height) {
                maxDim.toFloat() / width
            } else {
                maxDim.toFloat() / height
            }

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val jpegBytes = outputStream.toByteArray()

            lastSentFrameTime = now
            onFrameCaptured?.invoke(jpegBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }

    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    fun release() {
        stop()
        cameraExecutor.shutdown()
    }
}
