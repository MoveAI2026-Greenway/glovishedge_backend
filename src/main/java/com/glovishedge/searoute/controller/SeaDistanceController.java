package com.glovishedge.searoute.controller;

import com.glovishedge.searoute.dto.SeaDistanceResponse;
import com.glovishedge.searoute.exception.InvalidCoordinateException;
import com.glovishedge.searoute.exception.SeaRouteUnavailableException;
import com.glovishedge.searoute.service.SeaDistanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sea-distance")
public class SeaDistanceController {

    private final SeaDistanceService seaDistanceService;

    public SeaDistanceController(SeaDistanceService seaDistanceService) {
        this.seaDistanceService = seaDistanceService;
    }

    @GetMapping
    public SeaDistanceResponse getSeaDistance(
            @RequestParam double fromLng,
            @RequestParam double fromLat,
            @RequestParam double toLng,
            @RequestParam double toLat) {
        return seaDistanceService.getSeaDistance(fromLng, fromLat, toLng, toLat);
    }

    @ExceptionHandler(InvalidCoordinateException.class)
    public ResponseEntity<String> handleInvalidCoordinate(InvalidCoordinateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(SeaRouteUnavailableException.class)
    public ResponseEntity<String> handleUnavailable(SeaRouteUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
