package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.finsovetnik.backend.services.JwtService;
import ru.finsovetnik.backend.services.ReceiptService;

import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final JwtService jwtService;

    public ReceiptController(ReceiptService receiptService, JwtService jwtService) {
        this.receiptService = receiptService;
        this.jwtService = jwtService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scanReceipt(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        String qrData = request.get("qr_data");
        
        if (qrData == null || qrData.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "QR данные не получены"));
        }

        Long userId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                userId = jwtService.getUserIdFromToken(token);
            } catch (Exception e) {
                return ResponseEntity.status(401).body(Map.of("error", "Неверный токен"));
            }
        }

        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));
        }

        try {
            
            Map<String, Object> parsed = receiptService.parseAndSave(userId, qrData);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Чек успешно распознан!",
                "data", parsed
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Ошибка обработки: " + e.getMessage())
            );
        }
    }
}