package com.qiniu.challenge.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.challenge.auth.JwtService;
import com.qiniu.challenge.common.ApiException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = authenticate(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }
        session.getAttributes().put("userId", userId);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long id) {
            Set<WebSocketSession> sessions = sessionsByUser.get(id);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByUser.remove(id);
                }
            }
        }
    }

    public boolean push(NotificationResponse notification) {
        Set<WebSocketSession> sessions = sessionsByUser.get(notification.userId());
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        String payload;
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "reminder_notification");
            message.put("notificationId", notification.id());
            message.put("reminderId", notification.reminderId());
            message.put("title", notification.title());
            message.put("content", notification.content() == null ? "" : notification.content());
            message.put("createdAt", notification.createdAt().toString());
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException exception) {
            return false;
        }
        boolean pushed = false;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
                pushed = true;
            } catch (IOException ignored) {
                // Session cleanup is handled by the close callback.
            }
        }
        return pushed;
    }

    private Long authenticate(WebSocketSession session) {
        String token = tokenFromQuery(session.getUri());
        if (token == null) {
            return null;
        }
        try {
            return jwtService.parseAccessToken(token).userId();
        } catch (ApiException exception) {
            return null;
        }
    }

    private String tokenFromQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            int index = pair.indexOf('=');
            if (index > 0 && "token".equals(pair.substring(0, index))) {
                String token = pair.substring(index + 1).trim();
                return token.isEmpty() ? null : token;
            }
        }
        return null;
    }
}
