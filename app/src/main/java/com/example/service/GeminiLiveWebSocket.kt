package com.example.service

import android.util.Base64
import android.util.Log
import com.example.data.model.GeminiVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveWebSocket(
    private val apiKey: String,
    private val selectedVoice: GeminiVoice,
    private val scope: CoroutineScope,
    private val onStateChanged: (Boolean, String?) -> Unit,
    private val onAudioChunkReceived: (ByteArray) -> Unit,
    private val onTranscriptReceived: (String, Boolean) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTurnComplete: () -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveWS"
        private const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private var isConnected = false

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    fun connect() {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            onStateChanged(false, "Please configure your Gemini API Key in the Secrets panel.")
            return
        }

        disconnect()

        val url = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected. Sending setup message...")
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
                isConnected = false
                scope.launch(Dispatchers.Main) {
                    onStateChanged(false, null)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                isConnected = false
                scope.launch(Dispatchers.Main) {
                    onStateChanged(false, t.localizedMessage ?: "Connection failure")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                scope.launch(Dispatchers.Main) {
                    onStateChanged(false, null)
                }
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                put("model", LIVE_MODEL)

                val generationConfig = JSONObject().apply {
                    val modalities = JSONArray().apply {
                        put("AUDIO")
                    }
                    put("responseModalities", modalities)

                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                put("voiceName", selectedVoice.voiceName)
                            }
                            put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", generationConfig)

                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        val part = JSONObject().apply {
                            put(
                                "text",
                                "You are Gemini Live, an engaging, observant, and concise voice AI assistant with real-time audio and vision perception. Speak with natural conversational warmth. Keep responses brief (1-3 sentences) unless the user specifically asks for elaboration. When you see something through the camera, refer to it naturally."
                            )
                        }
                        put(part)
                    }
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)
            }

            val root = JSONObject().apply {
                put("setup", setupObj)
            }

            ws.send(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message", e)
        }
    }

    private fun handleServerMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)

            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete from Gemini Live!")
                isConnected = true
                scope.launch(Dispatchers.Main) {
                    onStateChanged(true, null)
                }
                return
            }

            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Gemini speech was interrupted by user")
                    scope.launch(Dispatchers.Main) {
                        onInterrupted()
                    }
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                val text = part.getString("text")
                                scope.launch(Dispatchers.Main) {
                                    onTranscriptReceived(text, true)
                                }
                            }
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    onAudioChunkReceived(audioBytes)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    scope.launch(Dispatchers.Main) {
                        onTurnComplete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message", e)
        }
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        if (!isConnected || webSocket == null) return
        try {
            val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val mediaChunk = JSONObject().apply {
                put("mimeType", "audio/pcm;rate=16000")
                put("data", base64Data)
            }
            val chunksArray = JSONArray().apply { put(mediaChunk) }
            val realtimeInput = JSONObject().apply {
                put("mediaChunks", chunksArray)
            }
            val root = JSONObject().apply {
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    fun sendVideoFrame(jpegBytes: ByteArray) {
        if (!isConnected || webSocket == null) return
        try {
            val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val mediaChunk = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Data)
            }
            val chunksArray = JSONArray().apply { put(mediaChunk) }
            val realtimeInput = JSONObject().apply {
                put("mediaChunks", chunksArray)
            }
            val root = JSONObject().apply {
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending video frame", e)
        }
    }

    fun sendTextMessage(text: String) {
        if (!isConnected || webSocket == null) return
        try {
            val part = JSONObject().apply { put("text", text) }
            val partsArray = JSONArray().apply { put(part) }
            val turn = JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            }
            val turnsArray = JSONArray().apply { put(turn) }
            val clientContent = JSONObject().apply {
                put("turns", turnsArray)
                put("turnComplete", true)
            }
            val root = JSONObject().apply {
                put("clientContent", clientContent)
            }
            webSocket?.send(root.toString())
            scope.launch(Dispatchers.Main) {
                onTranscriptReceived(text, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message", e)
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (ignored: Exception) {}
        webSocket = null
        isConnected = false
    }
}
