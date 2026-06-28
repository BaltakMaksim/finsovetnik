package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.finsovetnik.backend.entity.RefreshToken;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.repository.RefreshTokenRepository;
import ru.finsovetnik.backend.services.JwtService;
import ru.finsovetnik.backend.services.UserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;

    public AuthController(JwtService jwtService, 
                          RefreshTokenRepository refreshTokenRepository,
                          UserService userService) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userService = userService;
    }

    /**
     * Эндпоинт для автоматического входа по Refresh Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(@RequestBody Map<String, String> request) {
        String refreshTokenStr = request.get("refresh_token");
        
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token обязателен"));
        }

        try {
            // 1. Хешируем токен из запроса
            String tokenHash = hashToken(refreshTokenStr);
            
            // 2. Ищем в БД
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
            if (tokenOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Токен не найден"));
            }

            RefreshToken tokenEntity = tokenOpt.get();

            // 3. Проверяем, не отозван ли он и не истёк ли
            if (Boolean.TRUE.equals(tokenEntity.getIsRevoked()) || 
                tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
                // Если истёк — удаляем из БД
                refreshTokenRepository.delete(tokenEntity);
                return ResponseEntity.status(401).body(Map.of("error", "Токен истёк или отозван"));
            }

            // 4. Находим пользователя
            User user = tokenEntity.getUser();
            
            // 5. Генерируем новую пару токенов (ротация для безопасности)
            String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
            String newRefreshToken = jwtService.generateRefreshToken(user.getId());
            
            // 6. Сохраняем новый Refresh Token
            tokenEntity.setTokenHash(hashToken(newRefreshToken));
            tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(30));
            refreshTokenRepository.save(tokenEntity);

            // 7. Возвращаем новые токены и данные пользователя
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", newAccessToken);
            response.put("refresh_token", newRefreshToken);
            response.put("user_id", user.getId());
            response.put("username", user.getUsername());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println(" Ошибка обновления токена: " + e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Невалидный токен"));
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 не поддерживается", e);
        }
    }
}