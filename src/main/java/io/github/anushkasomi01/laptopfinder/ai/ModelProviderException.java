package io.github.anushkasomi01.laptopfinder.ai;

/** Sanitized boundary exception; provider messages must not reach API clients. */
public class ModelProviderException extends RuntimeException {

    public ModelProviderException(Throwable cause) {
        super("The model provider could not complete the request", cause);
    }
}
