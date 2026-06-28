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
    public ResponseEntity<Object> sendMessage(@RequestBody Map<String, Object> request) {
    String text = (String) request.get("text");
    Long userId = request.get("user_id") != null ? Long.valueOf(request.get("user_id").toString()) : null;
    
    if (text == null || text.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Текст сообщения пуст"));
    }

    Map<String, Object> aiResponse = aiClientService.parseExpense(text, userId);

    return ResponseEntity.ok(aiResponse);
}
}