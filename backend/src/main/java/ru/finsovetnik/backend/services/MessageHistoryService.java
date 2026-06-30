package ru.finsovetnik.backend.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import ru.finsovetnik.backend.entity.Message;
import ru.finsovetnik.backend.entity.User;
import ru.finsovetnik.backend.repository.MessageRepository;
import ru.finsovetnik.backend.repository.UserRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class MessageHistoryService {
    
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    
    private static final int MAX_CONTEXT_MESSAGES = 100;
    
    public MessageHistoryService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }
    
    public void saveMessage(Long userId, String sender, String text) {
        Optional<User> result = userRepository.findById(userId);
        
        if (result.isEmpty()) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }
        
        User user = result.get();
        
        Message message = new Message();
        message.setUser(user);
        message.setSender(sender);
        message.setText(text);
        
        messageRepository.save(message);
    }
    
    /**
     * ✅ Получает последние N сообщений (от СТАРЫХ к НОВЫМ — для отображения в чате)
     */
    public List<Message> getRecentMessages(Long userId) {
        // Получаем от новых к старым
        List<Message> messages = messageRepository.findRecentMessages(
            userId, 
            PageRequest.of(0, MAX_CONTEXT_MESSAGES)
        );
        
        // ✅ Переворачиваем, чтобы старые были первыми
        Collections.reverse(messages);
        return messages;
    }
    
    /**
     * Форматирует историю для промпта AI
     */
    public String formatHistoryForPrompt(Long userId) {
        List<Message> messages = getRecentMessages(userId); 
        
        if (messages.isEmpty()) {
            return "История сообщений пуста.";
        }
        
        StringBuilder history = new StringBuilder();
        history.append("Предыдущая история разговора:\n\n");
        
        for (Message msg : messages) {
            String role = msg.getSender().equals("user") ? "Пользователь" : "Ассистент";
            history.append(role).append(": ").append(msg.getText()).append("\n");
        }
        
        return history.toString();
    }
    
    /**
     * Очищает историю для пользователя
     */
    public void clearHistory(Long userId) {
        List<Message> messages = messageRepository.findAllByUserId(userId);
        messageRepository.deleteAll(messages);
    }
}