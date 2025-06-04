package com.yolo.guessnumber.playerBet;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerBetDTO {
    @NonNull
    private String nickname;
    @NonNull
    private Double betAmount;
    @NonNull
    private Integer pickedNumber;
}
