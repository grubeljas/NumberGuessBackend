package com.yolo.guessnumber.service;

import com.yolo.guessnumber.gamelogic.GuessNumber;
import com.yolo.guessnumber.playerBet.PlayerBetDTO;
import com.yolo.guessnumber.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceIntegrationTest {

    @Mock
    private GuessNumber guessNumber;

    @Mock
    private WebSocketService webSocketService;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @Mock
    private WebSocketSession session3;

    private GameService gameService;

    @BeforeEach
    void setUp() {
        lenient().when(session1.getId()).thenReturn("session1");
        lenient().when(session1.isOpen()).thenReturn(true);
        lenient().when(session2.getId()).thenReturn("session2");
        lenient().when(session2.isOpen()).thenReturn(true);
        lenient().when(session3.getId()).thenReturn("session3");
        lenient().when(session3.isOpen()).thenReturn(true);

        lenient().when(guessNumber.getResult()).thenReturn(5);

        gameService = new GameService(guessNumber, webSocketService, 1);
    }

    @Test
    void testCompleteGameFlow() throws Exception {
        gameService.addSession(session1);
        gameService.addSession(session2);
        gameService.addSession(session3);

        assertThat(gameService.getSessions()).hasSize(3);

        Thread.sleep(1100);

        assertThat(gameService.isBettingPhase()).isTrue();
        assertThat(gameService.isGameInProgress()).isTrue();

        PlayerBetDTO bet1 = new PlayerBetDTO("Player1", 100.0, 5);
        PlayerBetDTO bet2 = new PlayerBetDTO("Player2", 50.0, 3);
        PlayerBetDTO bet3 = new PlayerBetDTO("Player3", 200.0, 5);

        gameService.processPlayerBet("session1", bet1);
        gameService.processPlayerBet("session2", bet2);
        gameService.processPlayerBet("session3", bet3);

        assertThat(gameService.getPlayerBets()).hasSize(3);

        Thread.sleep(1000);

        assertThat(gameService.isBettingPhase()).isFalse();

        assertThat(gameService.getPlayerBets()).isEmpty();
        assertThat(gameService.isGameInProgress()).isFalse();

        verify(webSocketService, atLeast(3)).sendJson(any(WebSocketSession.class), any(Response.class));

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(webSocketService, atLeastOnce()).sendJson(eq(session1), responseCaptor.capture());

        boolean foundRoundStart = responseCaptor.getAllValues().stream()
                .anyMatch(response -> "ROUND_START".equals(response.getType()));
        assertThat(foundRoundStart).isTrue();

        verify(guessNumber, atLeastOnce()).getResult();
    }

    @Test
    void testSessionManagement() throws IOException {
        gameService.addSession(session1);
        assertThat(gameService.getSessions()).containsKey("session1");

        verify(webSocketService).sendJson(eq(session1), argThat(response ->
                "WELCOME".equals(response.getType())
        ));

        gameService.removeSession(session1);
        assertThat(gameService.getSessions()).doesNotContainKey("session1");

        WebSocketSession closedSession = mock(WebSocketSession.class);
        when(closedSession.isOpen()).thenReturn(false);
        gameService.addSession(closedSession);
        assertThat(gameService.getSessions()).isEmpty();

        gameService.addSession(null);
        assertThat(gameService.getSessions()).isEmpty();
    }

    @Test
    void testBettingPhaseValidation() throws Exception {
        gameService.addSession(session1);

        Thread.sleep(1000);

        PlayerBetDTO validBet = new PlayerBetDTO("TestPlayer", 100.0, 5);

        gameService.processPlayerBet("session1", validBet);
        assertThat(gameService.getPlayerBets()).hasSize(1);

        Thread.sleep(1500);

        gameService.addSession(session2);
        gameService.processPlayerBet("session2", validBet);
        verify(webSocketService).sendJson(eq(session2), argThat(response ->
                "ERROR".equals(response.getType()) &&
                        response.getMessage().contains("Betting phase is over")
        ));
    }


    @Test
    void testBroadcastMessage() throws IOException {
        gameService.addSession(session1);
        gameService.addSession(session2);

        Response testMessage = new Response("TEST", "Test broadcast message");
        gameService.broadcastMessage(testMessage);

        verify(webSocketService).sendJson(session1, testMessage);
        verify(webSocketService).sendJson(session2, testMessage);
    }

    @Test
    void testBroadcastToClosedSession() throws IOException {
        WebSocketSession closedSession = mock(WebSocketSession.class);
        gameService.addSession(closedSession);
        closedSession.close();

        Response testMessage = new Response("TEST", "Test message");
        gameService.broadcastMessage(testMessage);

        verify(webSocketService, never()).sendJson(eq(closedSession), any(Response.class));
    }

    @Test
    void testInvalidBetData() throws IOException {
        gameService.addSession(session1);


        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        gameService.processPlayerBet(null, new PlayerBetDTO("Test", 100.0, 5));
        assertThat(gameService.getPlayerBets()).isEmpty();

        gameService.processPlayerBet("session1", null);
        assertThat(gameService.getPlayerBets()).isEmpty();
    }

    @Test
    void testMultipleRounds() throws Exception {
        gameService.addSession(session1);

        Thread.sleep(500);

        assertThat(gameService.getPlayerBets()).isEmpty();
        assertThat(gameService.isBettingPhase()).isFalse();

        Thread.sleep(1000);

        assertThat(gameService.isBettingPhase()).isTrue();
        assertThat(gameService.isGameInProgress()).isTrue();
    }
}