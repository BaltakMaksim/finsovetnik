package ru.finsovetnik.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.finsovetnik.backend.entity.RefreshToken;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.repository.RefreshTokenRepository;
import ru.finsovetnik.backend.services.AiClientService;
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
@RequestMapping("/api/auth-chat")
public class AuthChatController {

    private final AiClientService aiClientService;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthChatController(
            AiClientService aiClientService, 
            UserService userService, 
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository) {
        this.aiClientService = aiClientService;
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostMapping("/message")
    public ResponseEntity<Object> sendMessage(@RequestBody Map<String, String> request) {
        String sessionId = request.get("session_id");
        String text = request.get("text");
        
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Session ID обязателен"));
        }
        
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Текст сообщения пуст"));
        }

        Map<String, Object> aiResponse = aiClientService.authChat(sessionId, text);

        Boolean authenticated = (Boolean) aiResponse.get("authenticated");
        Boolean checkExisting = (Boolean) aiResponse.get("check_existing");
        String username = (String) aiResponse.get("username");
        String seedPhrase = (String) aiResponse.get("seed_phrase");

        // =========================================================================
        // СЦЕНАРИЙ 1: Новый пользователь — создаём в БД и выдаём токены
        // =========================================================================
        if (Boolean.TRUE.equals(authenticated) && seedPhrase != null && !Boolean.TRUE.equals(checkExisting)) {
            try {
                if (!userService.userExistsBySeed(seedPhrase)) {
                    if (username != null && !username.isBlank()) {
                        User user = userService.createUser(username, seedPhrase);
                        
                        // Генерируем и сохраняем токены
                        Map<String, String> tokens = generateAndSaveTokens(user);
                        aiResponse.put("user_id", user.getId());
                        aiResponse.putAll(tokens);
                        
                        System.out.println("✅ Создан новый пользователь: " + username + " (ID: " + user.getId() + ")");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка создания пользователя: " + e.getMessage());
            }
        }
        
        // =========================================================================
        // СЦЕНАРИЙ 2: Существующий пользователь — проверяем и выдаём токены
        // =========================================================================
        else if (Boolean.TRUE.equals(checkExisting) && seedPhrase != null && username != null) {
            try {
                Optional<User> userOpt = userService.verifySeedPhrase(seedPhrase);
                
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    aiResponse.put("authenticated", true);
                    aiResponse.put("user_id", user.getId());
                    aiResponse.put("reply", "✅ С возвращением, " + user.getUsername() + "! 🙌 Рад тебя снова видеть.");
                    aiResponse.put("state", "AUTHENTICATED");
                    
                    // Генерируем и сохраняем токены
                    Map<String, String> tokens = generateAndSaveTokens(user);
                    aiResponse.putAll(tokens);
                    
                    System.out.println("✅ Вход существующего пользователя: " + user.getUsername() + " (ID: " + user.getId() + ")");
                } else {
                    aiResponse.put("authenticated", false);
                    aiResponse.put("check_existing", false);
                    aiResponse.put("reply", " Я не нашёл аккаунт с такой фразой. Попробуй ещё раз или зарегистрируйся заново.");
                    aiResponse.put("state", "OFFER_REREGISTER");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка проверки пользователя: " + e.getMessage());
                aiResponse.put("authenticated", false);
                aiResponse.put("reply", " Произошла ошибка при проверке.");
                aiResponse.put("state", "OFFER_REREGISTER");
            }
        }

        return ResponseEntity.ok(aiResponse);
    }

    /**
     * Генерирует Access и Refresh токены, сохраняет хеш Refresh токена в БД
     */
    private Map<String, String> generateAndSaveTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        
        // Сохраняем хеш refresh token в БД (сам токен не храним в БД в открытом виде!)
        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setUser(user);
        tokenEntity.setTokenHash(hashToken(refreshToken));
        tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshTokenRepository.save(tokenEntity);
        
        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", accessToken);
        tokens.put("refresh_token", refreshToken);
        return tokens;
    }

    /**
     * Хеширует строку через SHA-256
     */
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