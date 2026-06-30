package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.finsovetnik.backend.entity.Message;
import ru.finsovetnik.backend.services.JwtService;
import ru.finsovetnik.backend.services.MessageHistoryService;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final MessageHistoryService messageHistoryService;
    private final JwtService jwtService;

    public HistoryController(MessageHistoryService messageHistoryService, JwtService jwtService) {
        this.messageHistoryService = messageHistoryService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<?> getHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));
        }

        try {
            List<Message> messages = messageHistoryService.getRecentMessages(userId);
            
            List<Map<String, Object>> history = messages.stream()
                .map(this::messageToMap)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("messages", history);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Ошибка загрузки истории: " + e.getMessage())
            );
        }
    }

    /**
     *  Преобразование Message в Map (с поддержкой LocalDateTime)
     */
    private Map<String, Object> messageToMap(Message msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(msg.getId()));
        map.put("text", msg.getText());
        map.put("sender", msg.getSender());
        
        if (msg.getCreatedAt() != null) {
            long timestamp = msg.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            map.put("timestamp", timestamp);
        } else {
            map.put("timestamp", System.currentTimeMillis());
        }
        
        return map;
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return jwtService.getUserIdFromToken(token);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}