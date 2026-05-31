package com.qiniu.challenge.speech;

import com.qiniu.challenge.ai.AiChatRequest;
import com.qiniu.challenge.ai.AiChatResponse;
import com.qiniu.challenge.ai.AiConversation;
import com.qiniu.challenge.ai.AiRepository;
import com.qiniu.challenge.ai.AiService;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.event.PermissionService;
import java.io.IOException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpeechService {

    private final SpeechTranscriptionClient transcriptionClient;
    private final SpeechRepository speechRepository;
    private final AiRepository aiRepository;
    private final PermissionService permissionService;
    private final AiService aiService;
    private final SpeechProperties properties;

    public SpeechService(
            SpeechTranscriptionClient transcriptionClient,
            SpeechRepository speechRepository,
            AiRepository aiRepository,
            PermissionService permissionService,
            AiService aiService,
            SpeechProperties properties) {
        this.transcriptionClient = transcriptionClient;
        this.speechRepository = speechRepository;
        this.aiRepository = aiRepository;
        this.permissionService = permissionService;
        this.aiService = aiService;
        this.properties = properties;
    }

    @Transactional
    public SpeechTranscriptionResponse transcribe(
            long currentUserId,
            long calendarSpaceId,
            Long conversationId,
            MultipartFile file) {
        permissionService.requireSpaceAccess(calendarSpaceId, currentUserId);
        requireConversationInSpace(currentUserId, calendarSpaceId, conversationId);
        validateFile(file);
        try {
            SpeechTranscriptionResult result = transcriptionClient.transcribe(new SpeechTranscriptionRequest(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType()));
            String text = result.text() == null ? "" : result.text().trim();
            if (text.isEmpty()) {
                throw new ApiException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE, "语音转写结果为空");
            }
            long transcriptionId = speechRepository.create(new CreateSpeechTranscriptionCommand(
                    currentUserId,
                    calendarSpaceId,
                    conversationId,
                    result.provider(),
                    result.modelName(),
                    text,
                    result.confidence(),
                    result.audioFormat(),
                    result.durationMs(),
                    "succeeded",
                    null,
                    null));
            return new SpeechTranscriptionResponse(
                    transcriptionId,
                    text,
                    result.provider(),
                    result.confidence(),
                    result.durationMs());
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "读取音频文件失败");
        }
    }

    private void requireConversationInSpace(long currentUserId, long calendarSpaceId, Long conversationId) {
        if (conversationId == null) {
            return;
        }
        AiConversation conversation = aiRepository.findConversation(conversationId, currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "AI 会话不存在"));
        if (conversation.calendarSpaceId() != calendarSpaceId) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "会话不属于当前日历空间");
        }
    }

    @Transactional
    public SpeechChatResponse transcribeAndChat(
            long currentUserId,
            long calendarSpaceId,
            Long conversationId,
            MultipartFile file) {
        SpeechTranscriptionResponse transcription = transcribe(currentUserId, calendarSpaceId, conversationId, file);
        AiChatResponse chat = aiService.chat(
                currentUserId,
                new AiChatRequest(calendarSpaceId, conversationId, "voice", transcription.text()),
                transcription.transcriptionId());
        return SpeechChatResponse.from(chat, transcription);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "请上传音频文件");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "音频文件过大，请控制在 10MB 内");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!isSupportedAudio(contentType, filename)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "仅支持 webm、wav、mp3、m4a、ogg 音频");
        }
    }

    private boolean isSupportedAudio(String contentType, String filename) {
        if (contentType.startsWith("audio/")) {
            return true;
        }
        return filename.endsWith(".webm")
                || filename.endsWith(".wav")
                || filename.endsWith(".mp3")
                || filename.endsWith(".m4a")
                || filename.endsWith(".ogg");
    }
}
