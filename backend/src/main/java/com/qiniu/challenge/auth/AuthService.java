package com.qiniu.challenge.auth;

import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
            return new RegisterResponse(toResponse(user), null, null);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "用户名或邮箱已被注册");
        }
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
}
