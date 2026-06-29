package ru.finsovetnik.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;

import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import ru.finsovetnik.backend.services.JwtService;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public final JwtService jwtService;

     public WebSocketConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // ← Важно: Patterns, а не Origins
                .withSockJS();                 // ← Обязательно! Без этого SockJS не работает
    }

    @Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
            
            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("Токен не предоставлен");
                }
                
                String token = authHeader.substring(7);
                
                try {
                    //  Проверяем токен и извлекаем user_id
                    Long userId = jwtService.validateAccessToken(token);
                    accessor.setUser(userId::toString);
                    System.out.println("✅ WebSocket: пользователь аутентифицирован, user_id=" + userId);
                } catch (Exception e) {
                    System.err.println("❌ WebSocket: невалидный токен: " + e.getMessage());
                    throw new IllegalArgumentException("Невалидный токен");
                }
            }
            
            return message;
        }
    });
}
}