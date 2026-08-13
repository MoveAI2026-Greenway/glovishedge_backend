package com.glovishedge.chat.controller;

import com.glovishedge.chat.dto.ChatResponse;
import com.glovishedge.chat.exception.ChatUnavailableException;
import com.glovishedge.chat.exception.InvalidChatRequestException;
import com.glovishedge.chat.service.ChatService;
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

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void validRequest_returns200() throws Exception {
        when(chatService.handle(any())).thenReturn(new ChatResponse("term", "답변", List.of(), Map.of()));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botId\":\"term\",\"message\":\"조항\",\"ctx\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bot").value("term"));
    }

    @Test
    void invalidBotId_returns400() throws Exception {
        when(chatService.handle(any())).thenThrow(new InvalidChatRequestException("알 수 없는 botId입니다"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botId\":\"nope\",\"message\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailable_returns503() throws Exception {
        when(chatService.handle(any())).thenThrow(new ChatUnavailableException("risk engine not configured"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botId\":\"risk\",\"message\":\"x\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
