package com.glovishedge.rag.controller;

import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.rag.dto.RagAskRequest;
import com.glovishedge.rag.dto.RagAskResponse;
import com.glovishedge.rag.exception.InvalidRagRequestException;
import com.glovishedge.rag.exception.RagUnavailableException;
import com.glovishedge.rag.service.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag/ask")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public RagAskResponse ask(@RequestBody RagAskRequest request) {
        return ragService.ask(request);
    }

    @ExceptionHandler(InvalidRagRequestException.class)
    public ResponseEntity<String> handleInvalid(InvalidRagRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(RagUnavailableException.class)
    public ResponseEntity<String> handleUnavailable(RagUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(LlmGatewayException.class)
    public ResponseEntity<String> handleGatewayFailure(LlmGatewayException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
