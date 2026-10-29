package io.github.anushkasomi01.laptopfinder.ai;

/** A successful provider call that did not contain a usable assistant answer. */
public class EmptyModelResponseException extends RuntimeException {

    public EmptyModelResponseException() {
        super("The model returned an empty answer");
    }
}
