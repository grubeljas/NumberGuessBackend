package com.yolo.guessnumber.service;

import com.yolo.guessnumber.gamelogic.GuessNumber;
import com.yolo.guessnumber.playerBet.PlayerBet;
import com.yolo.guessnumber.playerBet.PlayerBetDTO;
import com.yolo.guessnumber.response.Response;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Getter
public class GameService {

    private static final int DEFAULT_ROUND_TIME = 10; // seconds

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PlayerBet> playerBets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final GuessNumber guessNumber;
    private final WebSocketService webSocketService;

    private long gameStartTime;
    private final int roundTime;
    public boolean bettingPhase = false;
    public boolean isGameInProgress = false;

    @Autowired
    public GameService(GuessNumber guessNumber, WebSocketService webSocketService) {
        this.guessNumber = guessNumber;
        this.webSocketService = webSocketService;
        roundTime = DEFAULT_ROUND_TIME;
        startGameLoop();
    }

    public GameService(GuessNumber guessNumber, WebSocketService webSocketService, int roundTime) {
        this.guessNumber = guessNumber;
        this.webSocketService = webSocketService;
        this.roundTime = roundTime;
        if (roundTime >= 0) {
            startGameLoop();
        } else {
            bettingPhase = true;
        }
    }

    //Game loop methods
    private void startGameLoop() {
        scheduler.scheduleWithFixedDelay(this::runGameRound, 1, roundTime + 1, TimeUnit.SECONDS);
        System.out.println("Game loop started with round time: " + roundTime + " seconds");
    }

    private void runGameRound() {
        gameStartTime = System.currentTimeMillis();
        startBettingPhase();

        if (roundTime > 0) {
            for (int i = 1; i <= roundTime; i++) {
                final int secondsLeft = roundTime - i;
                int even = i;
                scheduler.schedule(() -> {
                    if (bettingPhase && even % 2 == 0) {
                        Response countdownMsg = new Response("COUNTDOWN", "Time remaining: " + secondsLeft + " seconds");
                        countdownMsg.setTimeRemaining(secondsLeft);
                        broadcastMessage(countdownMsg);
                    }
                }, i, TimeUnit.SECONDS);
            }
        }

        scheduler.schedule(() -> {
            endBettingPhase();
            Response resultMsg = processResults();
            broadcastResult(resultMsg);
            endOfRound();
        }, roundTime, TimeUnit.SECONDS);
    }

    public void startBettingPhase() {
        bettingPhase = true;
        isGameInProgress = true;
        System.out.println("Start of the round.");
        Response roundStartMsg = new Response("ROUND_START", "Round has started! " + roundTime + " seconds until the result.");
        roundStartMsg.setTimeRemaining(roundTime);
        broadcastMessage(roundStartMsg);
    }

    public void endBettingPhase() {
        bettingPhase = false;
    }

    public void endOfRound() {
        System.out.println("End of round.");
        broadcastMessage(new Response("ROUND_END", "End of round! Please wait for the next round to start."));
        playerBets.clear();
        isGameInProgress = false;
    }

    //Results processing methods
    public Response processResults() {
        int winningNumber = guessNumber.getResult();
        setWins(winningNumber);
        Response resultMsg = new Response("ROUND_RESULT", "");
        resultMsg.setWinningNumber(winningNumber);
        resultMsg.setWinners(calculateWinners());
        return resultMsg;
    }

    public void setWins(int pickedNumber) {
        playerBets.forEach((sessionId, playerBet) -> {
            double winAmount;
            if (playerBet.getPickedNumber().equals(pickedNumber)) {
                winAmount = playerBet.getBetAmount() * GuessNumber.MULTIPLIER;
            } else {
                winAmount = 0f;
            }
            playerBet.setWinAmount(winAmount);
        });
    }

    public List<Response.Winner> calculateWinners() {
        return playerBets.values().stream()
                .filter(playerBet -> playerBet.getWinAmount() > 0)
                .map(playerBet -> new Response.Winner(playerBet.getNickname(), playerBet.getWinAmount()))
                .sorted(Comparator.comparing(Response.Winner::getWinning).reversed())
                .toList();
    }

    //Session management methods
    public void addSession(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            System.out.println("Session is null or not open: " + session);
            return;
        }
        if (sessions.containsKey(session.getId())) {
            System.out.println("Session already exists: " + session.getId());
            return;
        }
        sessions.put(session.getId(), session);

        if (bettingPhase) {
            long timeRemaining = getTimeRemaining();
            Response countdownMsg = new Response("COUNTDOWN", "Welcome! Round is running! Time remaining: " + timeRemaining + " seconds");
            countdownMsg.setTimeRemaining((int) timeRemaining);
            try {
                webSocketService.sendJson(session, countdownMsg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Response welcomeMsg = new Response("WELCOME", "Welcome! Please wait for the next round.");
            try {
                webSocketService.sendJson(session, welcomeMsg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
        playerBets.remove(session.getId());
    }

    public void processPlayerBet(String sessionId, PlayerBetDTO playerBetDTO) throws IOException {
        if (sessionId == null || playerBetDTO == null) {
            System.out.println("Session ID or PlayerBet is null");
            return;
        }
        if (!bettingPhase) {
            System.out.println("Betting phase is over. Bet rejected for session: " + sessionId);
            webSocketService.sendJson(sessions.get(sessionId),
                    new Response("ERROR", "Betting phase is over. Please wait for the next round."));
            return;
        }
        try {
            PlayerBet playerBet = new PlayerBet(playerBetDTO.getNickname(), playerBetDTO.getBetAmount(),
                    playerBetDTO.getPickedNumber());
            playerBets.put(sessionId, playerBet);
        } catch (Exception e) {
            System.err.println("Error processing player bet: " + e.getMessage());
        }
    }

    private long getTimeRemaining() {
        long elapsedTime = (System.currentTimeMillis() - gameStartTime) / 1000;
        return roundTime - elapsedTime;
    }

    //Broadcasting methods
    public void broadcastResult(Response resultMsg) {
        playerBets.forEach((key, bet) -> {
            String message;
            if (bet.getWinAmount() > 0) {
                message = "Congratulations " + bet.getNickname() + "! You won: " + bet.getWinAmount();
            } else {
                message = "Sorry " + bet.getNickname() + ", better luck next time!";
            }
            Response winMsg = new Response("ROUND_RESULT", message);
            winMsg.setWinningNumber(resultMsg.getWinningNumber());
            winMsg.setWinning(bet.getWinAmount());
            winMsg.setWinners(resultMsg.getWinners());
            WebSocketSession session = sessions.get(key);
            if (session != null && session.isOpen()) {
                try {
                    webSocketService.sendJson(session, winMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public void broadcastMessage(Response message) {
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    webSocketService.sendJson(session, message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
