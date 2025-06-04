package com.yolo.guessnumber.service;

import com.yolo.guessnumber.gamelogic.GuessNumber;
import com.yolo.guessnumber.playerBet.PlayerBet;
import com.yolo.guessnumber.playerBet.PlayerBetDTO;
import com.yolo.guessnumber.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameServiceTest {

    private GameService gameService;
    private WebSocketService webSocketServiceMock;

    @BeforeEach
    void setUp() {
        GuessNumber guessNumberMock = mock(GuessNumber.class);
        webSocketServiceMock = mock(WebSocketService.class);
        gameService = new GameService(guessNumberMock, webSocketServiceMock, 0);
    }

    @Test
    void testProcessPlayerBet_Success() throws IOException {
        String sessionId = "session1";
        PlayerBetDTO playerBetDTO = new PlayerBetDTO("JohnDoe", 100d, 5);

        WebSocketSession sessionMock = mock(WebSocketSession.class);
        when(sessionMock.getId()).thenReturn(sessionId);
        when(sessionMock.isOpen()).thenReturn(true);
        gameService.getSessions().put(sessionId, sessionMock);

        gameService.startBettingPhase();
        gameService.processPlayerBet(sessionId, playerBetDTO);

        assertTrue(gameService.getPlayerBets().containsKey(sessionId));
        assertEquals(100, gameService.getPlayerBets().get(sessionId).getBetAmount());
        assertEquals(5, gameService.getPlayerBets().get(sessionId).getPickedNumber());
    }

    @Test
    void testAddSession_Success() {
        WebSocketSession sessionMock = mock(WebSocketSession.class);
        when(sessionMock.getId()).thenReturn("session1");
        when(sessionMock.isOpen()).thenReturn(true);

        gameService.addSession(sessionMock);

        assertTrue(gameService.getSessions().containsKey("session1"));
    }

    @Test
    void testBroadcastMessage() throws IOException {
        WebSocketSession sessionMock = mock(WebSocketSession.class);
        when(sessionMock.getId()).thenReturn("session1");
        when(sessionMock.isOpen()).thenReturn(true);
        gameService.getSessions().put("session1", sessionMock);

        Response message = new Response("TEST", "Test message");
        gameService.broadcastMessage(message);

        verify(webSocketServiceMock, times(1)).sendJson(sessionMock, message);
    }


    @Test
    void testCalculateWinners() {
        gameService.getPlayerBets().put("session1", new PlayerBet("Player1", 100, 5));
        gameService.getPlayerBets().put("session2", new PlayerBet("Player2", 200, 5));
        gameService.getPlayerBets().put("session3", new PlayerBet("Player3", 150, 3));

        gameService.getPlayerBets().get("session1").setWinAmount(990);
        gameService.getPlayerBets().get("session2").setWinAmount(1980);
        gameService.getPlayerBets().get("session3").setWinAmount(0);

        List<Response.Winner> winners = gameService.calculateWinners();

        assertEquals(2, winners.size());
        assertEquals("Player2", winners.get(0).getNickname());
        assertEquals(1980, winners.get(0).getWinning());
        assertEquals("Player1", winners.get(1).getNickname());
        assertEquals(990, winners.get(1).getWinning());
    }
}