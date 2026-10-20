package com.cruise.pos.exception;

public class InvoiceNotAvailableException extends RuntimeException {

    public InvoiceNotAvailableException(String message) {
        super(message);
    }
}
