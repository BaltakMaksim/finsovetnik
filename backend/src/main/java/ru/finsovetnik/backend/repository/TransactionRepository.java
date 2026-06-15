package ru.finsovetnik.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.finsovetnik.backend.entity.Transaction;

// Spring сам создаст класс для работы с БД на основе этого интерфейса!
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}