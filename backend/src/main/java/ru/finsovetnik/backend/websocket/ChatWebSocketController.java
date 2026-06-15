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
     */
    @MessageMapping("/chat.message")
    @SendTo("/topic/chat.responses")
    public Map<String, Object> handleMessage(Map<String, String> message) {
        String text = message.get("text");
        
        if (text == null || text.isBlank()) {
            return Map.of("error", "Текст пуст");
        }

        // Вызываем тот же сервис, что и для REST API
        return aiClientService.parseExpense(text);
    }
}