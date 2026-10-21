package com.cruise.pos.exception;

public class InsufficientTenderException extends RuntimeException {

    public InsufficientTenderException(String message) {
        super(message);
    }
}
