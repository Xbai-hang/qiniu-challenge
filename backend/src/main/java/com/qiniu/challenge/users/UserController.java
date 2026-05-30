package com.qiniu.challenge.users;

import com.qiniu.challenge.auth.AuthService;
import com.qiniu.challenge.auth.AuthUserResponse;
import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ApiResponse.success(authService.toCurrentUserResponse(principal.user()));
    }
}
