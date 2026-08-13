package com.glovishedge.market.eua.controller;

import com.glovishedge.market.eua.dto.EuaResponse;
import com.glovishedge.market.eua.exception.EuaUnavailableException;
import com.glovishedge.market.eua.service.EuaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EuaController.class)
class EuaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EuaService euaService;

    @Test
    void getEua_returnsContractFields() throws Exception {
        EuaResponse response = new EuaResponse(
                new BigDecimal("79.01"),
                new BigDecimal("1.77"),
                "2026-08-07",
                "EUR",
                "CO2.L",
                List.of(new BigDecimal("77.65"), new BigDecimal("79.01")),
                "Yahoo Finance · CO2.L",
                true,
                true
        );
        when(euaService.getEua()).thenReturn(response);

        mockMvc.perform(get("/api/eua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spot").value(79.01))
                .andExpect(jsonPath("$.dayChangePct").value(1.77))
                .andExpect(jsonPath("$.asOf").value("2026-08-07"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.symbol").value("CO2.L"))
                .andExpect(jsonPath("$.source").value("Yahoo Finance · CO2.L"))
                .andExpect(jsonPath("$.isProxy").value(true))
                .andExpect(jsonPath("$.isLive").value(true))
                .andExpect(jsonPath("$.recentCloses.length()").value(2));
    }

    @Test
    void getEua_whenUnavailable_returns503() throws Exception {
        when(euaService.getEua()).thenThrow(new EuaUnavailableException("EUA 시세를 가져올 수 없습니다", null));

        mockMvc.perform(get("/api/eua"))
                .andExpect(status().isServiceUnavailable());
    }
}
