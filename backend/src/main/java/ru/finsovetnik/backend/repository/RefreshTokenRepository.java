package ru.finsovetnik.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.finsovetnik.backend.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    // Найти токен по его хешу
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
    // Удалить все токены пользователя (например, при выходе из системы)
    void deleteByUserId(Long userId);
}