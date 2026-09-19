package com.ebuddy.controller;

import com.ebuddy.service.MessageService;
import com.ebuddy.web.ApiModels;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
public class StompMessageController {
    private final MessageService messages;

    public StompMessageController(MessageService messages) {
        this.messages = messages;
    }

    @MessageMapping("/message")
    public void send(Principal principal, @Valid @Payload ApiModels.SendRequest request) {
        if (principal == null) {
            throw new IllegalArgumentException("AUTH_REQUIRED");
        }
        messages.send(UUID.fromString(principal.getName()), request);
    }

    @MessageMapping("/receipt")
    public void receipt(Principal principal, @Payload Map<String, String> request) {
        if (principal == null || request == null || request.get("message_id") == null || request.get("state") == null) {
            throw new IllegalArgumentException("INVALID_RECEIPT");
        }
        messages.receipt(UUID.fromString(principal.getName()), UUID.fromString(request.get("message_id")), request.get("state"));
    }
}
