package com.glovishedge.market.fx.controller;

import com.glovishedge.market.fx.dto.FxResponse;
import com.glovishedge.market.fx.exception.FxUnavailableException;
import com.glovishedge.market.fx.service.FxService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx")
public class FxController {

    private final FxService fxService;

    public FxController(FxService fxService) {
        this.fxService = fxService;
    }

    @GetMapping
    public FxResponse getFx() {
        return fxService.getFx();
    }

    @ExceptionHandler(FxUnavailableException.class)
    public ResponseEntity<String> handleFxUnavailable(FxUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
