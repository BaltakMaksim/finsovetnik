package ru.finsovetnik.backend.services;

import org.springframework.stereotype.Service;
import ru.finsovetnik.backend.dto.DashboardSummary;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.enums.TransactionType;
import ru.finsovetnik.backend.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Получает сводку по финансам за период
     */
    public DashboardSummary getSummary(Long userId, String period) {
        LocalDateTime from = calculatePeriodStart(period);

        double income = transactionRepository.sumByTypeAndPeriod(userId, TransactionType.INCOME, from);
        double expense = transactionRepository.sumByTypeAndPeriod(userId, TransactionType.EXPENSE, from);
        long count = transactionRepository.countByUserIdAndPeriod(userId, from);

        List<Object[]> categorySums = transactionRepository.sumByCategoryAndPeriod(userId, from);
        Map<String, Double> byCategory = new HashMap<>();
        for (Object[] row : categorySums) {
            String category = (String) row[0];
            Double sum = (Double) row[1];
            byCategory.put(category, sum);
        }

        return new DashboardSummary(income, expense, (int) count, byCategory, period);
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Получает список транзакций за период
     */
    public List<Transaction> getTransactions(Long userId, String period) {
        LocalDateTime from = calculatePeriodStart(period);
        return transactionRepository.findByUserIdAndPeriod(userId, from);
    }

    /**
     * Вычисляет начало периода
     */
    private LocalDateTime calculatePeriodStart(String period) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (period) {
            case "month":
                return now.minusMonths(1);
            case "year":
                return now.minusYears(1);
            case "all":
            default:
                return LocalDateTime.of(2000, 1, 1, 0, 0);
        }
    }
}