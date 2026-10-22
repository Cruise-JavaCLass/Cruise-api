package com.cruise.pos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CredentialSecurityException extends RuntimeException {
    public CredentialSecurityException(String message) {
        super(message);
    }
}
