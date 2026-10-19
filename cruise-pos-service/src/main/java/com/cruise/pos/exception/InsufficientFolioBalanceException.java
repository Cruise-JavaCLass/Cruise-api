package com.cruise.pos.exception;

public class InsufficientFolioBalanceException extends RuntimeException {
    public InsufficientFolioBalanceException(String message) {
        super(message);
    }
}
