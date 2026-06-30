package ru.finsovetnik.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import ru.finsovetnik.backend.entity.Transaction;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.enums.TransactionType;
import ru.finsovetnik.backend.repository.TransactionRepository;
import ru.finsovetnik.backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ReceiptService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MessageHistoryService messageHistoryService;
    private final RestTemplate restTemplate;
    private final String aiServiceUrl;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm"),
    };

    public ReceiptService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            MessageHistoryService messageHistoryService,
            @Value("${ai.service.url}") String aiServiceUrl) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.messageHistoryService = messageHistoryService;
        this.restTemplate = new RestTemplate();
        this.aiServiceUrl = aiServiceUrl;
    }

    // =========================================================================
    // 1. СКАНИРОВАНИЕ QR (Создаем "Черновик" чека)
    // =========================================================================
    public Map<String, Object> parseAndSave(Long userId, String qrData) {
        Map<String, String> params = parseQrString(qrData);
        String dateStr = params.get("t");
        String amountStr = params.get("s");

        if (dateStr == null || amountStr == null) {
            throw new RuntimeException("Неверный формат QR-кода");
        }

        String formattedDate = parseDateFlexible(dateStr);
        double amount = Double.parseDouble(amountStr.replace(",", "."));
        User user = userRepository.findById(userId).orElseThrow();

        //  Генерируем уникальный ID для этого чека
        String receiptId = UUID.randomUUID().toString();

        //  Сохраняем "Родительскую" транзакцию (Черновик)
        Transaction draft = new Transaction();
        draft.setUser(user);
        draft.setAmount(amount);
        draft.setCategory("Чек (ожидает детализации)");
        draft.setType(TransactionType.EXPENSE);
        draft.setIsFinancial(true);
        draft.setReceiptId(receiptId);
        draft.setReply(String.format("QR-чек на %.2f₽ от %s. Отправь фото для детализации.", amount, formattedDate));

        transactionRepository.save(draft);

        messageHistoryService.saveMessage(userId, "user", "Сканирование QR-кода чека");
        messageHistoryService.saveMessage(userId, "ai", draft.getReply());

        Map<String, Object> result = new HashMap<>();
        result.put("amount", amount);
        result.put("date", formattedDate);
        result.put("receipt_id", receiptId); 

        return result;
    }

    // =========================================================================
    // 2. ЗАГРУЗКА ФОТО + УМНОЕ СЛИЯНИЕ
    // =========================================================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzePhotoAndMerge(Long userId, MultipartFile file, String receiptId) throws Exception {
        User user = userRepository.findById(userId).orElseThrow();

        // 1. Отправляем фото в Python (OCR + LLM)
        Map<String, Object> analysis = sendPhotoToAiService(file);
        
        List<Map<String, Object>> items = (List<Map<String, Object>>) analysis.get("items");
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("AI не смог распознать товары на чеке");
        }

        // 2. Получаем точную сумму из QR (если он был отсканирован)
        double qrTotalAmount = 0;
        if (receiptId != null && !receiptId.isBlank()) {
            Optional<Transaction> draftOpt = transactionRepository.findByReceiptId(receiptId);
            if (draftOpt.isPresent()) {
                qrTotalAmount = draftOpt.get().getAmount();
                // Удаляем черновик, чтобы не дублировать сумму в балансе
                transactionRepository.delete(draftOpt.get());
            }
        }

        // Если ID не передан, создаем новый
        String finalReceiptId = (receiptId != null && !receiptId.isBlank()) ? receiptId : UUID.randomUUID().toString();

        // 3. Создаем детальные транзакции с математической коррекцией
        double currentSum = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setReceiptId(finalReceiptId);
            transaction.setType(TransactionType.EXPENSE);
            transaction.setIsFinancial(true);
            
            String name = (String) item.getOrDefault("name", "Товар");
            String category = (String) item.getOrDefault("category", "Другое");
            double itemPrice = item.get("sum") != null ? ((Number) item.get("sum")).doubleValue() : 0;
            int quantity = item.get("quantity") != null ? ((Number) item.get("quantity")).intValue() : 1;

            //  Для ПОСЛЕДНЕГО товара подгоняем сумму под точный QR
            if (i == items.size() - 1 && qrTotalAmount > 0) {
                itemPrice = qrTotalAmount - currentSum;
            }

            transaction.setAmount(itemPrice);
            transaction.setCategory(category);
            transaction.setReply(String.format("%s x%d (%s)", name, quantity, category));
            
            transactionRepository.save(transaction);
            currentSum += itemPrice;
        }

        // 4. Сохраняем в историю чата
        String store = (String) analysis.getOrDefault("store_name", "магазина");
        String summary = String.format(
            "📸 Чек из %s распознан!\n💰 Всего: %.2f₽\n🛍️ Товаров: %d\n\nДетали:\n%s",
            store,
            qrTotalAmount > 0 ? qrTotalAmount : currentSum,
            items.size(),
            buildItemsList(items)
        );

        messageHistoryService.saveMessage(userId, "user", "📸 Загрузка фото чека");
        messageHistoryService.saveMessage(userId, "ai", summary);

        return analysis;
    }

    // =========================================================================
    // Вспомогательные методы
    // =========================================================================
    private Map<String, Object> sendPhotoToAiService(MultipartFile file) throws Exception {
        String url = aiServiceUrl + "/receipt/analyze-photo";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() { return file.getOriginalFilename(); }
        });

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        
        if (response.getBody() == null) throw new RuntimeException("Пустой ответ от AI");
        return response.getBody();
    }

    private String buildItemsList(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : items) {
            sb.append("- ").append(item.get("name")).append(": ").append(item.get("sum")).append("₽\n");
        }
        return sb.toString();
    }

    private String parseDateFlexible(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateStr, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (DateTimeParseException e) { continue; }
        }
        return dateStr;
    }

    private Map<String, String> parseQrString(String qrData) {
        Map<String, String> params = new HashMap<>();
        for (String pair : qrData.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) params.put(kv[0], kv[1]);
        }
        return params;
    }
}