package ru.finsovetnik.backend.services;

import org.springframework.stereotype.Service;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.enums.TransactionType;
import ru.finsovetnik.backend.repository.TransactionRepository;
import ru.finsovetnik.backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReceiptService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public ReceiptService(
            TransactionRepository transactionRepository,
            UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> parseAndSave(Long userId, String qrData) {
        // 1. Парсим строку QR-кода (формат: t=20230101T120000&s=150.00&fn=123&i=456&fp=789)
        Map<String, String> params = parseQrString(qrData);
        
        String dateStr = params.get("t");
        String amountStr = params.get("s");
        String fn = params.get("fn");

        if (dateStr == null || amountStr == null) {
            throw new RuntimeException("Неверный формат QR-кода чека (отсутствует дата или сумма)");
        }

        // 2. Парсим дату
        LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
        String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        // 3. Парсим сумму
        double amount = Double.parseDouble(amountStr.replace(",", "."));

        // 4. Находим пользователя
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // 5. Создаем и сохраняем транзакцию
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setCategory("Покупка (QR)");
        transaction.setType(TransactionType.EXPENSE);
        transaction.setIsFinancial(true);
        transaction.setReply(String.format("Чек от %s | ФН: %s", formattedDate, fn != null ? fn : "неизвестно"));

        transactionRepository.save(transaction);

        // 6. Возвращаем результат для фронта
        Map<String, Object> result = new HashMap<>();
        result.put("amount", amount);
        result.put("date", formattedDate);
        result.put("fn", fn);

        return result;
    }

    private Map<String, String> parseQrString(String qrData) {
        Map<String, String> params = new HashMap<>();
        // Разбиваем по &
        String[] pairs = qrData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }
}