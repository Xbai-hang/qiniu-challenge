package com.qiniu.challenge.speech;

public interface SpeechTranscriptionClient {

    SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request);

    String provider();
}
