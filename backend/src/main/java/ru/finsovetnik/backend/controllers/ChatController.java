package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.finsovetnik.backend.services.AiClientService;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiClientService aiClientService;

    public ChatController(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    @PostMapping("/message")
    public ResponseEntity<Object> sendMessage(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Текст сообщения пуст"));
        }

        // Вызываем AI-сервис
        Map<String, Object> aiResponse = aiClientService.parseExpense(text);

        return ResponseEntity.ok(aiResponse);
    }
}