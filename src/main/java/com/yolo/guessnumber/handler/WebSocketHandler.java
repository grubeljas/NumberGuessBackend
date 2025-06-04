package com.yolo.guessnumber.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolo.guessnumber.playerBet.PlayerBetDTO;
import com.yolo.guessnumber.service.GameService;
import com.yolo.guessnumber.validator.RequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final RequestValidator requestValidator;
    private final GameService gameService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public WebSocketHandler(RequestValidator requestValidator, GameService gameService) {
        this.requestValidator = requestValidator;
        this.gameService = gameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        gameService.addSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            requestValidator.isValidRequest(message.getPayload());
            String clientMsg = message.getPayload();
            System.out.println("Received: " + clientMsg);
            PlayerBetDTO playerBetDTO = objectMapper.readValue(clientMsg, PlayerBetDTO.class);
            session.sendMessage(new TextMessage("Server received: " + clientMsg));
            gameService.processPlayerBet(session.getId(), playerBetDTO);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gameService.removeSession(session);
        System.out.println("Connection closed: " + session.getId());
    }
}