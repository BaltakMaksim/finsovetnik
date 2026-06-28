package ru.finsovetnik.backend.services;

import org.springframework.stereotype.Service;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String username, String seedPhrase) {
        User user = new User();
        user.setUsername(username);
        user.setSeedHash(hashSeedPhrase(normalizeSeedPhrase(seedPhrase)));
        user.setCreatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }

    public Optional<User> verifySeedPhrase(String seedPhrase) {
        String normalizedPhrase = normalizeSeedPhrase(seedPhrase);
        String inputHash = hashSeedPhrase(normalizedPhrase);
        
        Optional<User> userOpt = userRepository.findBySeedHash(inputHash);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        }
        return userOpt;
    }

    public boolean userExistsBySeed(String seedPhrase) {
        String normalizedPhrase = normalizeSeedPhrase(seedPhrase);
        String hash = hashSeedPhrase(normalizedPhrase);
        return userRepository.existsBySeedHash(hash);
    }

    /**
     * ✅ НОВОЕ: Нормализация seed-фразы (как в Python)
     * - Приводит к нижнему регистру
     * - Убирает знаки препинания
     * - Убирает лишние пробелы
     */
    private String normalizeSeedPhrase(String seedPhrase) {
        if (seedPhrase == null) return "";
        
        // Приводим к нижнему регистру
        String normalized = seedPhrase.toLowerCase();
        
        // Убираем знаки препинания (оставляем только буквы, цифры и пробелы)
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\s]", "");
        
        // Убираем лишние пробелы
        normalized = normalized.trim().replaceAll("\\s+", " ");
        
        return normalized;
    }

    private String hashSeedPhrase(String seedPhrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seedPhrase.getBytes(StandardCharsets.UTF_8));
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