package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
        // Используем новый метод валидации
        Long userId = jwtService.validateRefreshToken(refreshTokenStr);
        
        // Хешируем токен для поиска в БД
        String tokenHash = hashToken(refreshTokenStr);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Токен не найден в БД"));
        }

        RefreshToken tokenEntity = tokenOpt.get();

        // Проверяем, не отозван ли он
        if (Boolean.TRUE.equals(tokenEntity.getIsRevoked())) {
            refreshTokenRepository.delete(tokenEntity);
            return ResponseEntity.status(401).body(Map.of("error", "Токен отозван"));
        }

        User user = tokenEntity.getUser();
        
        // Проверяем, что userId в токене совпадает с userId в БД
        if (!user.getId().equals(userId)) {
            return ResponseEntity.status(401).body(Map.of("error", "Несовпадение пользователя"));
        }
        
        // Генерируем новую пару токенов (ротация)
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId());
        
        // Сохраняем новый refresh token
        tokenEntity.setTokenHash(hashToken(newRefreshToken));
        tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshTokenRepository.save(tokenEntity);

        Map<String, Object> response = new HashMap<>();
        response.put("access_token", newAccessToken);
        response.put("refresh_token", newRefreshToken);
        response.put("user_id", user.getId());
        response.put("username", user.getUsername());
        
        return ResponseEntity.ok(response);

    } catch (ExpiredJwtException e) {
        // Refresh token истёк — удаляем из БД и просим войти заново
        String tokenHash = hashToken(refreshTokenStr);
        refreshTokenRepository.findByTokenHash(tokenHash)
            .ifPresent(refreshTokenRepository::delete);
        return ResponseEntity.status(401).body(Map.of("error", "Refresh token истёк, войдите заново"));
    } catch (JwtException | IllegalArgumentException e) {
       
        return ResponseEntity.status(401).body(Map.of("error", "Невалидный refresh token"));
    } catch (Exception e) {
       
        return ResponseEntity.status(500).body(Map.of("error", "Внутренняя ошибка сервера"));
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