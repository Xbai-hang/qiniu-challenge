package com.qiniu.challenge.speech;

public interface SpeechRepository {

    long create(CreateSpeechTranscriptionCommand command);
}
