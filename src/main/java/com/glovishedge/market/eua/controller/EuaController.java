package com.glovishedge.market.eua.controller;

import com.glovishedge.market.eua.dto.EuaResponse;
import com.glovishedge.market.eua.exception.EuaUnavailableException;
import com.glovishedge.market.eua.service.EuaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eua")
public class EuaController {

    private final EuaService euaService;

    public EuaController(EuaService euaService) {
        this.euaService = euaService;
    }

    @GetMapping
    public EuaResponse getEua() {
        return euaService.getEua();
    }

    @ExceptionHandler(EuaUnavailableException.class)
    public ResponseEntity<String> handleEuaUnavailable(EuaUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
