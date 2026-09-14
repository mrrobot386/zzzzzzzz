package com.example.data.model

enum class LiveConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class LiveAgentState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

enum class GeminiVoice(val voiceName: String, val displayName: String, val description: String) {
    PUCK("Puck", "Puck", "Energetic & playful"),
    CHARON("Charon", "Charon", "Calm & authoritative"),
    AOEDE("Aoede", "Aoede", "Warm & melodic"),
    FENRIR("Fenrir", "Fenrir", "Deep & thoughtful"),
    KORE("Kore", "Kore", "Clear & friendly")
}

data class LiveTranscriptMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isInterrupted: Boolean = false
)

enum class MessageSender {
    USER,
    GEMINI
}

data class PresetVisionPrompt(
    val title: String,
    val iconName: String,
    val prompt: String
)
