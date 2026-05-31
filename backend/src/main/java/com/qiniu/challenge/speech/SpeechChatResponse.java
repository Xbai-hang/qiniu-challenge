package com.qiniu.challenge.speech;

import com.qiniu.challenge.ai.AiChatResponse;

public record SpeechChatResponse(
        long conversationId,
        long messageId,
        String reply,
        Object resultCard,
        Object toolCalls,
        Object confirmations,
        Object taskStates,
        SpeechTranscriptionResponse transcription) {

    public static SpeechChatResponse from(AiChatResponse chat, SpeechTranscriptionResponse transcription) {
        return new SpeechChatResponse(
                chat.conversationId(),
                chat.messageId(),
                chat.reply(),
                chat.resultCard(),
                chat.toolCalls(),
                chat.confirmations(),
                chat.taskStates(),
                transcription);
    }
}
