package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.finsovetnik.backend.dto.DashboardSummary;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.services.DashboardService;
import ru.finsovetnik.backend.services.JwtService;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtService jwtService;

    public DashboardController(DashboardService dashboardService, JwtService jwtService) {
        this.dashboardService = dashboardService;
        this.jwtService = jwtService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(defaultValue = "month") String period,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));
        }

        try {
            DashboardSummary summary = dashboardService.getSummary(userId, period);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(
            @RequestParam(defaultValue = "month") String period,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));
        }

        try {
            List<Transaction> transactions = dashboardService.getTransactions(userId, period);
            
            // Преобразуем сущности в простые Map для безопасной отправки на фронт
            List<Map<String, Object>> result = transactions.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", t.getId());
                    map.put("amount", t.getAmount());
                    map.put("category", t.getCategory());
                    map.put("type", t.getType().name());
                    map.put("reply", t.getReply());
                    map.put("receiptId", t.getReceiptId());
                    
                    //  Преобразуем LocalDateTime в миллисекунды (timestamp)
                    if (t.getCreatedAt() != null) {
                        long timestamp = t.getCreatedAt()
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();
                        map.put("createdAt", timestamp);
                    } else {
                        map.put("createdAt", null);
                    }
                    
                    return map;
                })
                .toList();
            
            return ResponseEntity.ok(Map.of("transactions", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // Вспомогательный метод для извлечения userId из токена
    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return jwtService.getUserIdFromToken(authHeader.substring(7));
            } catch (Exception e) { 
                return null; 
            }
        }
        return null;
    }
}