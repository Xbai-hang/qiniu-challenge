package com.qiniu.challenge.tts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "mock", matchIfMissing = true)
public class MockTextToSpeechClient implements TextToSpeechClient {

    @Override
    public TtsAudio synthesize(TtsSynthesisRequest request) {
        return new TtsAudio(silenceWav(), "audio/wav");
    }

    @Override
    public String provider() {
        return "mock";
    }

    private byte[] silenceWav() {
        int sampleRate = 8000;
        int durationMs = 420;
        int samples = sampleRate * durationMs / 1000;
        int dataSize = samples * 2;
        byte[] wav = new byte[44 + dataSize];
        writeAscii(wav, 0, "RIFF");
        writeInt(wav, 4, 36 + dataSize);
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeInt(wav, 16, 16);
        writeShort(wav, 20, 1);
        writeShort(wav, 22, 1);
        writeInt(wav, 24, sampleRate);
        writeInt(wav, 28, sampleRate * 2);
        writeShort(wav, 32, 2);
        writeShort(wav, 34, 16);
        writeAscii(wav, 36, "data");
        writeInt(wav, 40, dataSize);
        return wav;
    }

    private void writeAscii(byte[] data, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            data[offset + i] = (byte) value.charAt(i);
        }
    }

    private void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
        data[offset + 2] = (byte) ((value >> 16) & 0xff);
        data[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
    }
}
