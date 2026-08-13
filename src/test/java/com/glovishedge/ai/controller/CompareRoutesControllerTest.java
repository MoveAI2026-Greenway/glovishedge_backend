package com.glovishedge.ai.controller;

import com.glovishedge.ai.dto.CompareRoutesRequest;
import com.glovishedge.ai.dto.CompareRoutesResponse;
import com.glovishedge.ai.exception.InvalidCompareRoutesRequestException;
import com.glovishedge.ai.exception.LlmGatewayNotConfiguredException;
import com.glovishedge.ai.service.CompareRoutesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompareRoutesController.class)
class CompareRoutesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompareRoutesService compareRoutesService;

    private static final String VALID_BODY = """
            {"routes":[
              {"key":"A","name":"싱가포르 경유","status":"limited","statusReason":"사유A",
               "totalUsd":2916,"baseFreightUsd":2840,"carbonCostUsd":67,"warRiskUsd":9,
               "leadTimeDays":32,"meetsDeadline":true},
              {"key":"C","name":"희망봉 우회","status":"best","statusReason":"사유C",
               "totalUsd":3286,"baseFreightUsd":3200,"carbonCostUsd":86,"warRiskUsd":0,
               "leadTimeDays":52,"meetsDeadline":true}
            ], "context":{"incoterm":"FOB","unit":"20ft","deadline":"2026-10-05"}}
            """;

    @Test
    void validRequest_returns200() throws Exception {
        when(compareRoutesService.compare(any())).thenReturn(new CompareRoutesResponse(
                "요약", Map.of("A", List.of("싸다")), Map.of("C", List.of()), "A", List.of()));

        mockMvc.perform(post("/api/compare-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("요약"))
                .andExpect(jsonPath("$.recommendation").value("A"));
    }

    @Test
    void invalidRequest_returns400() throws Exception {
        when(compareRoutesService.compare(any()))
                .thenThrow(new InvalidCompareRoutesRequestException("routes는 정확히 2개여야 합니다"));

        mockMvc.perform(post("/api/compare-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void llmNotConfigured_returns503() throws Exception {
        when(compareRoutesService.compare(any()))
                .thenThrow(new LlmGatewayNotConfiguredException("LLM Gateway가 구성되지 않았습니다"));

        mockMvc.perform(post("/api/compare-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
