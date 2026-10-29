package io.github.anushkasomi01.laptopfinder.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Intentionally small public contract: clients cannot supply a system prompt,
 * provider credentials, arbitrary model options, or a conversation identifier.
 *
 * @param message nonblank user text, at most 4000 Java UTF-16 code units
 */
public record ChatRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message) {
}
