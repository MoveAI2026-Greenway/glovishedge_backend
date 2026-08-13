package com.glovishedge.searoute.controller;

import com.glovishedge.searoute.dto.RouteResult;
import com.glovishedge.searoute.dto.SeaDistanceResponse;
import com.glovishedge.searoute.exception.InvalidCoordinateException;
import com.glovishedge.searoute.exception.SeaRouteUnavailableException;
import com.glovishedge.searoute.service.SeaDistanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeaDistanceController.class)
class SeaDistanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeaDistanceService seaDistanceService;

    @Test
    void validRequest_returns200() throws Exception {
        when(seaDistanceService.getSeaDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new SeaDistanceResponse(Map.of(
                        "A", new RouteResult(11259.0, List.of(List.of(35.1, 129.04))))));

        mockMvc.perform(get("/api/sea-distance")
                        .param("fromLng", "129.04").param("fromLat", "35.1")
                        .param("toLng", "10").param("toLat", "53.55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.A.nm").value(11259.0));
    }

    @Test
    void invalidCoordinate_returns400() throws Exception {
        when(seaDistanceService.getSeaDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new InvalidCoordinateException("from 좌표가 유효하지 않습니다"));

        mockMvc.perform(get("/api/sea-distance")
                        .param("fromLng", "999").param("fromLat", "0")
                        .param("toLng", "10").param("toLat", "53.55"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sidecarUnavailable_returns503() throws Exception {
        when(seaDistanceService.getSeaDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new SeaRouteUnavailableException("연결할 수 없습니다"));

        mockMvc.perform(get("/api/sea-distance")
                        .param("fromLng", "129.04").param("fromLat", "35.1")
                        .param("toLng", "10").param("toLat", "53.55"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void missingQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/sea-distance")
                        .param("fromLng", "129.04").param("fromLat", "35.1"))
                .andExpect(status().isBadRequest());
    }
}
