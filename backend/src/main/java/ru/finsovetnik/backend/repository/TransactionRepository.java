package ru.finsovetnik.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.entity.User;

// Spring сам создаст класс для работы с БД на основе этого интерфейса!
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserOrderByCreatedAtDesc(User user);
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Transaction> findByReceiptId(String receiptId);
    
    void deleteByReceiptId(String receiptId);
}