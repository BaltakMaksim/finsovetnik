package ru.finsovetnik.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.finsovetnik.backend.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySeedHash(String seedHash);
    boolean existsBySeedHash(String seedHash);
    
    // Опционально: поиск по имени (для отображения)
    List<User> findByUsername(String username);
}