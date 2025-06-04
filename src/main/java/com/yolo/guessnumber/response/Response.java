package com.yolo.guessnumber.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class Response {
    private String type;
    private String message;
    private Integer winningNumber;
    private Double winning;
    private List<Winner> winners;
    private int timeRemaining;

    public Response(String type, String message) {
        this.type = type;
        this.message = message;
    }

    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Winner {
        private String nickname;
        private double winning;
    }
}
