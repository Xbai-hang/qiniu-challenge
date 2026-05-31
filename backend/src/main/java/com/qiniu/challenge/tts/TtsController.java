package com.qiniu.challenge.tts;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping("/synthesize")
    public ApiResponse<TtsSynthesizeResponse> synthesize(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody TtsSynthesizeRequest request) {
        return ApiResponse.success(ttsService.synthesize(principal.userId(), request));
    }

    @GetMapping("/audio/{ttsId}")
    public ResponseEntity<byte[]> audio(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long ttsId) {
        TtsAudio audio = ttsService.audio(principal.userId(), ttsId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .body(audio.bytes());
    }
}
