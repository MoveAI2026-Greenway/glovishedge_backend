package com.glovishedge.market.fx.controller;

import com.glovishedge.market.fx.dto.FxResponse;
import com.glovishedge.market.fx.exception.FxUnavailableException;
import com.glovishedge.market.fx.service.FxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FxController.class)
class FxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FxService fxService;

    @Test
    void getFx_returnsContractFields() throws Exception {
        FxResponse response = new FxResponse(
                new BigDecimal("1.1548157549203812"),
                new BigDecimal("1630.83"),
                "09 Aug 2026",
                "open.er-api.com",
                "Rates By Exchange Rate API",
                "https://www.exchangerate-api.com"
        );
        when(fxService.getFx()).thenReturn(response);

        mockMvc.perform(get("/api/fx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usdPerEur").value(1.1548157549203812))
                .andExpect(jsonPath("$.krwPerEur").value(1630.83))
                .andExpect(jsonPath("$.asOf").value("09 Aug 2026"))
                .andExpect(jsonPath("$.source").value("open.er-api.com"))
                .andExpect(jsonPath("$.attribution").value("Rates By Exchange Rate API"))
                .andExpect(jsonPath("$.attributionUrl").value("https://www.exchangerate-api.com"));
    }

    @Test
    void getFx_whenUnavailable_returns503() throws Exception {
        when(fxService.getFx()).thenThrow(new FxUnavailableException("환율 정보를 가져올 수 없습니다", null));

        mockMvc.perform(get("/api/fx"))
                .andExpect(status().isServiceUnavailable());
    }
}
