package com.yolo.guessnumber.rtp;

import com.yolo.guessnumber.gamelogic.GuessNumber;
import com.yolo.guessnumber.playerBet.PlayerBetDTO;
import com.yolo.guessnumber.service.GameService;
import com.yolo.guessnumber.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RtpSimulationTest {

    private static final int TOTAL_ROUNDS = 700_000;
    private static final int THREAD_COUNT = 24;
    private static final int ROUNDS_PER_THREAD = TOTAL_ROUNDS / THREAD_COUNT;
    private static final int LEFTOVER = TOTAL_ROUNDS % THREAD_COUNT;
    private static final double BET_AMOUNT = 100;
    private static final int NUMBER_RANGE = 11; // Assuming numbers 0-10

    @Mock
    private WebSocketSession session;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getId()).thenReturn("session");
    }

    @Test
    void testRTPCalculationWith1MillionRoundsInThreads() throws Exception {
        System.out.println("Starting RTP test with " + TOTAL_ROUNDS + " rounds across " + THREAD_COUNT + " threads...");
        long startTime = System.currentTimeMillis();

        // Atomic counters for thread-safe operations
        AtomicLong totalWagered = new AtomicLong(0);
        AtomicLong totalWon = new AtomicLong(0);
        AtomicLong totalWins = new AtomicLong(0);
        AtomicLong totalRounds = new AtomicLong(0);

        // Create futures for parallel execution
        List<Future<RoundResult>> futures = new ArrayList<>();

        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            final int currentThreadId = threadId;
            int finalThreadId = threadId;
            Future<RoundResult> future = executorService.submit(() ->
                    runRoundsForThread(currentThreadId, ROUNDS_PER_THREAD + (finalThreadId == THREAD_COUNT - 1 ? LEFTOVER : 0)));
            futures.add(future);
        }

        // Collect results from all threads
        for (Future<RoundResult> future : futures) {
            RoundResult result = future.get(); // This will block until the thread completes
            totalWagered.addAndGet(result.totalWagered);
            totalWon.addAndGet(result.totalWon);
            totalWins.addAndGet(result.wins);
            totalRounds.addAndGet(result.rounds);
        }

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // Calculate statistics
        double actualRTP = (double) totalWon.get() / totalWagered.get();
        double expectedRTP = 0.99; // Theoretical RTP for fair game
        double winRate = (double) totalWins.get() / totalRounds.get();
        double avgWinAmount = totalWins.get() > 0 ? (double) totalWon.get() / totalWins.get() : 0;

        // Print detailed results
        System.out.println("\n=== RTP PERFORMANCE TEST RESULTS ===");
        System.out.println("Execution time: " + executionTime + " ms (" + (executionTime) + " seconds)");
        System.out.println("Total rounds played: " + totalRounds.get());
        System.out.println("Total amount wagered: " + totalWagered.get());
        System.out.println("Total amount won: " + totalWon.get());
        System.out.println("Total winning rounds: " + totalWins.get());
        System.out.println("Win rate: " + String.format("%.4f%%", winRate * 100));
        System.out.println("Average win amount: " + avgWinAmount / 100);
        System.out.println("Actual RTP: " + String.format("%.6f", actualRTP));
        System.out.println("Expected RTP: " + String.format("%.6f", expectedRTP));
        System.out.println("RTP difference: " + String.format("%.6f", actualRTP - expectedRTP));
        System.out.println("Rounds per second: " + String.format("%.0f", (double) totalRounds.get() / (executionTime / 1000.0)));

        assertThat(totalRounds.get()).isEqualTo(TOTAL_ROUNDS);
        assertThat(totalWagered.get()).isEqualTo(TOTAL_ROUNDS * (long) (BET_AMOUNT) * 100); // Convert to cents for precision

        assertThat(actualRTP).isBetween(expectedRTP - 0.02, expectedRTP + 0.02);

        assertThat(winRate * 10).isBetween(expectedRTP - 0.02, expectedRTP + 0.02);
    }

    private RoundResult runRoundsForThread(int threadId, int rounds) throws IOException {
        GuessNumber guessNumber = new GuessNumber();
        WebSocketService localWebSocketService = mock(WebSocketService.class);
        GameService localGameService = new GameService(guessNumber, localWebSocketService, -1);

        WebSocketSession localSession = mock(WebSocketSession.class);
        when(localSession.isOpen()).thenReturn(true);
        when(localSession.getId()).thenReturn("session-" + threadId);
        localGameService.addSession(localSession);

        long threadWagered = 0;
        long threadWon = 0;
        int threadWins = 0;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < rounds; i++) {
            int playerNumber = rng.nextInt(1, NUMBER_RANGE);

            localGameService.processPlayerBet(session.getId(), new PlayerBetDTO("Player", BET_AMOUNT, playerNumber));
            double winAmount = localGameService.getPlayerBets().get(session.getId()).getWinAmount();

            threadWagered += (long) (BET_AMOUNT) * 100; // Convert to cents for precision
            if (winAmount > 0) {
                threadWon += (long) (winAmount) * 100; // Convert to cents
                threadWins++;
            }

            if (i > 0 && i % 10000 == 0) {
                System.out.printf("Thread %d: Completed %d/%d rounds (%.1f%%)%n",
                        threadId, i, rounds, (double) i / rounds * 100);
            }
        }
        System.out.printf("Thread %d completed: %d rounds, RTP: %.6f%n",
                threadId, rounds, (double) threadWon / threadWagered);

        return new RoundResult(threadWagered, threadWon, threadWins, rounds);
    }

    private record RoundResult(long totalWagered, long totalWon, long wins, int rounds) {
    }
}