package com.glovishedge.chat.controller;

import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.chat.dto.ChatRequest;
import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import com.glovishedge.chat.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.handle(request);
    }

    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<String> handleInvalid(InvalidChatRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(ChatUnavailableException.class)
    public ResponseEntity<String> handleUnavailable(ChatUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(LlmGatewayException.class)
    public ResponseEntity<String> handleGatewayFailure(LlmGatewayException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
