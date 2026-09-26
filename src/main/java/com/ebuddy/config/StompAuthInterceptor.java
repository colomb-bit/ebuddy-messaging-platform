package com.ebuddy.config;

import com.ebuddy.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class StompAuthInterceptor implements org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer {
    private final JwtService jwt;
    private final ConcurrentMap<String, Principal> sessions = new ConcurrentHashMap<String, Principal>();

    public StompAuthInterceptor(JwtService j) { jwt = j; }

    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                String sessionId = accessor.getSessionId();
                StompCommand command = accessor.getCommand();

                if (StompCommand.CONNECT.equals(command)) {
                    String header = accessor.getFirstNativeHeader("authorization");
                    if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException("AUTH_REQUIRED");
                    Principal principal = new UsernamePasswordAuthenticationToken(
                        jwt.subject(header.substring(7)), null, List.of());
                    accessor.setUser(principal);
                    if (sessionId != null) sessions.put(sessionId, principal);
                } else if (StompCommand.DISCONNECT.equals(command)) {
                    if (sessionId != null) sessions.remove(sessionId);
                } else if (accessor.getUser() == null && sessionId != null) {
                    Principal principal = sessions.get(sessionId);
                    if (principal != null) accessor.setUser(principal);
                }
                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }
        });
    }
}
