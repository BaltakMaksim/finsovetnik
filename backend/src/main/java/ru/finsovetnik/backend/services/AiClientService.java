package ru.finsovetnik.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.enums.TransactionType;
import ru.finsovetnik.backend.repository.TransactionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiClientService {

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;
    private final TransactionRepository transactionRepository;

    public AiClientService(
            @Value("${ai.service.url}") String aiServiceUrl,
            TransactionRepository transactionRepository) {
        this.restTemplate = new RestTemplate();
        this.aiServiceUrl = aiServiceUrl;
        this.transactionRepository = transactionRepository;
        System.out.println("🤖 AI Client initialized. Target URL: " + aiServiceUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseExpense(String text) {
        String url = aiServiceUrl + "/parse";
        Map<String, String> requestBody = Map.of("text", text);

        System.out.println("📤 Отправка в AI: " + text);

        // 1. Получаем ответ от AI
        Map<String, Object> aiResponse = restTemplate.postForObject(url, requestBody, Map.class);
        System.out.println("📥 Получено от AI: " + aiResponse);

        // 2. Сохраняем в базу данных
        saveTransactions(aiResponse);

        Map<String, Object> response = new HashMap<>();
        response.put("is_financial", aiResponse.get("is_financial"));
        response.put("transactions", aiResponse.get("transactions"));
        response.put("text", aiResponse.get("reply")); 
        return response;
    }

    @SuppressWarnings("unchecked")
    private void saveTransactions(Map<String, Object> aiResponse) {
    try {
        Boolean isFinancial = (Boolean) aiResponse.get("is_financial");
        if (isFinancial == null || !isFinancial) {
            System.out.println("⏭️ Не финансовая операция, пропускаем сохранение");
            return;
        }

        List<Map<String, Object>> transactions = (List<Map<String, Object>>) aiResponse.get("transactions");
        if (transactions == null || transactions.isEmpty()) {
            System.out.println("⚠️ Список транзакций пуст");
            return;
        }

        for (Map<String, Object> t : transactions) {
            Transaction transaction = new Transaction();
            
            // Базовые поля
            transaction.setAmount(((Number) t.get("amount")).doubleValue());
            transaction.setCategory((String) t.get("category"));
            transaction.setOwner((String) t.get("owner"));
            transaction.setReply((String) t.get("reply"));
            
            String typeStr = (String) t.get("type");
            if (typeStr != null) {
                try {
                    transaction.setType(TransactionType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️ Неизвестный тип: " + typeStr + ", ставлю EXPENSE по умолчанию");
                    transaction.setType(TransactionType.EXPENSE);
                }
            } else {
                transaction.setType(TransactionType.EXPENSE); // По умолчанию
            }
            
            transaction.setIsFinancial(true);

            transactionRepository.save(transaction);
            System.out.println("💾 Сохранено: " + transaction.getType() + " " 
                + t.get("amount") + "₽ | " + t.get("category") + " | " + t.get("owner"));
        }

    } catch (Exception e) {
        System.err.println("❌ Ошибка сохранения в БД: " + e.getMessage());
        e.printStackTrace();
    }
}
}