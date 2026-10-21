package com.cruise.pos.exception;

public class FolioAlreadySettledException extends RuntimeException {

    public FolioAlreadySettledException(String message) {
        super(message);
    }
}
