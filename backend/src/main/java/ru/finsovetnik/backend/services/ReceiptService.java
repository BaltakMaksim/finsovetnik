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

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final String DRAFT_CATEGORY = "Чек (ожидает детализации)";

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
    String fn = params.get("fn");
    String docNumber = params.get("i");
    String fiscalSign = params.get("fp");

    if (dateStr == null || amountStr == null) {
        throw new RuntimeException("Неверный формат QR-кода");
    }

    String fiscalId = null;
    if (fn != null && docNumber != null && fiscalSign != null) {
        fiscalId = String.format("%s_%s_%s", fn, docNumber, fiscalSign);
    }

    //  Если есть старый черновик с таким fiscalId — удаляем его
    if (fiscalId != null) {
        Optional<Transaction> oldDraft = transactionRepository.findByFiscalId(fiscalId);
        if (oldDraft.isPresent() && DRAFT_CATEGORY.equals(oldDraft.get().getCategory())) {
            transactionRepository.delete(oldDraft.get());
            System.out.println("🗑️ Старый черновик удалён");
        }
        
        // Проверяем только ГОТОВЫЕ транзакции
        Optional<Transaction> existing = transactionRepository.findCompletedByFiscalId(fiscalId);
        if (existing.isPresent()) {
            Transaction existingTx = existing.get();
            String existingDate = formatCreatedAt(existingTx.getCreatedAt());
            
            throw new RuntimeException(
                String.format(
                    "Этот чек уже учтён! 🧾\n💰 Сумма: %.2f₽\n📅 Дата: %s\n\nПопробуйте отсканировать другой чек.", 
                    existingTx.getAmount(),
                    existingDate
                )
            );
        }
    }

    String formattedDate = parseDateFlexible(dateStr);
    double amount = Double.parseDouble(amountStr.replace(",", "."));
    User user = userRepository.findById(userId).orElseThrow();

    String receiptId = UUID.randomUUID().toString();

    Transaction draft = new Transaction();
    draft.setUser(user);
    draft.setAmount(amount);
    draft.setCategory(DRAFT_CATEGORY);
    draft.setType(TransactionType.EXPENSE);
    draft.setIsFinancial(true);
    draft.setReceiptId(receiptId);
    draft.setFiscalId(fiscalId);
    draft.setReply(String.format("QR-чек на %.2f₽ от %s. Загрузите фото для детализации.", amount, formattedDate));

    transactionRepository.save(draft);

    messageHistoryService.saveMessage(userId, "user", "Сканирование QR-кода чека");
    messageHistoryService.saveMessage(userId, "ai", draft.getReply());

    Map<String, Object> result = new HashMap<>();
    result.put("amount", amount);
    result.put("date", formattedDate);
    result.put("receipt_id", receiptId);
    result.put("fiscal_id", fiscalId);

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

        // ✅ ИЗВЛЕКАЕМ ФИСКАЛЬНЫЕ ДАННЫЕ ИЗ ФОТО
        Map<String, Object> fiscalData = (Map<String, Object>) analysis.get("fiscal_data");
        String photoFiscalId = null;
        
        if (fiscalData != null) {
            String fn = fiscalData.get("fn") != null ? fiscalData.get("fn").toString() : null;
            String docNumber = fiscalData.get("i") != null ? fiscalData.get("i").toString() : null;
            String fiscalSign = fiscalData.get("fp") != null ? fiscalData.get("fp").toString() : null;
            
            if (fn != null && !fn.isBlank() && 
                docNumber != null && !docNumber.isBlank() && 
                fiscalSign != null && !fiscalSign.isBlank()) {
                photoFiscalId = String.format("%s_%s_%s", fn, docNumber, fiscalSign);
            }
        }

        // 2. Получаем точную сумму и fiscalId из QR-черновика (если он был)
        double qrTotalAmount = 0;
        String fiscalId = null;
        
        if (receiptId != null && !receiptId.isBlank()) {
            Optional<Transaction> draftOpt = transactionRepository.findByReceiptId(receiptId);
            if (draftOpt.isPresent()) {
                Transaction draft = draftOpt.get();
                qrTotalAmount = draft.getAmount();
                fiscalId = draft.getFiscalId(); // ✅ Берём fiscalId из QR
                transactionRepository.delete(draft); // ✅ Удаляем черновик
                System.out.println("🗑️ Черновик удалён, создаём детальные транзакции");
            }
        }

        //  Если не было QR, но LLM извлёк фискальные данные — используем их
        if (fiscalId == null && photoFiscalId != null) {
            // Проверяем, нет ли уже ГОТОВЫХ транзакций с таким fiscalId
            Optional<Transaction> existing = transactionRepository.findCompletedByFiscalId(photoFiscalId);
            if (existing.isPresent()) {
                Transaction existingTx = existing.get();
                String existingDate = formatCreatedAt(existingTx.getCreatedAt());
                
                throw new RuntimeException(
                    String.format(
                        "Этот чек уже учтён! 🧾\n💰 Сумма: %.2f₽\n📅 Дата: %s\n\nПопробуйте другой чек.", 
                        existingTx.getAmount(),
                        existingDate
                    )
                );
            }
            fiscalId = photoFiscalId;
        }

        // Fallback: если фискальных данных нет вообще — используем хеш файла
        if (fiscalId == null) {
            String fileHash = calculateFileHash(file);
            fiscalId = "photo_" + fileHash;
        }

        String finalReceiptId = (receiptId != null && !receiptId.isBlank()) ? receiptId : UUID.randomUUID().toString();

        // 3. Создаем детальные транзакции
        double currentSum = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setReceiptId(finalReceiptId);
            transaction.setFiscalId(fiscalId);
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
    // 3. СОХРАНЕНИЕ ТРАНЗАКЦИИ ИЗ ЧАТА
    // =========================================================================
    @SuppressWarnings("unchecked")
    public void saveTransactionFromChat(Long userId, Map<String, Object> transactionData) {
        Number amount = (Number) transactionData.get("amount");
        if (amount == null || amount.doubleValue() <= 0) {
            System.out.println("⚠️ Пропускаем сохранение — сумма некорректна: " + amount);
            return;
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        
        String category = (String) transactionData.get("category");
        String typeStr = (String) transactionData.get("type");
        
        transaction.setAmount(amount.doubleValue());
        transaction.setCategory(category != null ? category : "Другое");
        transaction.setType(TransactionType.valueOf(typeStr != null ? typeStr : "EXPENSE"));
        transaction.setIsFinancial(true);
        transaction.setReply("Зафиксировано из чата");
        
        transactionRepository.save(transaction);
    }

    // =========================================================================
    // Вспомогательные методы
    // =========================================================================
    
    private String calculateFileHash(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] fileBytes = file.getBytes();
        byte[] hashBytes = digest.digest(fileBytes);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

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

    private String formatCreatedAt(Object createdAt) {
        if (createdAt == null) return "неизвестно";
        
        try {
            if (createdAt instanceof LocalDateTime) {
                return ((LocalDateTime) createdAt).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else if (createdAt instanceof java.util.Date) {
                LocalDateTime ldt = ((java.util.Date) createdAt).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                return ldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
        } catch (Exception e) {
            return "неизвестно";
        }
        
        return "неизвестно";
    }
}