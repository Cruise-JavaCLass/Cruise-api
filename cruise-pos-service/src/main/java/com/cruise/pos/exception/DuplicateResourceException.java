package com.cruise.pos.exception;

public class DuplicateResourceException extends RuntimeException {
    /**
     * Creates a duplicate-resource exception with the supplied conflict message.
     */
    public DuplicateResourceException(String message) {
        super(message);
    }
}
