package ru.finsovetnik.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.enums.TransactionType;

// Spring сам создаст класс для работы с БД на основе этого интерфейса!
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserOrderByCreatedAtDesc(User user);
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Transaction> findByReceiptId(String receiptId);
    
    void deleteByReceiptId(String receiptId);

    /**
     * Получить все транзакции пользователя за период (от даты from до сейчас)
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.createdAt >= :from ORDER BY t.createdAt DESC")
    List<Transaction> findByUserIdAndPeriod(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    /**
     * Посчитать общую сумму по типу (INCOME или EXPENSE) за период
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND t.createdAt >= :from")
    double sumByTypeAndPeriod(@Param("userId") Long userId, @Param("type") TransactionType type, @Param("from") LocalDateTime from);

    /**
     * Получить сумму расходов в разрезе категорий за период
     */
    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.createdAt >= :from GROUP BY t.category")
    List<Object[]> sumByCategoryAndPeriod(@Param("userId") Long userId, @Param("from") LocalDateTime from);

        Optional<Transaction> findByFiscalId(String fiscalId);
    /**
     * Посчитать количество транзакций за период
     */
    @Query("SELECT t FROM Transaction t WHERE t.fiscalId = :fiscalId AND t.category <> 'Чек (ожидает детализации)'")
Optional<Transaction> findCompletedByFiscalId(@Param("fiscalId") String fiscalId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId AND t.createdAt >= :from")
    long countByUserIdAndPeriod(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}