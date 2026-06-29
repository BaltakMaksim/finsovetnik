package ru.finsovetnik.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.finsovetnik.backend.entity.Message;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    
    @Query("SELECT m FROM Message m WHERE m.user.id = :userId ORDER BY m.createdAt DESC")
    List<Message> findRecentMessages(@Param("userId") Long userId, Pageable pageable);
    
    @Query("SELECT m FROM Message m WHERE m.user.id = :userId ORDER BY m.createdAt ASC")
    List<Message> findAllByUserId(@Param("userId") Long userId);
}