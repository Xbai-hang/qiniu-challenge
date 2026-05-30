package com.qiniu.challenge.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.common.ApiException;
import com.qiniu.challenge.common.ErrorCode;
import com.qiniu.challenge.user.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;
    private final long accessTokenTtlSeconds;

    @Autowired
    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret}") String jwtSecret,
            @Value("${app.security.access-token-ttl-seconds:86400}") long accessTokenTtlSeconds) {
        this(objectMapper, Clock.systemUTC(), jwtSecret, accessTokenTtlSeconds);
    }

    JwtService(ObjectMapper objectMapper, Clock clock, String jwtSecret, long accessTokenTtlSeconds) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(user.id()));
        claims.put("username", user.username());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(accessTokenTtlSeconds).getEpochSecond());

        String headerPart = encodeJson(header);
        String claimsPart = encodeJson(claims);
        String signaturePart = sign(headerPart + "." + claimsPart);
        return headerPart + "." + claimsPart + "." + signaturePart;
    }

    public JwtClaims parseAccessToken(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized();
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(signingInput), parts[2])) {
            throw unauthorized();
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        long expiresAt = readLong(claims.get("exp"));
        if (expiresAt <= clock.instant().getEpochSecond()) {
            throw unauthorized();
        }

        long userId = Long.parseLong(String.valueOf(claims.get("sub")));
        String username = String.valueOf(claims.get("username"));
        return new JwtClaims(userId, username, expiresAt);
    }

    private String encodeJson(Map<String, Object> payload) {
        try {
            return base64UrlEncode(objectMapper.writeValueAsBytes(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode JWT payload", exception);
        }
    }

    private Map<String, Object> decodeJson(String payload) {
        try {
            return objectMapper.readValue(base64UrlDecode(payload), CLAIMS_TYPE);
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return base64UrlEncode(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }

    private long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private ApiException unauthorized() {
        return new ApiException(ErrorCode.UNAUTHORIZED);
    }

    public record JwtClaims(long userId, String username, long expiresAt) {
    }
}
