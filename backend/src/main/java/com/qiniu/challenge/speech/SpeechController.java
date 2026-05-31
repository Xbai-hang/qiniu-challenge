package com.qiniu.challenge.speech;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechService speechService;

    public SpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    @PostMapping("/transcribe")
    public ApiResponse<SpeechTranscriptionResponse> transcribe(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam long calendarSpaceId,
            @RequestParam(required = false) Long conversationId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(speechService.transcribe(principal.userId(), calendarSpaceId, conversationId, file));
    }

    @PostMapping("/transcribe-and-chat")
    public ApiResponse<SpeechChatResponse> transcribeAndChat(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam long calendarSpaceId,
            @RequestParam(required = false) Long conversationId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(speechService.transcribeAndChat(
                principal.userId(),
                calendarSpaceId,
                conversationId,
                file));
    }
}
