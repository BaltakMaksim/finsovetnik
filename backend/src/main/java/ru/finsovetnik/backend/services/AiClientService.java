package ru.finsovetnik.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.enums.TransactionType;
import ru.finsovetnik.backend.repository.TransactionRepository;
import ru.finsovetnik.backend.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiClientService {

    private static final Logger logger = LoggerFactory.getLogger(AiClientService.class);

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MessageHistoryService messageHistoryService;

    public AiClientService(
            @Value("${ai.service.url}") String aiServiceUrl,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            MessageHistoryService messageHistoryService) {
        this.restTemplate = new RestTemplate();
        this.aiServiceUrl = aiServiceUrl;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.messageHistoryService = messageHistoryService;
        logger.info("🤖 AI Client initialized. Target URL: {}", aiServiceUrl);
    }

    /**
     * Обработка сообщения с контекстом истории
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseExpense(String text, Long userId) {
        // 1. Сохраняем сообщение пользователя в историю
        messageHistoryService.saveMessage(userId, "user", text);
        
        // 2. Получаем историю для контекста
        String history = messageHistoryService.formatHistoryForPrompt(userId);
        
        // 3. Отправляем в Python текст + историю
        Map<String, Object> aiResponse = callAiParseService(text, history);
        
        // 4. Сохраняем ответ AI в историю
        String aiReply = (String) aiResponse.get("reply");
        if (aiReply != null && !aiReply.isBlank()) {
            messageHistoryService.saveMessage(userId, "ai", aiReply);
        }

        // 5. Сохраняем транзакции
        saveTransactions(aiResponse, userId);

        // 6. Формируем ответ для фронтенда
        Map<String, Object> response = new HashMap<>();
        response.put("is_financial", aiResponse.get("is_financial"));
        response.put("transactions", aiResponse.get("transactions"));
        response.put("text", aiReply);
        return response;
    }

    /**
     * Вызов AI сервиса для парсинга расходов
     * Передаём только данные, промпт формируется в Python
     */
    private Map<String, Object> callAiParseService(String text, String history) {
        String url = aiServiceUrl + "/api/parse";
        
        logger.info("📤 Отправка в AI: text={}, history length={}", text, history.length());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // ✅ Передаём текст и историю
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("history", history);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);
            
            Map<String, Object> aiResponse = responseEntity.getBody();
            logger.info("📥 Получено от AI: {}", aiResponse);
            
            return aiResponse != null ? aiResponse : getDefaultErrorResponse();
            
        } catch (Exception e) {
            logger.error("❌ Ошибка вызова AI parse: {}", e.getMessage(), e);
            return getDefaultErrorResponse();
        }
    }

    /**
     * Сохранение транзакций в БД
     */
    @SuppressWarnings("unchecked")
    private void saveTransactions(Map<String, Object> aiResponse, Long userId) {
        try {
            Boolean isFinancial = (Boolean) aiResponse.get("is_financial");
            if (isFinancial == null || !isFinancial) {
                logger.debug("⏭️ Не финансовая операция, пропускаем сохранение");
                return;
            }

            List<Map<String, Object>> transactions = (List<Map<String, Object>>) aiResponse.get("transactions");
            if (transactions == null || transactions.isEmpty()) {
                logger.warn("⚠️ Список транзакций пуст");
                return;
            }

            // Загружаем пользователя один раз
            User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

            for (Map<String, Object> t : transactions) {
                Transaction transaction = new Transaction();
                
                transaction.setAmount(((Number) t.get("amount")).doubleValue());
                transaction.setCategory((String) t.get("category"));
                transaction.setOwner((String) t.get("owner"));
                transaction.setReply((String) t.get("reply"));
                
                String typeStr = (String) t.get("type");
                if (typeStr != null) {
                    try {
                        transaction.setType(TransactionType.valueOf(typeStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        transaction.setType(TransactionType.EXPENSE);
                    }
                } else {
                    transaction.setType(TransactionType.EXPENSE);
                }
                
                transaction.setIsFinancial(true);

                // ✅ Привязываем к пользователю (используем загруженного)
                if (user != null) {
                    transaction.setUser(user);
                }

                transactionRepository.save(transaction);
                logger.info("💾 Сохранено: {} {}₽ | {} | user_id={}", 
                    transaction.getType(), t.get("amount"), t.get("category"), userId);
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка сохранения в БД: {}", e.getMessage(), e);
        }
    }

    /**
     * Чат для аутентификации (без сохранения в историю)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> authChat(String sessionId, String text) {
        String url = aiServiceUrl + "/api/auth/chat";
        
        logger.info("🔐 Отправка в AI auth: sessionId={}, text={}", sessionId, text);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId);
            requestBody.put("text", text);
            
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);
            
            Map<String, Object> aiResponse = responseEntity.getBody();
            logger.info("🔐 Получено от AI auth: {}", aiResponse);
            
            return aiResponse != null ? aiResponse : getDefaultAuthErrorResponse();
            
        } catch (Exception e) {
            logger.error("❌ Ошибка вызова AI auth: {}", e.getMessage(), e);
            return getDefaultAuthErrorResponse();
        }
    }

    /**
     * Дефолтный ответ при ошибке парсинга
     */
    private Map<String, Object> getDefaultErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("is_financial", false);
        response.put("transactions", List.of());
        response.put("reply", "Произошла ошибка при обработке. Попробуйте ещё раз!");
        return response;
    }

    /**
     * Дефолтный ответ при ошибке auth
     */
    private Map<String, Object> getDefaultAuthErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("reply", "Произошла ошибка при обработке. Попробуйте ещё раз.");
        response.put("state", "ERROR");
        return response;
    }
}