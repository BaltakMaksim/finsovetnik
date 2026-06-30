package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    // 1. Сканирование QR-кода
    @PostMapping("/scan")
    public ResponseEntity<?> scanReceipt(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));

        try {
            Map<String, Object> result = receiptService.parseAndSave(userId, request.get("qr_data"));
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 2. Загрузка фото чека (Умное слияние)
    @PostMapping("/scan-photo")
    public ResponseEntity<?> scanReceiptPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "receipt_id", required = false) String receiptId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));

        try {
            Map<String, Object> result = receiptService.analyzePhotoAndMerge(userId, file, receiptId);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return jwtService.getUserIdFromToken(authHeader.substring(7));
            } catch (Exception e) { return null; }
        }
        return null;
    }
}