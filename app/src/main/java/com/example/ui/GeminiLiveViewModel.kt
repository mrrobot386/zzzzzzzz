package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.GeminiVoice
import com.example.data.model.LiveAgentState
import com.example.data.model.LiveConnectionState
import com.example.data.model.LiveTranscriptMessage
import com.example.data.model.MessageSender
import com.example.service.AudioInputManager
import com.example.service.AudioOutputManager
import com.example.service.GeminiLiveWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class GeminiLiveUiState(
    val connectionState: LiveConnectionState = LiveConnectionState.DISCONNECTED,
    val agentState: LiveAgentState = LiveAgentState.IDLE,
    val selectedVoice: GeminiVoice = GeminiVoice.PUCK,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = false,
    val isPipMode: Boolean = true,
    val userAmplitude: Float = 0f,
    val geminiAmplitude: Float = 0f,
    val messages: List<LiveTranscriptMessage> = emptyList(),
    val sessionSeconds: Int = 0,
    val errorMessage: String? = null,
    val showVoiceDialog: Boolean = false,
    val hasApiKey: Boolean = false
)

class GeminiLiveViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GeminiLiveVM"
    }

    private val _uiState = MutableStateFlow(GeminiLiveUiState())
    val uiState: StateFlow<GeminiLiveUiState> = _uiState.asStateFlow()

    private var liveWebSocket: GeminiLiveWebSocket? = null
    private val audioInputManager = AudioInputManager(viewModelScope)
    private val audioOutputManager = AudioOutputManager(viewModelScope)

    private var timerJob: Job? = null
    private var speechDecayJob: Job? = null
    private val apiKey = try {
        BuildConfig.GEMINI_API_KEY
    } catch (e: Exception) {
        ""
    }

    init {
        val validKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
        _uiState.update { it.copy(hasApiKey = validKey) }
    }

    fun startSession() {
        if (!_uiState.value.hasApiKey) {
            _uiState.update {
                it.copy(
                    connectionState = LiveConnectionState.ERROR,
                    errorMessage = "Gemini API Key missing. Please set GEMINI_API_KEY in the Secrets panel."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                connectionState = LiveConnectionState.CONNECTING,
                errorMessage = null,
                sessionSeconds = 0
            )
        }

        liveWebSocket = GeminiLiveWebSocket(
            apiKey = apiKey,
            selectedVoice = _uiState.value.selectedVoice,
            scope = viewModelScope,
            onStateChanged = { isConnected, error ->
                if (isConnected) {
                    _uiState.update {
                        it.copy(
                            connectionState = LiveConnectionState.CONNECTED,
                            agentState = LiveAgentState.LISTENING
                        )
                    }
                    startSessionTimer()
                    startAudioInput()
                    startAudioOutput()
                } else {
                    _uiState.update {
                        it.copy(
                            connectionState = if (error != null) LiveConnectionState.ERROR else LiveConnectionState.DISCONNECTED,
                            agentState = LiveAgentState.IDLE,
                            errorMessage = error
                        )
                    }
                    stopSessionTimer()
                    stopAudio()
                }
            },
            onAudioChunkReceived = { pcmChunk ->
                audioOutputManager.queueAudio(pcmChunk)
                _uiState.update { it.copy(agentState = LiveAgentState.SPEAKING) }
            },
            onTranscriptReceived = { text, isModel ->
                handleTranscript(text, isModel)
            },
            onInterrupted = {
                audioOutputManager.interrupt()
                _uiState.update { state ->
                    val updated = state.messages.mapIndexed { idx, msg ->
                        if (idx == state.messages.lastIndex && msg.sender == MessageSender.GEMINI) {
                            msg.copy(isInterrupted = true)
                        } else msg
                    }
                    state.copy(messages = updated, agentState = LiveAgentState.LISTENING)
                }
            },
            onTurnComplete = {
                _uiState.update { it.copy(agentState = LiveAgentState.LISTENING) }
            }
        )

        liveWebSocket?.connect()
    }

    private fun startAudioInput() {
        audioInputManager.isMuted = _uiState.value.isMuted
        audioInputManager.startRecording(
            onAudioChunk = { pcmChunk ->
                liveWebSocket?.sendAudioChunk(pcmChunk)
            },
            onAmplitudeChanged = { amp ->
                _uiState.update { it.copy(userAmplitude = amp) }
                if (amp > 0.15f && _uiState.value.agentState != LiveAgentState.SPEAKING) {
                    _uiState.update { it.copy(agentState = LiveAgentState.LISTENING) }
                }
            }
        )
    }

    private fun startAudioOutput() {
        audioOutputManager.startPlayback { amp ->
            _uiState.update {
                it.copy(
                    geminiAmplitude = amp,
                    agentState = if (amp > 0.05f) LiveAgentState.SPEAKING else it.agentState
                )
            }
        }
    }

    private fun stopAudio() {
        audioInputManager.stopRecording()
        audioOutputManager.interrupt()
    }

    private fun handleTranscript(text: String, isModel: Boolean) {
        val sender = if (isModel) MessageSender.GEMINI else MessageSender.USER
        _uiState.update { state ->
            val list = state.messages.toMutableList()
            if (isModel && list.isNotEmpty() && list.last().sender == MessageSender.GEMINI && !list.last().isInterrupted) {
                // Append text chunk to current model turn
                val last = list.removeAt(list.lastIndex)
                list.add(last.copy(text = last.text + text))
            } else {
                list.add(LiveTranscriptMessage(sender = sender, text = text))
            }
            state.copy(messages = list)
        }
    }

    fun endSession() {
        stopSessionTimer()
        liveWebSocket?.disconnect()
        liveWebSocket = null
        stopAudio()
        _uiState.update {
            it.copy(
                connectionState = LiveConnectionState.DISCONNECTED,
                agentState = LiveAgentState.IDLE,
                userAmplitude = 0f,
                geminiAmplitude = 0f
            )
        }
    }

    fun toggleMute() {
        val newMute = !_uiState.value.isMuted
        audioInputManager.isMuted = newMute
        _uiState.update { it.copy(isMuted = newMute) }
    }

    fun toggleVideo() {
        val newVideo = !_uiState.value.isVideoEnabled
        _uiState.update { it.copy(isVideoEnabled = newVideo) }
    }

    fun togglePip() {
        _uiState.update { it.copy(isPipMode = !it.isPipMode) }
    }

    fun toggleCameraFacing() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun setVoiceDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showVoiceDialog = visible) }
    }

    fun selectVoice(voice: GeminiVoice) {
        _uiState.update { it.copy(selectedVoice = voice) }
        // If connected, reconnect to apply new voice setup
        if (_uiState.value.connectionState == LiveConnectionState.CONNECTED) {
            startSession()
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        liveWebSocket?.sendTextMessage(text)
        _uiState.update { it.copy(agentState = LiveAgentState.THINKING) }
    }

    fun onFrameCaptured(jpegBytes: ByteArray) {
        if (_uiState.value.isVideoEnabled && _uiState.value.connectionState == LiveConnectionState.CONNECTED) {
            liveWebSocket?.sendVideoFrame(jpegBytes)
        }
    }

    fun inspectSampleImage(label: String) {
        // Generate a synthetic high-contrast test subject card for testing
        // multimodal vision even when in an emulator with virtual test pattern
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(AndroidColor.rgb(18, 24, 38))

                val paint = Paint().apply {
                    color = AndroidColor.WHITE
                    textSize = 32f
                    isAntiAlias = true
                }
                canvas.drawText("GEMINI LIVE VISION TEST: $label", 40f, 80f, paint)

                val accentPaint = Paint().apply {
                    color = AndroidColor.rgb(56, 189, 248)
                    strokeWidth = 6f
                    style = Paint.Style.STROKE
                }
                canvas.drawRect(40f, 120f, 600f, 400f, accentPaint)

                val textPaint = Paint().apply {
                    color = AndroidColor.rgb(226, 232, 240)
                    textSize = 24f
                    isAntiAlias = true
                }

                when (label) {
                    "Math Equation" -> {
                        canvas.drawText("f(x) = 3x^2 + 12x - 5", 80f, 220f, textPaint)
                        canvas.drawText("Question: Find critical points and derivative f'(x)", 80f, 270f, textPaint)
                    }
                    "Circuit Diagram" -> {
                        canvas.drawText("Input Voltage: 5V DC -> Resistor R1 (10k) -> LED", 80f, 220f, textPaint)
                        canvas.drawText("Microcontroller GPIO 14 -> Transistor Switch", 80f, 270f, textPaint)
                    }
                    else -> {
                        canvas.drawText("Visual Subject: $label", 80f, 220f, textPaint)
                        canvas.drawText("Real-time Multimodal Live Stream Active", 80f, 270f, textPaint)
                    }
                }

                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                val bytes = out.toByteArray()

                onFrameCaptured(bytes)
                sendTextMessage("I am showing you: $label. Please analyze and explain it verbally!")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating sample test image", e)
            }
        }
    }

    private fun startSessionTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(sessionSeconds = it.sessionSeconds + 1) }
            }
        }
    }

    private fun stopSessionTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        endSession()
        audioOutputManager.release()
    }
}
