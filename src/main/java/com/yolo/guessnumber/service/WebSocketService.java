package com.yolo.guessnumber.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolo.guessnumber.response.Response;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Service
public class WebSocketService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendJson(WebSocketSession session, Response response) throws IOException {
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
    }
}
