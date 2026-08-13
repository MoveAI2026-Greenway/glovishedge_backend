package com.glovishedge.ai.controller;

import com.glovishedge.ai.dto.CompareRoutesRequest;
import com.glovishedge.ai.dto.CompareRoutesResponse;
import com.glovishedge.ai.exception.InvalidCompareRoutesRequestException;
import com.glovishedge.ai.exception.LlmGatewayException;
import com.glovishedge.ai.exception.LlmGatewayNotConfiguredException;
import com.glovishedge.ai.service.CompareRoutesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compare-routes")
public class CompareRoutesController {

    private final CompareRoutesService compareRoutesService;

    public CompareRoutesController(CompareRoutesService compareRoutesService) {
        this.compareRoutesService = compareRoutesService;
    }

    @PostMapping
    public CompareRoutesResponse compareRoutes(@RequestBody CompareRoutesRequest request) {
        return compareRoutesService.compare(request);
    }

    @ExceptionHandler(InvalidCompareRoutesRequestException.class)
    public ResponseEntity<String> handleInvalidRequest(InvalidCompareRoutesRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(LlmGatewayNotConfiguredException.class)
    public ResponseEntity<String> handleNotConfigured(LlmGatewayNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(LlmGatewayException.class)
    public ResponseEntity<String> handleGatewayFailure(LlmGatewayException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
