package ru.finsovetnik.backend.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranscribeController {

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;

    public TranscribeController(@Value("${ai.service.url}") String aiServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.aiServiceUrl = aiServiceUrl;
    }

    /**
     * Прокси для распознавания речи (Speech-to-Text).
     * Принимает аудиофайл от React → отправляет в Python → возвращает текст.
     */
    @PostMapping("/transcribe")
    public ResponseEntity<Object> transcribe(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Аудиофайл пуст"));
            }

            System.out.println("🎤 Получен аудиофайл: " + file.getOriginalFilename() 
                + ", размер: " + file.getSize() + " байт");

            String url = aiServiceUrl + "/transcribe";

            // Настраиваем заголовки для multipart/form-data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Формируем тело запроса с файлом
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file.getResource());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Отправляем в Python и получаем ответ
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            System.out.println("🎤 Распознанный текст: " + response.getBody());

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            System.err.println("❌ Ошибка транскрибации: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка распознавания речи"));
        }
    }
}