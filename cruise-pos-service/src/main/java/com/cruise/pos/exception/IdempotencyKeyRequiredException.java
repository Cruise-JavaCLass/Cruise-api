package com.cruise.pos.exception;

public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
