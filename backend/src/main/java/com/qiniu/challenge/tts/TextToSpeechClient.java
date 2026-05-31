package com.qiniu.challenge.tts;

public interface TextToSpeechClient {

    TtsAudio synthesize(TtsSynthesisRequest request);

    String provider();
}
