package ru.finsovetnik.backend.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TtsController {

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;

    public TtsController(@Value("${ai.service.url}") String aiServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.aiServiceUrl = aiServiceUrl;
    }

    /**
     * Прокси для Text-to-Speech (Озвучка текста)
     * Принимает текст → отправляет в Python → возвращает аудио (base64)
     */
    @PostMapping("/tts")
    public ResponseEntity<Object> textToSpeech(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            if (text == null || text.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Текст обязателен"));
            }

            String url = aiServiceUrl + "/tts";

            // Настраиваем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Формируем запрос
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(request, headers);

            // Отправляем в Python и получаем ответ
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            // Возвращаем ответ от Python на фронтенд
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            System.err.println("❌ Ошибка TTS: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка генерации голоса"));
        }
    }
}