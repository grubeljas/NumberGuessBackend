package com.yolo.guessnumber.playerBet;

import lombok.*;

@Data
public class PlayerBet {
    private String nickname;
    private double betAmount;
    private Integer pickedNumber;
    private double winAmount;

    public PlayerBet(@NonNull String nickname, double betAmount, @NonNull Integer pickedNumber) {
        this.nickname = nickname;
        this.betAmount = betAmount;
        this.pickedNumber = pickedNumber;
    }
}
