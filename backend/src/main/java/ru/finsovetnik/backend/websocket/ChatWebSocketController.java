package ru.finsovetnik.backend.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import ru.finsovetnik.backend.services.AiClientService;

import java.util.Map;

@Controller
public class ChatWebSocketController {

    private final AiClientService aiClientService;

    public ChatWebSocketController(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    /**
     * Обрабатывает сообщение из чата
     * Клиент отправляет на /app/chat.message
     * Сервер отвечает всем подписчикам на /topic/chat.responses
     * 
     * Теперь принимает user_id для привязки транзакций к пользователю
     */
    @MessageMapping("/chat.message")
    @SendTo("/topic/chat.responses")
    public Map<String, Object> handleMessage(Map<String, Object> message) {
        String text = (String) message.get("text");
        
        if (text == null || text.isBlank()) {
            return Map.of("error", "Текст пуст");
        }

        // ✅ Извлекаем user_id из сообщения
        Long userId = null;
        Object userIdObj = message.get("user_id");
        if (userIdObj != null) {
            try {
                userId = Long.valueOf(userIdObj.toString());
                System.out.println("🔐 WebSocket: user_id=" + userId);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Неверный формат user_id: " + userIdObj);
            }
        }

        // ✅ Передаём userId для привязки транзакций
        return aiClientService.parseExpense(text, userId);
    }
}