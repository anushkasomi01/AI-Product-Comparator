package io.github.anushkasomi01.laptopfinder.api.dto;

import java.time.Instant;
import java.util.List;

/** Stable error envelope. Never includes rejected input, prompt text, or provider details. */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String requestId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
