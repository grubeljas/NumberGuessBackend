package com.yolo.guessnumber.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class RequestValidator {

    ObjectMapper objectMapper = new ObjectMapper();

    public void isValidRequest(String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Request message cannot be null or empty.");
        }
        try {
            objectMapper.readTree(message);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: Unrecognized token '" + e.getMessage() + "'");
        }
    }
}