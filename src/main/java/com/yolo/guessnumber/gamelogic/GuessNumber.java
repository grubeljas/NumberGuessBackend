package com.yolo.guessnumber.gamelogic;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class GuessNumber {
    public static final Double MULTIPLIER = 9.9d;

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    public int getResult() {
        return rng.nextInt(1, 11);
    }
}
