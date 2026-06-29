package ru.finsovetnik.backend.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Генерирует короткий access token (живёт 15 минут)
     */
    public String generateAccessToken(Long userId, String username) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of("username", username, "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Генерирует длинный refresh token (живёт 30 дней)
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of("type", "refresh"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Проверяет и парсит токен
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Извлекает userId из токена
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Проверяет, не истёк ли токен
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    //  НОВЫЕ МЕТОДЫ для WebSocket и API
    // =========================================================================

    /**
     *  ВАЛИДАЦИЯ ACCESS TOKEN
     * 
     * Проверяет:
     * 1. Подпись токена
     * 2. Срок действия
     * 3. Что это именно ACCESS токен (не refresh!)
     * 
     * @param token JWT токен
     * @return userId из токена
     * @throws JwtException если токен невалиден
     * @throws IllegalArgumentException если токен не является access token
     */
    public Long validateAccessToken(String token) {
        try {
            Claims claims = parseToken(token);

            // Проверка срока действия
            if (claims.getExpiration().before(new Date())) {
                throw new ExpiredJwtException(null, claims, "Access token истёк");
            }

            // КРИТИЧНО: проверяем, что это именно access token
            String tokenType = claims.get("type", String.class);
            if (!"access".equals(tokenType)) {
                logger.warn("⚠️ Попытка использовать {} token как access token", tokenType);
                throw new IllegalArgumentException("Токен не является access token");
            }

            // Извлекаем userId
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("В токене отсутствует subject (userId)");
            }

            return Long.parseLong(subject);

        } catch (ExpiredJwtException e) {
            logger.warn("⏰ Access token истёк: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            logger.error("❌ Невалидный JWT: {}", e.getMessage());
            throw e;
        } catch (NumberFormatException e) {
            logger.error("❌ Некорректный userId в токене: {}", e.getMessage());
            throw new IllegalArgumentException("Некорректный userId в токене");
        }
    }

    /**
     *  ВАЛИДАЦИЯ REFRESH TOKEN
     * 
     * Проверяет:
     * 1. Подпись токена
     * 2. Срок действия
     * 3. Что это именно REFRESH токен (не access!)
     * 
     * @param token JWT токен
     * @return userId из токена
     * @throws JwtException если токен невалиден
     * @throws IllegalArgumentException если токен не является refresh token
     */
    public Long validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);

            // Проверка срока действия
            if (claims.getExpiration().before(new Date())) {
                throw new ExpiredJwtException(null, claims, "Refresh token истёк");
            }

            // ✅ КРИТИЧНО: проверяем, что это именно refresh token
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                logger.warn("⚠️ Попытка использовать {} token как refresh token", tokenType);
                throw new IllegalArgumentException("Токен не является refresh token");
            }

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("В токене отсутствует subject (userId)");
            }

            return Long.parseLong(subject);

        } catch (ExpiredJwtException e) {
            logger.warn("⏰ Refresh token истёк: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            logger.error("❌ Невалидный refresh token: {}", e.getMessage());
            throw e;
        } catch (NumberFormatException e) {
            logger.error("❌ Некорректный userId в refresh token: {}", e.getMessage());
            throw new IllegalArgumentException("Некорректный userId в refresh token");
        }
    }

    /**
     * Извлекает тип токена (access/refresh) без строгой валидации
     */
    public String getTokenType(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("type", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}