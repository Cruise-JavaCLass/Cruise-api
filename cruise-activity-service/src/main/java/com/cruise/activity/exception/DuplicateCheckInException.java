package com.cruise.activity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ngoại lệ ném ra khi phát hiện hành khách đã check-in trước đó (HTTP 409 Conflict)
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateCheckInException extends RuntimeException {

    public DuplicateCheckInException(String message) {
        super(message);
    }
}
