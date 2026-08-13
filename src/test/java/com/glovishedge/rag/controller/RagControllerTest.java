package com.glovishedge.rag.controller;

import com.glovishedge.rag.dto.RagAskResponse;
import com.glovishedge.rag.dto.RagAskResult;
import com.glovishedge.rag.exception.InvalidRagRequestException;
import com.glovishedge.rag.exception.RagUnavailableException;
import com.glovishedge.rag.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @Test
    void validRequest_returns200() throws Exception {
        when(ragService.ask(any())).thenReturn(new RagAskResponse(
                "What is EU ETS?", List.of(), new RagAskResult("답변", List.of(), "법률 자문이 아닙니다")));

        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"EU ETS가 뭐야?\",\"k\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.answer").value("답변"));
    }

    @Test
    void blankQuestion_returns400() throws Exception {
        when(ragService.ask(any())).thenThrow(new InvalidRagRequestException("question은 필수입니다"));

        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ragUnavailable_returns503() throws Exception {
        when(ragService.ask(any())).thenThrow(new RagUnavailableException("RAG가 비활성화되어 있습니다"));

        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"EU ETS가 뭐야?\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
