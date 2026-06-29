package ru.finsovetnik.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import ru.finsovetnik.backend.services.AiClientService;

import java.security.Principal;
import java.util.Map;

@Controller
public class ChatWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketController.class);
    private final AiClientService aiClientService;

    public ChatWebSocketController(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    @MessageMapping("/chat.message")
    @SendTo("/topic/chat.responses")
    public Map<String, Object> handleMessage(
            Map<String, Object> message,
            Principal principal) {
        
        String text = (String) message.get("text");
        
        if (text == null || text.isBlank()) {
            return Map.of("error", "Текст пуст");
        }

        // ✅ ПРОВЕРКА: Principal не null
        if (principal == null) {
            logger.warn("⚠️ WebSocket: Principal is null - пользователь не аутентифицирован");
            return Map.of(
                "is_financial", false,
                "transactions", java.util.List.of(),
                "reply", "❌ Ошибка: вы не аутентифицированы. Пожалуйста, войдите заново.",
                "error", "NOT_AUTHENTICATED"
            );
        }

        // ✅ ПРОВЕРКА: userId не null
        Long userId;
        try {
            String principalName = principal.getName();
            if (principalName == null || principalName.isBlank()) {
                throw new NumberFormatException("Principal name is empty");
            }
            userId = Long.valueOf(principalName);
            logger.info("🔐 WebSocket: user_id={} (из JWT)", userId);
        } catch (NumberFormatException e) {
            logger.error("❌ Неверный формат user_id в Principal: {}", principal.getName());
            return Map.of(
                "is_financial", false,
                "transactions", java.util.List.of(),
                "reply", "❌ Ошибка: невалидный идентификатор пользователя.",
                "error", "INVALID_USER_ID"
            );
        }

        return aiClientService.parseExpense(text, userId);
    }
}