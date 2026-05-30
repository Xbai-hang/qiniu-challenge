package com.qiniu.challenge.auth;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.space.CalendarSpaceService;
import com.qiniu.challenge.user.CreateUserCommand;
import com.qiniu.challenge.user.User;
import com.qiniu.challenge.user.UserRepository;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CalendarSpaceService calendarSpaceService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CalendarSpaceService calendarSpaceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.calendarSpaceService = calendarSpaceService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().trim();

        ensureUniqueUsername(username);
        ensureUniqueEmail(email);

        try {
            User user = userRepository.save(new CreateUserCommand(
                    username,
                    email,
                    displayName,
                    passwordEncoder.encode(request.password())));
            calendarSpaceService.createPersonalSpace(user);
            return new RegisterResponse(toResponse(user), jwtService.generateAccessToken(user), null);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "用户名或邮箱已被注册");
        }
    }

    public AuthTokenResponse login(LoginRequest request) {
        String account = request.account().trim();
        User user = userRepository.findByUsernameOrEmail(account)
                .orElseThrow(this::badCredentials);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw badCredentials();
        }

        return new AuthTokenResponse(jwtService.generateAccessToken(user), toResponse(user));
    }

    public AuthUserResponse toCurrentUserResponse(User user) {
        return toResponse(user);
    }

    private void ensureUniqueUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "用户名已被注册",
                    Map.of("field", "username"));
        }
    }

    private void ensureUniqueEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "邮箱已被注册",
                    Map.of("field", "email"));
        }
    }

    private AuthUserResponse toResponse(User user) {
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.email(),
                user.displayName());
    }

    private ApiException badCredentials() {
        return new ApiException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
    }
}
